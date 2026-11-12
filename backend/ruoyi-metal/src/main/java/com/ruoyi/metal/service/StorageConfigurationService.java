package com.ruoyi.metal.service;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.metal.catalog.FactoryCatalogRecord;
import com.ruoyi.metal.catalog.ProcessRecord;
import com.ruoyi.metal.catalog.ProductRecord;
import com.ruoyi.metal.security.FactoryScope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Shared configuration validation for catalog transactions and the compatibility endpoint.
 */
@Service
public class StorageConfigurationService {
    private final JdbcTemplate jdbc;
    private final FactoryScope scope;

    public StorageConfigurationService(JdbcTemplate jdbc, FactoryScope scope) {
        this.jdbc = jdbc;
        this.scope = scope;
    }

    private record Table(String name, String pk, String permission) {
    }

    private static Table table(String kind) {
        return switch (kind) {
            case "material" -> new Table("metal_material", "material_id", "material");
            case "process" -> new Table("metal_process", "process_id", "process");
            case "product" -> new Table("metal_product", "product_id", "product");
            default -> throw new ServiceException("配置类型无效", 400);
        };
    }

    private void allow(String kind, boolean write) {
        String p = "metal:" + table(kind).permission() + ":";
        if (!scope.isAdmin() && !SecurityUtils.hasPermi("metal:storage-config:" + (write ? "edit" : "query")) && !(write ? SecurityUtils.hasPermi(p + "edit") : SecurityUtils.hasPermi(p + "list") || SecurityUtils.hasPermi(p + "query") || SecurityUtils.hasPermi(p + "add") || SecurityUtils.hasPermi(p + "edit")))
            throw new ServiceException("没有基础资料配置权限", 403);
    }

    private Map<String, Object> master(String kind, long id, long factory, boolean lock) {
        Table t = table(kind);
        var rows = jdbc.queryForList("SELECT * FROM " + t.name() + " WHERE " + t.pk() + "=? AND factory_id=? AND del_flag='0'" + (lock ? " FOR UPDATE" : ""), id, factory);
        if (rows.isEmpty()) throw new ServiceException("资料不存在或不属于本厂", 404);
        return rows.get(0);
    }

    private List<Map<String, Object>> steps(long id) {
        return jdbc.queryForList("SELECT CAST(pp.process_id AS CHAR) processId,pp.sequence_no sequenceNo,p.process_name processName,p.semantic_code semanticCode,pp.output_category outputCategory,pp.output_form outputForm,CAST(pp.output_material_id AS CHAR) outputMaterialId,pp.measurement_mode measurementMode,pp.loss_unit lossUnit,m.material_code outputMaterialCode,m.material_name outputMaterialName FROM metal_product_process pp JOIN metal_product product ON product.product_id=pp.product_id JOIN metal_process p ON p.process_id=pp.process_id AND p.factory_id=product.factory_id LEFT JOIN metal_material m ON m.material_id=pp.output_material_id AND m.factory_id=product.factory_id AND m.del_flag='0' WHERE pp.product_id=? ORDER BY pp.sequence_no", id);
    }

    private boolean locked(String kind, long id) {
        return switch (kind) {
            case "material" -> WarehouseReferences.materialReferenced(jdbc, id);
            case "process" ->
                    count("SELECT COUNT(*) FROM metal_production_order_step s JOIN metal_production_order_item i ON i.id=s.item_id JOIN metal_production_order o ON o.id=i.order_id WHERE s.process_id=? AND o.flow_version>0", id) > 0;
            default ->
                    count("SELECT COUNT(*) FROM metal_production_order_item i JOIN metal_production_order o ON o.id=i.order_id WHERE i.product_id=? AND o.flow_version>0", id) > 0;
        };
    }

    Map<String, Object> catalogDetail(String kind, long id, long factory) {
        var row = master(kind, id, factory, false);
        var result = new LinkedHashMap<String, Object>();
        result.put("locked", locked(kind, id));
        if (kind.equals("material")) {
            result.put("measurementMode", row.get("measurement_mode"));
            result.put("form", row.get("material_form"));
        } else if (kind.equals("process")) result.put("semanticCode", row.get("semantic_code"));
        else result.put("steps", steps(id));
        return result;
    }

    public Map<String, Object> detail(String kind, long id, Long requested) {
        allow(kind, false);
        long f = scope.resolveFactory(requested);
        var row = master(kind, id, f, false);
        var result = catalogDetail(kind, id, f);
        result.put("id", Long.toString(id));
        result.put("factoryId", Long.toString(f));
        result.put("version", row.get("version"));
        if (kind.equals("product")) result.put("materials", materials(f));
        return result;
    }

    public List<Map<String, Object>> materials(long f) {
        return jdbc.queryForList("SELECT CAST(material_id AS CHAR) id,material_code code,material_name name,measurement_mode measurementMode,material_form form FROM metal_material WHERE factory_id=? AND del_flag='0' ORDER BY material_code,material_id", f);
    }

    public List<Map<String, Object>> materialsForFactory(Long requested) {
        allow("product", false);
        return materials(scope.resolveFactory(requested));
    }

    private static final List<String> STEP_KEYS = List.of("processId", "sequenceNo", "measurementMode", "outputCategory", "outputForm", "outputMaterialId");

    private static List<List<String>> comparable(List<Map<String, Object>> route) {
        return route.stream().map(s -> STEP_KEYS.stream().map(k -> Objects.toString(s.get(k), "")).toList()).toList();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> maps(Object raw) {
        if (!(raw instanceof List<?> list)) throw new ServiceException("请配置完整产品工序", 400);
        var result = new ArrayList<Map<String, Object>>();
        for (var item : list) {
            if (!(item instanceof Map<?, ?> m)) throw new ServiceException("工序配置无效", 400);
            var row = new LinkedHashMap<String, Object>();
            m.forEach((k, v) -> row.put(k.toString(), v));
            result.add(row);
        }
        return result;
    }

    void prepareCatalog(String kind, FactoryCatalogRecord record) {
        if (kind.equals("process")) {
            var process = (ProcessRecord) record;
            record.setStorageConfiguration(validateProcess(record.getId(), record.getFactoryId(), process.getProcessType(), record.getStorageConfiguration() == null ? Collections.<String,Object>emptyMap() : record.getStorageConfiguration()));
            return;
        }
        boolean preserve = false;
        if (record.getStorageConfiguration() == null) {
            if (!kind.equals("product")) return;
            var product = (ProductRecord) record;
            if (record.getId() == null) throw new ServiceException("请刷新页面并补齐各工序产出配置", 400);
            var prior = steps(record.getId());
            if (product.getProcesses() == null || prior.size() != product.getProcesses().size())
                throw new ServiceException("请刷新页面并补齐各工序产出配置", 400);
            for (int i = 0; i < prior.size(); i++)
                if (!Objects.equals(Objects.toString(prior.get(i).get("processId"), ""), Objects.toString(product.getProcesses().get(i).getProcessId(), "")))
                    throw new ServiceException("修改产品路线须补齐各工序产出配置", 400);
            // Incomplete historical routes need automatic stage derivation; complete ones stay frozen.
            preserve = prior.stream().allMatch(s -> Set.of("RAW", "SEMI", "PRODUCT").contains(Objects.toString(s.get("outputCategory"), "")));
            record.setStorageConfiguration(Map.of("steps", prior));
        }
        var input = new LinkedHashMap<>(record.getStorageConfiguration());
        if (kind.equals("product")) {
            var product = (ProductRecord) record;
            var route = maps(input.get("steps"));
            if (product.getProcesses() == null || route.size() != product.getProcesses().size())
                throw new ServiceException("请补齐各工序产出配置", 400);
            for (int n = 0; n < route.size(); n++) {
                var step = product.getProcesses().get(n);
                if (!Objects.equals(Objects.toString(step.getProcessId(), ""), Objects.toString(route.get(n).get("processId"), "")))
                    throw new ServiceException("工序与产出配置顺序不一致", 400);
                route.get(n).put("sequenceNo", n + 1);
                route.get(n).put("lossUnit", step.getLossUnit());
            }
            input.put("steps", route);
        }
        record.setStorageConfiguration(validate(kind, record.getId(), record.getFactoryId(), input, !preserve));
    }

    private Map<String, Object> validateProcess(Long recordId, long factory, Integer typeId, Map<String, Object> input) {
        String semantic = ProcessTypeService.semantic(jdbc, typeId, factory);
        if (input.get("semanticCode") != null && !semantic.equals(input.get("semanticCode")))
            throw new ServiceException("工序类型与处理规则不一致，请刷新后重新选择", 409);
        if (recordId != null) {
            var old = catalogDetail("process", recordId, factory);
            if (Boolean.TRUE.equals(old.get("locked")) && !semantic.equals(old.get("semanticCode")))
                throw new ServiceException("工序已有真实生产流转引用，不能改变处理规则，请另建工序", 409);
        }
        return Map.of("semanticCode", semantic);
    }

    private Map<String, Object> validate(String kind, Long recordId, long f, Map<String, Object> input, boolean automatic) {
        Map<String, Object> old = recordId == null ? Map.of() : catalogDetail(kind, recordId, f);
        var clean = new LinkedHashMap<String, Object>();
        if (kind.equals("material")) {
            String form = choice(input.get("form"), Set.of("POWDER", "UNTREATED", "METAL"), "材料形态"), mode = choice(input.get("measurementMode"), Set.of("PIECE", "WEIGHT"), "计量方式");
            if (form.equals("POWDER") && !mode.equals("WEIGHT")) throw new ServiceException("粉状物料按重量计量", 400);
            clean.put("form", form);
            clean.put("measurementMode", mode);
        } else if (kind.equals("process")) {
            var process = master(kind, recordId, f, true);
            return validateProcess(recordId, f, ((Number) process.get("process_type")).intValue(), input);
        } else {
            var route = maps(input.get("steps"));
            if (route.isEmpty() || route.size() > 100) throw new ServiceException("请配置1至100道工序", 400);
            for (var step : route) {
                Long processId = id(step.get("processId"));
                if (processId == null) throw new ServiceException("请选择加工工序", 400);
                var p = master("process", processId, f, true);
                step.put("semanticCode", p.get("semantic_code"));
                step.put("processName", p.get("process_name"));
            }
            boolean special = route.stream().anyMatch(s -> !"NORMAL".equals(s.get("semanticCode"))), melted = false;
            for (int n = 0; n < route.size(); n++) {
                var step = route.get(n);
                String semantic = Objects.toString(step.get("semanticCode"), "");
                melted |= semantic.equals("SMELTING");
                if (automatic) {
                    String category = semantic.equals("ACID_WASH") ? "RAW" : semantic.equals("SMELTING") ? "SEMI" : n == route.size() - 1 ? "PRODUCT" : special ? (melted ? "SEMI" : "RAW") : Objects.toString(step.get("outputCategory"), "SEMI");
                    step.put("outputCategory", category);
                }
                String category = choice(step.get("outputCategory"), Set.of("RAW", "SEMI", "PRODUCT"), "产出类别");
                if (category.equals("RAW")) {
                    Long material = id(step.get("outputMaterialId"));
                    if (material == null)
                        throw new ServiceException("工序“" + step.get("processName") + "”请选择本厂产出物料", 400);
                    var m = master("material", material, f, true);
                    if (automatic) {
                        step.put("measurementMode", m.get("measurement_mode"));
                        step.put("outputForm", m.get("material_form"));
                    }
                    if (!Objects.equals(m.get("measurement_mode"), step.get("measurementMode")) || !Objects.equals(m.get("material_form"), step.get("outputForm")))
                        throw new ServiceException("工序“" + step.get("processName") + "”所选物料“" + m.get("material_code") + "”的计量方式或形态与产出配置不一致", 400);
                    choice(step.get("outputForm"), Set.of("POWDER", "UNTREATED", "METAL"), "产出物料形态");
                } else {
                    step.put("outputMaterialId", null);
                    step.put("outputForm", null);
                }
                if ("WEIGHT".equals(step.get("measurementMode")) && "G_PER_PIECE".equals(step.get("lossUnit")))
                    throw new ServiceException("按重量管理须先重新配置损耗百分比", 400);
            }
            ProductionRoutePolicy.validate(route);
            clean.put("steps", route);
        }
        if (Boolean.TRUE.equals(old.get("locked"))) {
            boolean changed = kind.equals("product") ? !comparable(maps(old.get("steps"))).equals(comparable(maps(clean.get("steps")))) : clean.entrySet().stream().anyMatch(e -> !Objects.equals(e.getValue(), old.get(e.getKey())));
            if (changed) throw new ServiceException("资料已有真实库存或生产流转引用，不能改变生产与仓储配置", 409);
        }
        return clean;
    }

    void writeCatalog(String kind, FactoryCatalogRecord record) {
        if (record.getStorageConfiguration() != null) write(kind, record.getId(), record.getStorageConfiguration());
    }

    private void write(String kind, long recordId, Map<String, Object> clean) {
        if (kind.equals("material"))
            jdbc.update("UPDATE metal_material SET measurement_mode=?,material_form=? WHERE material_id=?", clean.get("measurementMode"), clean.get("form"), recordId);
        else if (kind.equals("process"))
            jdbc.update("UPDATE metal_process SET semantic_code=? WHERE process_id=?", clean.get("semanticCode"), recordId);
        else for (var step : maps(clean.get("steps")))
                jdbc.update("UPDATE metal_product_process SET output_category=?,output_form=?,output_material_id=?,measurement_mode=? WHERE product_id=? AND sequence_no=? AND process_id=?", step.get("outputCategory"), step.get("outputForm"), step.get("outputMaterialId"), step.get("measurementMode"), recordId, step.get("sequenceNo"), step.get("processId"));
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> save(String kind, long recordId, Map<String, Object> input) {
        allow(kind, true);
        long f = scope.resolveFactory(id(input.get("factoryId")));
        scope.lockFactories(List.of(f));
        var old = master(kind, recordId, f, true);
        if (!Objects.equals(String.valueOf(old.get("version")), String.valueOf(input.get("version"))))
            throw new ServiceException("资料版本已变化，请重新加载", 409);
        if (kind.equals("product")) {
            var requested = maps(input.get("steps"));
            var current = steps(recordId);
            if (requested.size() != current.size()) throw new ServiceException("产品工序已变化，请重新加载", 409);
            for (int n = 0; n < requested.size(); n++) {
                for (String key : List.of("processId", "sequenceNo"))
                    if (!Objects.equals(Objects.toString(requested.get(n).get(key)), Objects.toString(current.get(n).get(key))))
                        throw new ServiceException("工序顺序不匹配", 409);
                requested.get(n).put("lossUnit", current.get(n).get("lossUnit"));
            }
            input = new LinkedHashMap<>(input);
            input.put("steps", requested);
        }
        var clean = validate(kind, recordId, f, input, false);
        write(kind, recordId, clean);
        Table t = table(kind);
        jdbc.update("UPDATE " + t.name() + " SET version=version+1,update_by=?,update_time=NOW() WHERE " + t.pk() + "=?", SecurityUtils.getUsername(), recordId);
        return detail(kind, recordId, f);
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    static String choice(Object value, Set<String> choices, String label) {
        String s = Objects.toString(value, "");
        if (!choices.contains(s)) throw new ServiceException("请设置有效的" + label, 400);
        return s;
    }

    static Long id(Object value) {
        if (value == null || value.toString().isBlank()) return null;
        try {
            long id = Long.parseLong(value.toString());
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (Exception e) {
            throw new ServiceException("标识无效", 400);
        }
    }
}
