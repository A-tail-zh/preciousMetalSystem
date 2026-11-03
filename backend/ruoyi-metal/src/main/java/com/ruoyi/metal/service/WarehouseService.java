package com.ruoyi.metal.service;

import java.util.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.math.*;
import java.sql.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.metal.catalog.WarehouseRequests.*;
import com.ruoyi.metal.catalog.WarehouseWorkOrderContext;
import com.ruoyi.metal.security.FactoryScope;

@Service
@Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
/**
 * 仓储业务服务，负责业务校验、数据处理和相关持久化操作。
 */
public class WarehouseService {
    private final JdbcTemplate jdbc;
    private final FactoryScope scope;
    private final SemiFlowService semi;

    public WarehouseService(JdbcTemplate jdbc, FactoryScope scope) {
        this.jdbc = jdbc;
        this.scope = scope;
        this.semi = new SemiFlowService(jdbc, scope);
    }

    private static ServiceException fail(String message) {
        return new ServiceException(message);
    }

    private static Long number(Object v) {
        return v == null ? null : Long.valueOf(v.toString());
    }

    private static BigDecimal decimal(Object v) {
        return v == null ? BigDecimal.ZERO : new BigDecimal(v.toString());
    }

    private static String str(Object v) {
        return v == null ? null : v.toString();
    }

    private static String actor() {
        String n = SecurityUtils.getLoginUser().getUser().getNickName();
        return n == null ? SecurityUtils.getUsername() : n;
    }

    private void permit(String p) {
        if (!scope.isAdmin() && !SecurityUtils.getLoginUser().getPermissions().contains(p))
            throw new ServiceException("没有此操作权限", 403);
    }

    private long insert(String sql, Object... args) {
        KeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(c -> {
            PreparedStatement p = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) p.setObject(i + 1, args[i]);
            return p;
        }, holder);
        return holder.getKey().longValue();
    }

    private List<Map<String, Object>> rows(String sql, Object... args) {
        return jdbc.queryForList(sql, args);
    }

    private Map<String, Object> one(String sql, Object... args) {
        var r = rows(sql, args);
        if (r.isEmpty()) throw fail("记录不存在或已归档");
        return r.get(0);
    }

    Map<String, Object> clean(Map<String, Object> input) {
        var result = new LinkedHashMap<String, Object>();
        input.forEach((k, v) -> {
            StringBuilder n = new StringBuilder();
            boolean upper = false;
            for (char c : k.toCharArray()) {
                if (c == '_') {
                    upper = true;
                    continue;
                }
                n.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            }
            String key = n.toString();
            Object value = v;
            if (v instanceof BigDecimal) value = v.toString();
            else if (v instanceof java.sql.Timestamp t)
                value = t.toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            else if (v instanceof LocalDateTime t) value = t.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            else if (v instanceof java.sql.Date) value = v.toString();
            else if (v instanceof Number && (key.equals("id") || key.endsWith("Id") || key.endsWith("Actor")))
                value = v.toString();
            result.put(key, value);
        });
        if(SemiMeasurementRules.weight(input)){for(String field:List.of("quantity","completedQuantity","averageLoss","thresholdValue","plannedQuantity"))if(result.containsKey(field)&&(!"thresholdValue".equals(field)||!"PERCENT".equals(input.get("loss_unit"))))result.put(field,null);}
        return result;
    }

    List<Map<String, Object>> cleaned(List<Map<String, Object>> data) {
        return data.stream().map(this::clean).toList();
    }

    public Map<String, Object> listOrders(int page, int size, com.ruoyi.metal.catalog.OrderListQuery q) {
        var p = new BusinessQueries(scope).orders(q);
        var args = new ArrayList<>(p.args());
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM metal_warehouse_order t WHERE " + p.sql(), Long.class, args.toArray());
        args.add(size);
        args.add((page - 1) * size);
        var data = rows("SELECT t.*,t.order_id id" + extraColumns("warehouse-order") + " FROM metal_warehouse_order t WHERE " + p.sql() + " ORDER BY t.order_id LIMIT ? OFFSET ?", args.toArray());
        return Map.of("rows", cleaned(data), "total", total, "code", 200, "msg", "查询成功");
    }

    private record Resource(String table, String pk, String extra, String where) {
    }

    private Resource resource(String key) {
        return switch (key) {
            case "warehouse" -> new Resource("metal_warehouse", "warehouse_id", "", "t.del_flag='0'");
            case "inventory" ->
                    new Resource("metal_inventory", "inventory_id", " LEFT JOIN metal_warehouse h ON h.warehouse_id=t.warehouse_id LEFT JOIN metal_material m ON t.category='RAW' AND m.material_id=t.reference_id LEFT JOIN metal_customer c ON c.customer_id=t.customer_id", "t.del_flag='0'");
            case "warehouse-order" -> new Resource("metal_warehouse_order", "order_id", "", "1=1");
            case "process-batch" ->
                    new Resource("metal_process_batch", "batch_id", " LEFT JOIN metal_work_order w ON w.work_order_id=t.work_order_id LEFT JOIN metal_work_order_step s ON s.item_id=t.item_id AND s.process_id=t.process_id", "1=1");
            case "work-report" ->
                    new Resource("metal_work_report", "report_id", " LEFT JOIN metal_process_batch b ON b.batch_id=t.batch_id LEFT JOIN metal_work_order w ON w.work_order_id=b.work_order_id LEFT JOIN metal_work_order_step s ON s.item_id=t.item_id AND s.process_id=t.process_id", "1=1");
            case "warning-rule" ->
                    new Resource("metal_warning_rule", "rule_id", " LEFT JOIN metal_product p ON p.product_id=t.product_id LEFT JOIN metal_process s ON s.process_id=t.process_id", "t.del_flag='0'");
            case "warning-record" -> new Resource("metal_warning_record", "warning_id", "", "t.del_flag='0'");
            default -> throw fail("资源无效");
        };
    }

    private String reportWeightColumns(String alias) {
        String base = " FROM metal_warehouse_order o JOIN metal_warehouse_order_line l ON l.order_id=o.order_id WHERE o.batch_id=" + alias + ".batch_id AND o.status='POSTED' AND o.type ";
        return ",CASE WHEN " + alias + ".voided=FALSE THEN (SELECT COALESCE(SUM(COALESCE(l.processor_net_weight,l.net_weight)),0)" + base + "='RAW_RETURN') ELSE NULL END raw_returned_weight,CASE WHEN " + alias + ".voided=FALSE THEN (SELECT COALESCE(SUM(COALESCE(l.processor_net_weight,l.net_weight)),0)" + base + "='SEMI_RETURN') ELSE NULL END semi_returned_weight,CASE WHEN " + alias + ".voided=FALSE THEN (SELECT COALESCE(SUM(COALESCE(l.processor_net_weight,l.net_weight)),0)" + base + "IN ('SEMI_IN','FINISHED_IN','RAW_PROCESS_IN')) ELSE NULL END output_weight";
    }

    private String extraColumns(String key) {
        return switch (key) {
            case "warehouse" ->
                    ",(SELECT factory_name FROM metal_factory f WHERE f.factory_id=t.factory_id) factory_name";
            case "warehouse-order" ->
                    ",(SELECT order_no FROM metal_work_order w WHERE w.work_order_id=t.work_order_id) work_order_no,(SELECT o.order_no FROM metal_warehouse_order_line l JOIN metal_warehouse_order o ON o.order_id=l.order_id WHERE l.line_id=(SELECT MIN(x.source_line_id) FROM metal_warehouse_order_line x WHERE x.order_id=t.order_id)) source_order_no";
            case "inventory" ->
                    ",h.name warehouse_name,"+RawMaterialKinds.labelSql("t","m")+" material_type,CASE WHEN t.category='RAW' AND t.material_ownership='FACTORY' THEN '工厂自有' WHEN t.category='RAW' THEN c.company_name ELSE t.customer_company_snapshot END customer_company_name,t.customer_name_snapshot customer_name,t.product_type_snapshot product_type,t.metal_type_name_snapshot metal_type_name,COALESCE((SELECT SUM(r.weight) FROM metal_stock_reservation r WHERE r.inventory_id=t.inventory_id AND r.status='ACTIVE'),0) reserved_weight,COALESCE((SELECT SUM(r.quantity) FROM metal_stock_reservation r WHERE r.inventory_id=t.inventory_id AND r.status='ACTIVE'),0) reserved_quantity,t.net_weight-COALESCE((SELECT SUM(r.weight) FROM metal_stock_reservation r WHERE r.inventory_id=t.inventory_id AND r.status='ACTIVE'),0) available_weight,EXISTS(SELECT 1 FROM metal_warehouse_order_line l JOIN metal_warehouse_order o ON o.order_id=l.order_id WHERE l.inventory_id=t.inventory_id AND (o.type NOT IN ('OPENING','ADJUST','RAW_IN','FINISHED_IN','SEMI_IN','RAW_PROCESS_IN') OR o.batch_id IS NOT NULL)) business_referenced";
            case "process-batch" -> ",w.order_no,s.process_name";
            case "work-report" -> ",w.order_no,s.process_name,b.measurement_mode" + reportWeightColumns("t");
            case "warning-rule" ->
                    ",p.product_name,s.process_name,(SELECT factory_name FROM metal_factory f WHERE f.factory_id=t.factory_id) factory_name";
            default -> "";
        };
    }

    /**
     * 查询列表并补充关联资料。
     */
    public Map<String, Object> list(String key, int page, int size, String filter, Long factoryId) {
        Resource r = resource(key);
        Long own = scope.currentFactory();
        if (factoryId != null) {
            scope.check(factoryId);
            own = factoryId;
        }
        List<Object> args = new ArrayList<>();
        String where = r.where();
        if (own != null) {
            where += " AND t.factory_id=?";
            args.add(own);
        }
        if (filter != null && !filter.isBlank()) {
            if (key.equals("inventory")) {
                if (!Set.of("RAW", "FINISHED", "SEMI").contains(filter)) throw fail("库存分类无效");
                where += " AND t.category=?";
                args.add(filter);
            } else if (key.equals("warehouse-order")) {
                if (!Set.of("OUT", "IN", "RET", "ADJ").contains(filter)) throw fail("单据分类无效");
                where += " AND t.kind=?";
                args.add(filter);
            }
        }
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM " + r.table() + " t WHERE " + where, Long.class, args.toArray());
        args.add(size);
        args.add((page - 1) * size);
        var data = rows("SELECT t.*,t." + r.pk() + " id" + extraColumns(key) + " FROM " + r.table() + " t" + r.extra() + " WHERE " + where + " ORDER BY t." + r.pk() + " LIMIT ? OFFSET ?", args.toArray());
        return Map.of("rows", cleaned(data), "total", total, "code", 200, "msg", "查询成功");
    }

    private Map<String, Object> record(String key, Long id, boolean lock) {
        Resource r = resource(key);
        var row = one("SELECT t.*,t." + r.pk() + " id FROM " + r.table() + " t WHERE " + r.pk() + "=? AND " + r.where() + (lock ? " FOR UPDATE" : ""), id);
        scope.check(number(row.get("factory_id")));
        return row;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> inventoryList(int page,int size,com.ruoyi.metal.catalog.InventoryListQuery query){
        var r=resource("inventory");var predicate=new BusinessQueries(scope).inventory(query);long total=jdbc.queryForObject("SELECT COUNT(*) FROM "+r.table()+" t"+r.extra()+" WHERE "+predicate.sql(),Long.class,predicate.args().toArray());return Map.of("rows",inventoryRows(query,size,(long)(page-1)*size),"total",total,"code",200,"msg","查询成功");
    }
    List<Map<String,Object>> inventoryRows(com.ruoyi.metal.catalog.InventoryListQuery query,int size,long offset){
        var r=resource("inventory");var predicate=new BusinessQueries(scope).inventory(query);var args=new ArrayList<>(predicate.args());args.add(size);args.add(offset);return cleaned(rows("SELECT t.*,t.inventory_id id"+extraColumns("inventory")+" FROM "+r.table()+" t"+r.extra()+" WHERE "+predicate.sql()+" ORDER BY t.inventory_id LIMIT ? OFFSET ?",args.toArray()));
    }

    private Map<String, Object> lock(String key, Long id) {
        var initial = record(key, id, false);
        scope.lockFactories(List.of(number(initial.get("factory_id"))));
        return record(key, id, true);
    }

    private void version(Map<String, Object> row, Integer version) {
        if (version == null || !number(row.get("version")).equals(version.longValue()))
            throw fail("记录已被修改，请刷新后重试");
    }

    /**
     * 查询指定记录详情；业务层执行相应的访问权限检查。
     */
    public Map<String, Object> detail(String key, Long id) {
        var row = record(key, id, false);
        var result = clean(row);
        result.put("factoryName", str(one("SELECT factory_name FROM metal_factory WHERE factory_id=?", row.get("factory_id")).get("factory_name")));
        if (key.equals("warehouse-order")) {
            if (row.get("batch_id") != null) {
                var batchInfo = one("SELECT batch_no,status,version,planned_quantity,completed_quantity,completion_policy,measurement_mode FROM metal_process_batch WHERE batch_id=?", row.get("batch_id"));
                result.put("batchNo", batchInfo.get("batch_no"));
                result.put("completionPolicy", batchInfo.get("completion_policy"));
                result.put("batchStatus", batchInfo.get("status"));
                result.put("batchVersion", batchInfo.get("version"));
                result.put("plannedQuantity", batchInfo.get("planned_quantity"));
                result.put("completedQuantity",SemiMeasurementRules.weight(batchInfo)?null:batchInfo.get("completed_quantity"));result.put("measurementMode",batchInfo.get("measurement_mode"));
            }
            if(row.get("inbound_submission_id")!=null)result.put("relatedInboundOrders",jdbc.queryForList("SELECT CAST(order_id AS CHAR) id,order_no orderNo,warehouse_name warehouseName,status FROM metal_warehouse_order WHERE inbound_submission_id=? AND order_id<>? ORDER BY warehouse_id",row.get("inbound_submission_id"),id));
            if(row.get("outbound_submission_id")!=null)result.put("relatedOutboundOrders",jdbc.queryForList("SELECT CAST(order_id AS CHAR) id,order_no orderNo,warehouse_name warehouseName,status FROM metal_warehouse_order WHERE outbound_submission_id=? AND order_id<>? ORDER BY warehouse_id",row.get("outbound_submission_id"),id));
            result.put("operatorIds", JSON.parseArray(str(row.get("operator_ids"))));
            if (row.get("work_order_id") != null)
                result.put("workOrderNo", str(one("SELECT order_no FROM metal_work_order WHERE work_order_id=?", row.get("work_order_id")).get("order_no")));
            var sources = rows("SELECT DISTINCT o.order_no FROM metal_warehouse_order_line x JOIN metal_warehouse_order_line l ON l.line_id=x.source_line_id JOIN metal_warehouse_order o ON o.order_id=l.order_id WHERE x.order_id=? ORDER BY o.order_no", id);
            result.put("sourceOrderNo", sources.stream().map(x -> str(x.get("order_no"))).collect(java.util.stream.Collectors.joining("、")));
        }
        if (key.equals("process-batch") || key.equals("work-report")) {
            Long work = key.equals("process-batch") ? number(row.get("work_order_id")) : number(one("SELECT work_order_id FROM metal_process_batch WHERE batch_id=?", row.get("batch_id")).get("work_order_id"));
            result.put("orderNo", str(one("SELECT order_no FROM metal_work_order WHERE work_order_id=?", work).get("order_no")));
            result.put("processName", str(step(number(row.get("item_id")), number(row.get("process_id"))).get("process_name")));
        }
        if (key.equals("warning-rule")) {
            result.put("productName", str(one("SELECT product_name FROM metal_product WHERE product_id=?", row.get("product_id")).get("product_name")));
            result.put("processName", str(one("SELECT process_name FROM metal_process WHERE process_id=?", row.get("process_id")).get("process_name")));
        }
        if (key.equals("inventory")) {
            for(String field:List.of("originWorkOrderId","originWorkOrderNo","originItemId","originProcessId","originBatchNo","originOutputStage"))result.put(field,null);
            if(row.get("origin_batch_id")!=null){var origin=rows("SELECT b.work_order_id origin_work_order_id,w.order_no origin_work_order_no,b.item_id origin_item_id,b.process_id origin_process_id,b.batch_no origin_batch_no,b.output_stage origin_output_stage FROM metal_process_batch b LEFT JOIN metal_work_order w ON w.work_order_id=b.work_order_id AND w.factory_id=b.factory_id WHERE b.batch_id=? AND b.factory_id=?",row.get("origin_batch_id"),row.get("factory_id"));if(!origin.isEmpty())result.putAll(clean(origin.get(0)));}
            result.put("customerName",row.get("customer_name_snapshot"));result.put("productType",row.get("product_type_snapshot"));result.put("metalTypeName",row.get("metal_type_name_snapshot"));if(!"RAW".equals(row.get("category")))result.put("customerCompanyName",row.get("customer_company_snapshot"));
            if("RAW".equals(row.get("category")))result.putAll(clean(one("SELECT "+RawMaterialKinds.labelSql("i","m")+" material_type,CASE WHEN i.material_ownership='FACTORY' THEN '工厂自有' ELSE c.company_name END customer_company_name FROM metal_inventory i LEFT JOIN metal_material m ON m.material_id=i.reference_id LEFT JOIN metal_customer c ON c.customer_id=i.customer_id WHERE i.inventory_id=?",id)));
            var held=WarehouseReservations.stock(jdbc,id);result.put("reservedWeight",held.get("weight"));result.put("reservedQuantity",held.get("quantity"));result.put("bookWeight",row.get("net_weight"));result.put("availableWeight",decimal(row.get("net_weight")).subtract(decimal(held.get("weight"))));result.put("availableQuantity",number(row.get("quantity"))-number(held.get("quantity")));
            result.put("pendingReservations",cleaned(rows("SELECT r.*,g.order_id,o.order_no,f.kind,f.status,f.stage FROM metal_stock_reservation r JOIN metal_warehouse_flow_line l ON l.flow_line_id=r.flow_line_id JOIN metal_warehouse_flow_group g ON g.group_id=l.group_id JOIN metal_warehouse_flow f ON f.flow_id=r.flow_id JOIN metal_warehouse_order o ON o.order_id=g.order_id WHERE r.inventory_id=? AND r.status='ACTIVE' ORDER BY r.reservation_id",id)));
            if("SEMI".equals(row.get("category")))result.putAll(semi.inventoryBalance(row));
            if("RAW".equals(row.get("category")))result.putAll(clean(new ProcessRawReturnService(jdbc,scope).provenance(id)));
            result.put("businessReferenced", !rows("SELECT l.line_id FROM metal_warehouse_order_line l JOIN metal_warehouse_order o ON o.order_id=l.order_id WHERE l.inventory_id=? AND (o.type NOT IN ('OPENING','ADJUST','RAW_IN','FINISHED_IN','SEMI_IN','RAW_PROCESS_IN') OR o.batch_id IS NOT NULL) LIMIT 1", id).isEmpty());
            result.put("warehouseName", str(one("SELECT name FROM metal_warehouse WHERE warehouse_id=?", row.get("warehouse_id")).get("name")));
            result.put("ledger", cleaned(rows("SELECT l.*,l.ledger_id id,o.order_no,o.type,o.work_order_id,w.order_no work_order_no,o.batch_id,b.item_id,b.process_id, d.organization customer_company_snapshot,CASE WHEN o.type<>'ADJUST' THEN NULL WHEN l.reversal_of IS NOT NULL THEN CASE WHEN NULLIF(TRIM(o.reason),'') IS NOT NULL THEN CONCAT('冲销：',o.reason) ELSE NULL END WHEN NULLIF(TRIM(d.remark),'') IS NOT NULL THEN d.remark WHEN o.status='POSTED' AND NULLIF(TRIM(o.reason),'') IS NOT NULL THEN o.reason ELSE NULL END adjustment_reason,(CASE WHEN l.reversal_of IS NULL THEN 1 ELSE -1 END)*(CASE WHEN o.kind='OUT' THEN -1 ELSE 1 END)*d.gross_weight delta_gross_weight,(CASE WHEN l.reversal_of IS NULL THEN 1 ELSE -1 END)*(CASE WHEN o.kind='OUT' THEN -1 ELSE 1 END)*d.impurity delta_impurity FROM metal_stock_ledger l JOIN metal_warehouse_order o ON o.order_id=l.order_id LEFT JOIN metal_work_order w ON w.work_order_id=o.work_order_id AND w.factory_id=o.factory_id LEFT JOIN metal_process_batch b ON b.batch_id=o.batch_id AND b.factory_id=o.factory_id LEFT JOIN metal_warehouse_order_line d ON d.line_id=l.line_id WHERE l.inventory_id=? ORDER BY l.ledger_id", id)));
        }
        if (key.equals("warehouse-order"))
            result.put("lines", cleaned(rows("SELECT *,line_id id FROM metal_warehouse_order_line WHERE order_id=? ORDER BY sequence_no", id)));
        if (key.equals("process-batch")) {
            result.put("orders", cleaned(rows("SELECT *,order_id id FROM metal_warehouse_order WHERE batch_id=? ORDER BY order_id", id)));
            result.put("reports", cleaned(rows("SELECT *,report_id id FROM metal_work_report WHERE batch_id=?", id)));
        }
        if (key.equals("work-report")) {
            result.putAll(clean(one("SELECT " + reportWeightColumns("t").substring(1) + " FROM metal_work_report t WHERE t.report_id=?", id)));
            var reportBatch = one("SELECT version,status FROM metal_process_batch WHERE batch_id=?", row.get("batch_id"));
            result.put("batchVersion", reportBatch.get("version"));
            result.put("batchStatus", reportBatch.get("status"));
            result.put("materials", cleaned(rows("SELECT *,report_material_id id FROM metal_report_material WHERE report_id=? ORDER BY code", id)));
            result.put("orders", cleaned(rows("SELECT *,order_id id FROM metal_warehouse_order WHERE batch_id=? ORDER BY order_id", row.get("batch_id"))));
        }
        if (key.equals("warning-record")) {
            result.put("report", detail("work-report", number(row.get("report_id"))));
            result.put("history", cleaned(rows("SELECT * FROM metal_warning_history WHERE warning_id=? ORDER BY history_id", id)));
        }
        return result;
    }

    /**
     * 校验工厂和版本后新增或修改库房，新增请求使用请求标识防止重复创建。
     */
    public String saveWarehouse(Warehouse input) {
        if (input.id() == null) {
            scope.lockFactories(List.of(input.factoryId()));
            uuid(input.requestId());
            String existing = creation("warehouse", input.requestId(), fingerprint(input));
            if (existing != null) return existing;
        }
        String saved = saveWarehouseInternal(input);
        if (input.id() == null) remember("warehouse", input.requestId(), fingerprint(input), saved);
        return saved;
    }

    private String saveWarehouseInternal(Warehouse input) {
        scope.lockFactories(List.of(input.factoryId()));
        if (input.name().isBlank()) throw fail("请输入库房名称");
        String status = input.status() == null ? "0" : input.status();
        if (input.id() == null)
            return Long.toString(insert("INSERT INTO metal_warehouse(factory_id,name,sort_order,status,create_by) VALUES(?,?,?,?,?)", input.factoryId(), input.name().strip(), input.sortOrder(), status, SecurityUtils.getUsername()));
        var old = lock("warehouse", input.id());
        version(old, input.version());
        if (!number(old.get("factory_id")).equals(input.factoryId())) throw fail("库房不能转移工厂");
        jdbc.update("UPDATE metal_warehouse SET name=?,sort_order=?,status=?,version=version+1,update_time=NOW() WHERE warehouse_id=?", input.name().strip(), input.sortOrder(), status, input.id());
        return input.id().toString();
    }

    /**
     * 拒绝已停用的预警规则配置入口。
     */
    public String saveRule(Rule input) {
        throw fail("预警规则已停用，预警按工单工序理论损耗判断");
    }

    private String saveRuleInternal(Rule input) {
        scope.lockFactories(List.of(input.factoryId()));
        if (rows("SELECT p.product_id FROM metal_product p JOIN metal_product_process x ON x.product_id=p.product_id JOIN metal_process s ON s.process_id=x.process_id WHERE p.product_id=? AND x.process_id=? AND p.del_flag='0' AND s.del_flag='0' FOR UPDATE", input.productId(), input.processId()).isEmpty())
            throw fail("工序必须属于所选有效产品工艺");
        String status = input.status() == null ? "0" : input.status();
        if (input.id() == null) {
            var existing = rows("SELECT * FROM metal_warning_rule WHERE factory_id=? AND product_id=? AND process_id=? FOR UPDATE", input.factoryId(), input.productId(), input.processId());
            if (!existing.isEmpty()) {
                if (!"2".equals(existing.get(0).get("del_flag"))) throw fail("该产品工序已有预警规则");
                Long id = number(existing.get(0).get("rule_id"));
                jdbc.update("UPDATE metal_warning_rule SET tolerance=?,status=?,del_flag='0',version=version+1 WHERE rule_id=?", input.tolerance(), status, id);
                return id.toString();
            }
            return Long.toString(insert("INSERT INTO metal_warning_rule(factory_id,product_id,process_id,tolerance,status) VALUES(?,?,?,?,?)", input.factoryId(), input.productId(), input.processId(), input.tolerance(), status));
        }
        var old = lock("warning-rule", input.id());
        version(old, input.version());
        if (!number(old.get("factory_id")).equals(input.factoryId()) || !number(old.get("product_id")).equals(input.productId()) || !number(old.get("process_id")).equals(input.processId()))
            throw fail("已有规则不能改变工厂、产品或工序，请新增规则");
        jdbc.update("UPDATE metal_warning_rule SET tolerance=?,status=?,version=version+1 WHERE rule_id=?", input.tolerance(), status, input.id());
        return input.id().toString();
    }

    /**
     * 检查资源类型及业务引用后批量归档记录。
     */
    public void archive(String key, Long[] ids) {
        if (!Set.of("warehouse", "inventory", "warning-rule", "warning-record").contains(key) || ids.length < 1 || ids.length > 100)
            throw fail("请选择1～100条可归档记录");
        var sorted = Arrays.stream(ids).distinct().sorted().toList();
        var initial = sorted.stream().map(id -> record(key, id, false)).toList();
        scope.lockFactories(initial.stream().map(r -> number(r.get("factory_id"))).toList());
        for (Long id : sorted) {
            var row = record(key, id, true);
            if (key.equals("warehouse") && (!rows("SELECT inventory_id FROM metal_inventory WHERE warehouse_id=? LIMIT 1", id).isEmpty() || !rows("SELECT order_id FROM metal_warehouse_order WHERE warehouse_id=? LIMIT 1", id).isEmpty()))
                throw fail("库房存在库存或历史单据引用");
            if (key.equals("inventory") && (decimal(row.get("net_weight")).signum() != 0 || number(row.get("quantity")) != 0 || !rows("SELECT l.line_id FROM metal_warehouse_order_line l JOIN metal_warehouse_order o ON o.order_id=l.order_id WHERE l.inventory_id=? AND (o.type NOT IN ('OPENING','ADJUST','RAW_IN','FINISHED_IN','SEMI_IN','RAW_PROCESS_IN') OR o.batch_id IS NOT NULL) LIMIT 1", id).isEmpty()))
                throw fail("库存须为零且无业务引用");
            if (key.equals("warning-record") && "PENDING".equals(row.get("status")) && !truth(row.get("source_void")))
                throw fail("待处理预警不能归档");
            Resource r = resource(key);
            jdbc.update("UPDATE " + r.table() + " SET del_flag='2',version=version+1 WHERE " + r.pk() + "=?", id);
            if (key.equals("warning-record")) history(id, "ARCHIVE", "归档记录");
        }
    }

    private static boolean truth(Object v) {
        return Boolean.TRUE.equals(v) || (v instanceof Number n && n.intValue() != 0);
    }

    private String creation(String kind, String requestId, String hash) {
        var values = rows("SELECT * FROM metal_warehouse_request WHERE request_id=? FOR UPDATE", requestId);
        if (values.isEmpty()) return null;
        var r = values.get(0);
        if (!kind.equals(r.get("kind")) || !hash.equals(r.get("request_hash")) || !SecurityUtils.getUserId().equals(number(r.get("actor_id"))))
            throw fail("请求标识已用于其他内容");
        return str(r.get("result_id"));
    }

    private void remember(String kind, String id, String hash, String result) {
        jdbc.update("INSERT INTO metal_warehouse_request(request_id,kind,request_hash,actor_id,result_id) VALUES(?,?,?,?,?)", id, kind, hash, SecurityUtils.getUserId(), number(result));
    }

    private String fingerprint(Object request) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(request.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void uuid(String value) {
        try {
            if (!UUID.fromString(value).toString().equalsIgnoreCase(value)) throw fail("无效UUID");
        } catch (IllegalArgumentException e) {
            throw fail("请求标识必须为UUID");
        }
    }

    private String duplicate(String table, String pk, String requestId, String hash) {
        uuid(requestId);
        var data = rows("SELECT " + pk + ",request_hash,request_actor,status FROM " + table + " WHERE request_id=? FOR UPDATE", requestId);
        if (data.isEmpty()) return null;
        var row = data.get(0);
        if (!SecurityUtils.getUserId().equals(number(row.get("request_actor"))) || !hash.equals(row.get("request_hash")))
            throw fail("同一请求标识不能提交不同内容");
        if ("VOID".equals(row.get("status"))) throw fail("原请求已冲销，请使用新的请求标识");
        return str(row.get(pk));
    }

    private String next(String prefix) {
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        jdbc.update("INSERT INTO metal_stock_sequence(sequence_date,prefix,sequence_value) VALUES(?,?,0) ON DUPLICATE KEY UPDATE sequence_value=sequence_value", date, prefix);
        var row = one("SELECT sequence_value FROM metal_stock_sequence WHERE sequence_date=? AND prefix=? FOR UPDATE", date, prefix);
        long value = number(row.get("sequence_value")) + 1;
        if (value > 999999) throw fail("当日单据编号已用完");
        jdbc.update("UPDATE metal_stock_sequence SET sequence_value=? WHERE sequence_date=? AND prefix=?", value, date, prefix);
        return prefix + date.format(DateTimeFormatter.BASIC_ISO_DATE) + String.format("%06d", value);
    }

    private Map<String, Object> workOrder(Long id) {
        var w = one("SELECT * FROM metal_work_order WHERE work_order_id=? AND del_flag='0' FOR UPDATE", id);
        scope.check(number(w.get("factory_id")));
        if (!Set.of("WAITING_MATERIAL", "IN_PROGRESS", "COMPLETED").contains(w.get("status")))
            throw fail("该工单不能执行生产业务");
        return w;
    }

    private void requireProductionMetal(Map<String,Object> step) {
        var saved=JSON.parseObject(str(step.get("product_snapshot")));
        if(ProcessingPolicy.v2(step)){long metal=ProcessingPolicy.metalId(saved);if(rows("SELECT metal_type_id FROM metal_type WHERE metal_type_id=?",metal).isEmpty())throw fail("工单金属已失效");return;}
        if(saved!=null&&saved.get("metalTypeId")!=null&&!"1".equals(saved.get("metalTypeId").toString()))throw fail("其他金属本轮仅支持建档和重量试算，暂不能投产");
    }

    private Map<String, Object> step(Long itemId, Long processId) {
        return one("SELECT s.*,i.product_id,i.material_id,i.planned_input_weight,i.quantity item_quantity,COALESCE(i.material_snapshot,i.product_snapshot) product_snapshot,i.work_order_id,w.task_type,w.processing_policy_version FROM metal_work_order_step s JOIN metal_work_order_item i ON i.item_id=s.item_id JOIN metal_work_order w ON w.work_order_id=i.work_order_id WHERE s.item_id=? AND s.process_id=?", itemId, processId);
    }

    private long sumQuantity(Long itemId, int seq, String status) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(" + (status.equals("OPEN") ? "planned_quantity" : "completed_quantity") + "),0) FROM metal_process_batch WHERE item_id=? AND sequence_no=? AND status=?", Long.class, itemId, seq, status);
    }

    /**
     * 校验工单、工序和可加工数量，并按请求标识创建加工批次。
     */
    public String createBatch(Batch input) {
        var initial = one("SELECT factory_id FROM metal_work_order WHERE work_order_id=? AND del_flag='0'", input.workOrderId());
        scope.lockFactories(List.of(number(initial.get("factory_id"))));
        String hash = fingerprint(input), old = duplicate("metal_process_batch", "batch_id", input.requestId(), hash);
        if (old != null) return old;
        var w = workOrder(input.workOrderId());
        if ("COMPLETED".equals(w.get("status"))) throw fail("已完成工单不能新增加工批次");
        var s = step(input.itemId(), input.processId());
        requireProductionMetal(s);
        if (!input.workOrderId().equals(number(s.get("work_order_id")))) throw fail("产品明细不属于所选工单");
        int seq = number(s.get("sequence_no")).intValue();
        boolean rawStage=ProcessingPolicy.v2(s)&&"RAW".equals(s.get("output_stage"));
        if(rawStage&&input.plannedQuantity()!=0||!rawStage&&input.plannedQuantity()<1)throw fail("原料工序不填写件数，产品工序须提供有效加工数量");
        long max = number(s.get("item_quantity")) - semi.credit(input.itemId(), seq) - WarehouseReservations.pendingCredit(jdbc,input.itemId(),seq);
        long used = sumQuantity(input.itemId(), seq, "SETTLED") + sumQuantity(input.itemId(), seq, "OPEN");
        if (Boolean.TRUE.equals(s.get("weight_stage_closed"))) throw fail("本工序已确认结束投产，请先受控重新打开");
        if (!SemiMeasurementRules.weight(s) && input.plannedQuantity() > max - used) throw fail("加工数量超过工单或前工序可用数量");
        var product = JSON.parseObject(str(s.get("product_snapshot")));
        long saved = insert("INSERT INTO metal_process_batch(request_id,request_hash,request_actor,factory_id,work_order_id,item_id,process_id,sequence_no,planned_quantity,completion_policy,measurement_mode,processing_policy_version,process_role,output_stage,metal_type_id) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", input.requestId(), hash, SecurityUtils.getUserId(), w.get("factory_id"), input.workOrderId(), input.itemId(), input.processId(), seq, input.plannedQuantity(), WarehouseFlowRules.POLICY, SemiMeasurementRules.mode(s.get("measurement_mode")),ProcessingPolicy.version(s),s.get("process_role"),s.get("output_stage"),product==null?null:product.get("metalTypeId"));
        LocalDateTime stamp = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        String no;
        do {
            no = w.get("order_no") + "-" + stamp.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")) + "-" + product.get("code");
            stamp = stamp.plusNanos(1000000);
        } while (!rows("SELECT batch_id FROM metal_process_batch WHERE batch_no=?", no).isEmpty());
        jdbc.update("UPDATE metal_process_batch SET batch_no=? WHERE batch_id=?", no, saved);
        return Long.toString(saved);
    }

    private Map<String, Object> warehouse(Long id, Long factoryId, boolean active) {
        var h = one("SELECT * FROM metal_warehouse WHERE warehouse_id=? AND del_flag='0' FOR UPDATE", id);
        if (!factoryId.equals(number(h.get("factory_id")))) throw new ServiceException("库房不属于本厂", 403);
        if (active && !"0".equals(h.get("status"))) throw fail("库房已停用");
        return h;
    }

    private String person(Long id, Long factoryId, boolean required) {
        if (id == null) {
            if (required) throw fail("请选择经手人、仓管员或领用人");
            return null;
        }
        var data = rows("SELECT COALESCE(NULLIF(nick_name,''),user_name) name FROM sys_user WHERE user_id=? AND factory_id=? AND status='0' AND del_flag='0' FOR UPDATE", id, factoryId);
        if (data.isEmpty()) throw fail("单据人员必须属于本厂且启用");
        return str(data.get(0).get("name"));
    }

    private Map<String, Object> source(String category, Long referenceId) {
        if (category == null) throw fail("请选择库存分类");
        if (referenceId == null || referenceId <= 0) throw fail("请选择物料或产品");
        if ("RAW".equals(category))
            return one("SELECT m.material_id id,m.material_code code,CONCAT(t.name,m.material_type) name,m.material_type,m.metal_type_id,t.name metal_type_name,m.specification FROM metal_material m JOIN metal_type t ON t.metal_type_id=m.metal_type_id WHERE m.material_id=? AND m.del_flag='0' FOR UPDATE", referenceId);
        if (Set.of("FINISHED", "SEMI").contains(category))
            return one("SELECT p.product_id id,p.product_code code,p.product_name name,p.specification,p.purity,p.product_type,p.metal_type_id,t.name metal_type_name FROM metal_product p JOIN metal_type t ON t.metal_type_id=p.metal_type_id WHERE p.product_id=? AND p.del_flag='0' FOR UPDATE", referenceId);
        throw fail("库存分类无效");
    }

    private record Type(String kind, String category, int sign) {
    }

    private Type type(String value) {
        return switch (value) {
            case "PRODUCTION_OUT" -> new Type("OUT", null, -1);
            case "PRODUCT_OUT" -> new Type("OUT", "FINISHED", -1);
            case "EXCHANGE_OUT" -> new Type("OUT", "RAW", -1);
            case "OWNERSHIP_OUT" -> new Type("OUT", "RAW", -1);
            case "OWNERSHIP_IN", "RESIDUAL_STORAGE" -> new Type("IN", "RAW", 1);
            case "SEMI_OUT" -> new Type("OUT", "SEMI", -1);
            case "RAW_IN", "ACID_IN", "RAW_PROCESS_IN" -> new Type("IN", "RAW", 1);
            case "ACID_OUT" -> new Type("OUT", "RAW", -1);
            case "ACID_RETURN" -> new Type("RET", "RAW", 1);
            case "FINISHED_IN" -> new Type("IN", "FINISHED", 1);
            case "SEMI_IN" -> new Type("IN", "SEMI", 1);
            case "PROCESS_RAW_RETURN" -> new Type("RET", "RAW", 1);
            case "RAW_RETURN" -> new Type("RET", "RAW", 1);
            case "SEMI_RETURN" -> new Type("RET", "SEMI", 1);
            case "PRODUCT_RETURN" -> new Type("RET", "FINISHED", 1);
            case "OPENING", "ADJUST" -> new Type("ADJ", null, 1);
            default -> throw fail("单据类型无效");
        };
    }

    /**
     * 校验请求归属和单据类别，预留可重复获取的仓储单据编号。
     */
    public String reserveOrderNumber(String requestId, String kind) {
        uuid(requestId);
        if (!Set.of("OUT", "IN", "RET", "ADJ").contains(kind)) throw fail("单据类别无效");
        jdbc.update("INSERT INTO metal_warehouse_number(request_id,actor_id,kind) VALUES(?,?,?) ON DUPLICATE KEY UPDATE request_id=request_id", requestId, SecurityUtils.getUserId(), kind);
        var row = one("SELECT actor_id,kind,order_no FROM metal_warehouse_number WHERE request_id=? FOR UPDATE", requestId);
        if (!SecurityUtils.getUserId().equals(number(row.get("actor_id"))) || !kind.equals(row.get("kind")))
            throw fail("编号申请归属或单据类别不一致");
        if (row.get("order_no") != null) return str(row.get("order_no"));
        String no = next(kind);
        jdbc.update("UPDATE metal_warehouse_number SET order_no=? WHERE request_id=?", no, requestId);
        return no;
    }

    /**
     * 校验并过账仓储单据，同时更新相应库存。
     */
    public String post(Order input) {
        if (Set.of("OPENING", "ADJUST").contains(input.type())) throw fail("请使用期初或调整接口");
        if(input.type().startsWith("ACID_"))throw fail("酸洗单据须通过独立酸洗批次提交");
        return postInternal(input, false);
    }

    /**
     * 校验期初录入权限和单据类型后登记期初库存。
     */
    public String opening(Order input) {
        permit("metal:inventory:add");
        if (!"OPENING".equals(input.type()) || input.batchId() != null) throw fail("期初类型无效");
        return postInternal(input, true);
    }

    private Order autoBind(Order x) {
        if (x.workOrderId() == null || x.itemId() == null || x.processId() == null)
            throw fail("请选择生产工单、产品明细和工序");
        var w = workOrder(x.workOrderId());
        if (!x.factoryId().equals(number(w.get("factory_id")))) throw fail("工单不属于所选工厂");
        var s = step(x.itemId(), x.processId());
        if (!x.workOrderId().equals(number(s.get("work_order_id")))) throw fail("产品明细不属于工单");
        var open = rows("SELECT * FROM metal_process_batch WHERE item_id=? AND process_id=? AND status='OPEN' ORDER BY batch_id FOR UPDATE", x.itemId(), x.processId());
        if (open.size() > 1) throw fail("存在多个历史未结算批次，请先结算或冲销多余批次");
        Long id;
        if (open.isEmpty()) {
            if (!x.type().equals("PRODUCTION_OUT")) throw fail("请先生产领料，系统将自动生成批次号");
            int seq = number(s.get("sequence_no")).intValue();
            long available = number(s.get("item_quantity")) - semi.credit(x.itemId(), seq) - WarehouseReservations.pendingCredit(jdbc,x.itemId(),seq) - sumQuantity(x.itemId(), seq, "SETTLED");
            if(SemiMeasurementRules.weight(s))available=number(s.get("item_quantity"));
            if(ProcessingPolicy.v2(s)&&"RAW".equals(s.get("output_stage")))available=0;
            else if (available <= 0) throw fail("当前工序没有可加工数量，请先完成前道工序");
            id = Long.valueOf(createBatch(new Batch(UUID.randomUUID().toString(), x.workOrderId(), x.itemId(), x.processId(), (int) available)));
        } else id = number(open.get(0).get("batch_id"));
        var b = record("process-batch", id, true);
        return new Order(x.requestId(), x.factoryId(), x.warehouseId(), x.type(), x.orderDate(), x.operatorId(), x.operatorIds(), x.keeperId(), x.recipientId(), x.inspectorId(), x.reviewerId(), x.workOrderId(), id, number(b.get("version")).intValue(), x.settle(), x.completedQuantity(), x.reason(), x.lines(), x.itemId(), x.processId());
    }

    private String postInternal(Order input, boolean internal) {
        return postInternal(input, internal, false);
    }

    // Package-private: only the transactional return confirmation orchestrator uses this path.
    String postCompletionDocument(Order input) { return postInternal(input, false, true); }

    private String postInternal(Order input, boolean internal, boolean completion) {return postInternal(input,internal,completion,null);}
    Order bindInboundSubmission(Order input){scope.lockFactories(List.of(input.factoryId()));return input.batchId()==null&&(input.itemId()!=null||input.processId()!=null)?autoBind(input):input;}
    String postInboundChild(Order input,long submissionId){
        var root=jdbc.queryForMap("SELECT * FROM metal_inbound_submission WHERE submission_id=? FOR UPDATE",submissionId);
        if(!Objects.equals(number(root.get("request_actor")),SecurityUtils.getUserId())||!Objects.equals(number(root.get("factory_id")),input.factoryId())||!Objects.equals(root.get("type"),input.type())||!Objects.equals(number(root.get("batch_id")),input.batchId()))throw fail("入库提交关联无效");
        return postInternal(input,false,false,submissionId);
    }
    String postOutboundChild(Order input,long submissionId){
        var root=jdbc.queryForMap("SELECT * FROM metal_outbound_submission WHERE submission_id=? FOR UPDATE",submissionId);
        if(!Objects.equals(number(root.get("request_actor")),SecurityUtils.getUserId())||!Objects.equals(number(root.get("factory_id")),input.factoryId())||!"PRODUCTION_OUT".equals(input.type())||!Objects.equals(number(root.get("batch_id")),input.batchId()))throw fail("出库提交关联无效");
        return postInternal(input,false,false,null,submissionId);
    }
    private String postInternal(Order input, boolean internal, boolean completion, Long submissionId) {return postInternal(input,internal,completion,submissionId,null);}
    String postAcidChild(Order input,long acidId) {
        var root=one("SELECT * FROM metal_acid_wash_batch WHERE acid_wash_id=? FOR UPDATE",acidId);
        if(!Set.of("ACID_OUT","ACID_IN","ACID_RETURN").contains(input.type())||!"OPEN".equals(root.get("status"))||!Objects.equals(input.factoryId(),number(root.get("factory_id")))||input.batchId()!=null||input.workOrderId()!=null)throw fail("酸洗批次关联无效");
        return postInternal(input,false,false,null,null,acidId);
    }
    void reverseAcidDocuments(long acidId,String reason) {
        reverseDocuments(rows("SELECT * FROM metal_warehouse_order WHERE acid_wash_id=? ORDER BY order_id",acidId),reason);
    }
    private String postInternal(Order input, boolean internal, boolean completion, Long submissionId,Long outboundSubmissionId) {return postInternal(input,internal,completion,submissionId,outboundSubmissionId,null);}
    private String postInternal(Order input, boolean internal, boolean completion, Long submissionId,Long outboundSubmissionId,Long acidId) {return postInternal(input,internal,completion,submissionId,outboundSubmissionId,acidId,null);}
    Map<String,Object> workflowHeader(Order input) {
        var h=warehouse(input.warehouseId(),input.factoryId(),true);
        var operators=input.operatorIds()==null?(input.operatorId()==null?List.<Long>of():List.of(input.operatorId())):input.operatorIds();
        if(operators.size()>20||new HashSet<>(operators).size()!=operators.size()||operators.isEmpty()&&!"PRODUCTION_OUT".equals(input.type()))throw fail("请选择合法经办人");
        var names=new ArrayList<String>();for(Long id:operators)names.add(person(id,input.factoryId(),true));
        if(input.operatorId()!=null&&(operators.isEmpty()||!input.operatorId().equals(operators.get(0))))throw fail("经办人字段不一致");
        var result=new LinkedHashMap<String,Object>();result.put("warehouseName",h.get("name"));result.put("operatorIds",operators);
        result.put("operatorName",String.join("、",names));result.put("keeperName",person(input.keeperId(),input.factoryId(),true));
        result.put("recipientName",person(input.recipientId(),input.factoryId(),Set.of("PRODUCTION_OUT","SEMI_OUT","PRODUCT_OUT").contains(input.type())));
        result.put("reviewerName",person(input.reviewerId(),input.factoryId(),false));result.put("inspectorName",person(input.inspectorId(),input.factoryId(),false));return result;
    }
    long pendingWorkflowOrder(Order input,long flow) {
        var names=workflowHeader(input);var t=type(input.type());String no=reserveOrderNumber(input.requestId(),t.kind());
        var operators=(List<Long>)names.get("operatorIds");
        long id=insert("INSERT INTO metal_warehouse_order(order_no,request_id,request_hash,request_actor,factory_id,warehouse_id,kind,type,order_date,work_order_id,batch_id,operator_id,keeper_id,recipient_id,inspector_id,reviewer_id,operator_name,keeper_name,recipient_name,inspector_name,reviewer_name,warehouse_name,reason,status,warehouse_flow_id,operator_ids) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",no,input.requestId(),fingerprint(input),SecurityUtils.getUserId(),input.factoryId(),input.warehouseId(),t.kind(),input.type(),input.orderDate(),input.workOrderId(),input.batchId(),operators.isEmpty()?null:operators.get(0),input.keeperId(),input.recipientId(),input.inspectorId(),input.reviewerId(),names.get("operatorName"),names.get("keeperName"),names.get("recipientName"),names.get("inspectorName"),names.get("reviewerName"),names.get("warehouseName"),input.reason(),t.sign()<0?"WAITING_OUT":"WAITING_IN",flow,JSON.toJSONString(operators.stream().map(String::valueOf).toList()));
        return id;
    }
    String postWorkflowDocument(Order input,long document) {
        var row=one("SELECT o.*,g.status group_status,f.status flow_status FROM metal_warehouse_order o JOIN metal_warehouse_flow_group g ON g.group_id=o.workflow_group_id JOIN metal_warehouse_flow f ON f.flow_id=g.flow_id WHERE o.order_id=? FOR UPDATE",document);
        if(!Set.of("WAITING_OUT","WAITING_IN").contains(row.get("status"))||!"CONFIRMED".equals(row.get("group_status"))||!Set.of("WAITING","COLLECTING").contains(row.get("flow_status")))throw fail("仓储确认上下文无效");
        return postInternal(input,false,true,null,null,null,document);
    }
    void settleWorkflow(long batchId,long flow) {
        permit("metal:process-batch:settle");
        WarehouseReservations.batch(jdbc,batchId,flow);
        var batch=record("process-batch",batchId,true);
        if(!WarehouseFlowRules.POLICY.equals(batch.get("completion_policy")))throw fail("该批次不是双称重加工交接策略");
        var root=one("SELECT batch_id,kind,status FROM metal_warehouse_flow WHERE flow_id=? FOR UPDATE",flow);
        if(!Objects.equals(number(root.get("batch_id")),batchId)||!"HANDOVER".equals(root.get("kind"))||!Set.of("WAITING","COLLECTING").contains(root.get("status")))throw fail("加工交接结算关联无效");
        int quantity=jdbc.queryForObject("SELECT COALESCE(SUM(l.quantity),0) FROM metal_warehouse_order o JOIN metal_warehouse_order_line l ON l.order_id=o.order_id WHERE o.warehouse_flow_id=? AND o.status='POSTED' AND o.type IN ('SEMI_IN','FINISHED_IN','RAW_PROCESS_IN')",Integer.class,flow);
        settleInternal(batch,quantity);
    }
    void reverseWorkflow(long flow,String reason) {
        if(reason==null||reason.isBlank())throw fail("请填写冲销原因");
        var root=one("SELECT * FROM metal_warehouse_flow WHERE flow_id=? FOR UPDATE",flow);scope.check(number(root.get("factory_id")));
        if(root.get("batch_id")!=null)throw fail("生产关联申请须整体冲销加工批次");
        if(root.get("closeout_id")!=null)throw fail("客户余料收尾须从收尾入口整体撤回");
        reverseDocuments(rows("SELECT * FROM metal_warehouse_order WHERE warehouse_flow_id=? AND status='POSTED' ORDER BY order_id DESC",flow),reason);
    }
    void reverseCloseoutDocuments(long closeout,String reason) {
        var docs=rows("SELECT o.* FROM metal_warehouse_order o JOIN metal_warehouse_flow f ON f.flow_id=o.warehouse_flow_id WHERE f.closeout_id=? AND o.status='POSTED' ORDER BY o.order_id DESC",closeout);
        for(var stock:rows("SELECT DISTINCT r.inventory_id FROM metal_work_order_residual r JOIN metal_customer_residual_closeout_line l ON l.residual_id=r.residual_id WHERE l.closeout_id=?",closeout))WarehouseReservations.stockReference(jdbc,number(stock.get("inventory_id")));
        if(!rows("SELECT u.use_id FROM metal_work_order_residual_use u JOIN metal_customer_residual_closeout_line l ON l.residual_id=u.residual_id JOIN metal_warehouse_order_line d ON d.line_id=u.line_id JOIN metal_warehouse_order o ON o.order_id=d.order_id LEFT JOIN metal_warehouse_flow f ON f.flow_id=o.warehouse_flow_id WHERE l.closeout_id=? AND u.reversed=FALSE AND o.status='POSTED' AND (f.closeout_id IS NULL OR f.closeout_id<>?) AND o.order_id>(SELECT MIN(first_order.order_id) FROM metal_warehouse_order first_order JOIN metal_warehouse_flow first_flow ON first_flow.flow_id=first_order.warehouse_flow_id WHERE first_flow.closeout_id=?)",closeout,closeout,closeout).isEmpty())throw fail("余料已再领用，不能撤回收尾");
        reverseDocuments(docs,reason);
        jdbc.update("UPDATE metal_work_order_residual r JOIN metal_customer_residual_closeout_line l ON l.residual_id=r.residual_id SET r.status='OPEN',r.version=r.version+1 WHERE l.closeout_id=?",closeout);
    }
    void postResidualTransfer(Order input,long document) {
        var target=one("SELECT o.*,f.closeout_id,c.mode FROM metal_warehouse_order o JOIN metal_warehouse_flow f ON f.flow_id=o.warehouse_flow_id JOIN metal_customer_residual_closeout c ON c.closeout_id=f.closeout_id JOIN metal_warehouse_flow_group g ON g.group_id=o.workflow_group_id WHERE o.order_id=? AND g.status='CONFIRMED' FOR UPDATE",document);
        boolean storage="RESIDUAL_STORAGE".equals(input.type());
        if(!Set.of("WAITING_IN","WAITING_OUT").contains(target.get("status"))||!Objects.equals(target.get("type"),input.type())||!(storage?"STORAGE":"OFFSET").equals(target.get("mode")))throw fail("余料收尾单据关联无效");
        jdbc.update("UPDATE metal_warehouse_order SET status='POSTED',version=version+1 WHERE order_id=?",document);
        int index=0;
        for(var line:input.lines()){
            var sourceStock=record("inventory",line.inventoryId(),true);Long stockId=number(sourceStock.get("inventory_id"));
            if(!"RAW".equals(sourceStock.get("category"))||!"CUSTOMER".equals(MaterialOwnership.of(sourceStock))||!input.warehouseId().equals(number(sourceStock.get("warehouse_id"))))throw fail("收尾须关联原客户原料库存");
            Map<String,Object> stock=sourceStock;Long recoveryOrigin=number(sourceStock.get("origin_recovery_line_id"));
            if(recoveryOrigin==null){var sources=rows("SELECT line_id FROM metal_process_raw_return_line WHERE inventory_id=?",stockId);if(!sources.isEmpty())recoveryOrigin=number(sources.get(0).get("line_id"));}
            if(!storage){
                var matches=rows("SELECT *,inventory_id id FROM metal_inventory WHERE factory_id=? AND warehouse_id=? AND category='RAW' AND reference_id=? AND batch_no=? AND raw_customer_key=? AND raw_kind_key=? AND raw_origin_key=? AND ownership_status='NORMAL' FOR UPDATE",input.factoryId(),input.warehouseId(),sourceStock.get("reference_id"),sourceStock.get("batch_no"),"F:"+input.factoryId(),sourceStock.get("raw_material_kind"),sourceStock.get("raw_origin_key"));
                if(matches.isEmpty()){
                    stockId=insert("INSERT INTO metal_inventory(factory_id,warehouse_id,category,reference_id,batch_no,code,name,specification,purity,organization,material_ownership,raw_material_kind,origin_acid_wash_id,origin_batch_id,powder_origin,washing_status,smelting_status,origin_policy_version,origin_verification_id,metal_type_id_snapshot,metal_type_name_snapshot,source_process_id,source_process_name) SELECT factory_id,warehouse_id,category,reference_id,batch_no,code,name,specification,purity,'工厂自有','FACTORY',raw_material_kind,origin_acid_wash_id,origin_batch_id,powder_origin,washing_status,smelting_status,origin_policy_version,origin_verification_id,metal_type_id_snapshot,metal_type_name_snapshot,source_process_id,source_process_name FROM metal_inventory WHERE inventory_id=?",sourceStock.get("inventory_id"));
                    jdbc.update("UPDATE metal_inventory SET origin_recovery_line_id=? WHERE inventory_id=?",recoveryOrigin,stockId);stock=record("inventory",stockId,true);
                }else{stock=matches.get(0);stockId=number(stock.get("inventory_id"));if(!"0".equals(stock.get("del_flag")))throw fail("工厂目标库存已归档，请先核对");if(!Objects.equals(recoveryOrigin,number(stock.get("origin_recovery_line_id"))))throw fail("工厂目标库存的回收来源不一致，不能混并");}
            }
            long lineId=insert("INSERT INTO metal_warehouse_order_line(order_id,inventory_id,sequence_no,category,reference_id,code,name,specification,batch_no,net_weight,quantity,organization,customer_id,material_ownership,raw_material_kind) VALUES(?,?,?,'RAW',?,?,?,?,?,?,0,?,?,?,?)",document,stockId,++index,stock.get("reference_id"),stock.get("code"),stock.get("name"),stock.get("specification"),stock.get("batch_no"),line.netWeight(),storage?sourceStock.get("organization"):"工厂自有",storage?sourceStock.get("customer_id"):null,storage?"CUSTOMER":"FACTORY",stock.get("raw_material_kind"));
            jdbc.update("UPDATE metal_warehouse_order_line SET powder_origin=?,washing_status=?,smelting_status=?,origin_policy_version=?,origin_verification_id=? WHERE line_id=?",sourceStock.get("powder_origin"),sourceStock.get("washing_status"),sourceStock.get("smelting_status"),sourceStock.get("origin_policy_version"),sourceStock.get("origin_verification_id"),lineId);
            jdbc.update("UPDATE metal_warehouse_flow_line l JOIN metal_warehouse_flow_group g ON g.group_id=l.group_id SET l.posted_line_id=? WHERE g.order_id=? AND l.sequence_no=?",lineId,document,index);
            if(!storage)balance(stock,line.netWeight(),0,document,lineId,null);
        }
    }
    private String postInternal(Order input, boolean internal, boolean completion, Long submissionId,Long outboundSubmissionId,Long acidId,Long workflowDocument) {
        scope.lockFactories(List.of(input.factoryId()));
        Type t = type(input.type());
        String reservedNo = workflowDocument==null?reserveOrderNumber(input.requestId(), t.kind()):str(one("SELECT order_no FROM metal_warehouse_order WHERE order_id=?",workflowDocument).get("order_no"));
        if(submissionId==null&&jdbc.queryForObject("SELECT COUNT(*) FROM metal_inbound_submission WHERE request_id=?",Long.class,input.requestId())>0)throw new ServiceException("请求编号已用于多仓入库，不能更改内容或切换为单仓重试",409);
        if(outboundSubmissionId==null&&jdbc.queryForObject("SELECT COUNT(*) FROM metal_outbound_submission WHERE request_id=?",Long.class,input.requestId())>0)throw new ServiceException("请求编号已用于多仓出库，不能更改内容或切换为单仓重试",409);
        String hash = fingerprint(input), existing = workflowDocument==null?duplicate("metal_warehouse_order", "order_id", input.requestId(), hash):null;
        if (existing != null) return existing;
        input=WarehousePrecision.order(input);
        if (ProcessRawReturnService.TYPE.equals(input.type())) {
            var ctx = new ProcessRawReturnService(jdbc,scope).context(input.factoryId(),input.workOrderId(),input.itemId(),input.processId(),input.batchId());
            if(input.batchId()==null||input.batchVersion()==null||!input.batchId().toString().equals(ctx.get("batchId"))||input.batchVersion().longValue()!=number(ctx.get("version")))throw new ServiceException("加工余料批次版本已变化，请刷新后重试",409);
            if(input.reason()==null||input.reason().isBlank()||input.reason().length()>500)throw fail("请填写1至500字加工余料还库原因");
        } else if (input.batchId() == null && (input.itemId() != null || input.processId() != null)) input = autoBind(input);
        lockPostingSources(input);
        var h = warehouse(input.warehouseId(), input.factoryId(), true);
        List<Long> operators = input.operatorIds() == null ? (input.operatorId() == null ? List.of() : List.of(input.operatorId())) : input.operatorIds();
        if ((operators.isEmpty() && !input.type().equals("PRODUCTION_OUT")) || operators.size() > 20 || new HashSet<>(operators).size() != operators.size())
            throw fail("请选择1至20位不重复经办人");
        if (input.operatorId() != null && (operators.isEmpty() || !input.operatorId().equals(operators.get(0))))
            throw fail("经办人字段不一致");
        var operatorNames = new LinkedHashMap<Long, String>();
        for (Long operator : new TreeSet<>(operators))
            operatorNames.put(operator, person(operator, input.factoryId(), true));
        String op = operators.stream().map(operatorNames::get).collect(java.util.stream.Collectors.joining("、")), keeper = person(input.keeperId(), input.factoryId(), true), recipient = person(input.recipientId(), input.factoryId(), Set.of("PRODUCTION_OUT", "SEMI_OUT").contains(input.type())), inspector = person(input.inspectorId(), input.factoryId(), false), reviewer = person(input.reviewerId(), input.factoryId(), false);
        Map<String, Object> batch = null, w = null, s = null;
        if (input.batchId() != null) {
            batch = record("process-batch", input.batchId(), true);
            if (!input.factoryId().equals(number(batch.get("factory_id")))) throw fail("批次不属于所选工厂");
            version(batch, input.batchVersion());
            if(input.itemId()!=null&&!input.itemId().equals(number(batch.get("item_id")))||input.processId()!=null&&!input.processId().equals(number(batch.get("process_id"))))throw fail("产品明细、工序和指定批次不一致");
            if (!"OPEN".equals(batch.get("status"))) throw fail("批次已结算或已冲销");
            if(WarehouseFlowRules.POLICY.equals(batch.get("completion_policy"))&&workflowDocument==null)throw fail("本批次须通过实领确认及加工交接验收入库，不能直接过账");
            if (BatchCompletionRules.POLICY.equals(batch.get("completion_policy"))) {
                if (Set.of("SEMI_IN","FINISHED_IN","RAW_PROCESS_IN").contains(input.type())) {
                    long count = submissionId==null?jdbc.queryForObject("SELECT COUNT(DISTINCT "+BatchOutputQueries.actionKey("o")+") FROM metal_warehouse_order o WHERE batch_id=? AND status='POSTED' AND type IN ('SEMI_IN','FINISHED_IN','RAW_PROCESS_IN')",Long.class,input.batchId()):jdbc.queryForObject("SELECT COUNT(*) FROM metal_warehouse_order WHERE batch_id=? AND status='POSTED' AND type IN ('SEMI_IN','FINISHED_IN','RAW_PROCESS_IN') AND (inbound_submission_id IS NULL OR inbound_submission_id<>?)",Long.class,input.batchId(),submissionId);
                    BatchCompletionRules.requireOutput(count);
                }
                if (Boolean.TRUE.equals(input.settle())) throw fail("请先登记产出，再确认全部还料或无余料；不能直接结算");
                if (Set.of("RAW_RETURN","SEMI_RETURN","PROCESS_RAW_RETURN").contains(input.type()) && !completion)
                    throw fail("本批次须从全部还料确认入口一次提交全部还料");
            }

            if (!Set.of("PRODUCTION_OUT", "RAW_RETURN", "SEMI_RETURN", "PROCESS_RAW_RETURN", "FINISHED_IN", "SEMI_IN", "RAW_PROCESS_IN").contains(input.type()))
                throw fail("该单据类型不能参与生产批次");
            w = workOrder(number(batch.get("work_order_id")));
            s = step(number(batch.get("item_id")), number(batch.get("process_id")));
            requireProductionMetal(s);
            if (input.workOrderId() != null && !input.workOrderId().equals(number(batch.get("work_order_id"))))
                throw fail("生产工单和批次不一致");
        } else if (Set.of("PRODUCTION_OUT", "RAW_RETURN", "SEMI_RETURN", "PROCESS_RAW_RETURN","RAW_PROCESS_IN").contains(input.type()) || Boolean.TRUE.equals(input.settle()))
            throw fail("生产领料、回料和结算必须关联加工批次");
        if (input.workOrderId() != null && batch == null && Set.of("FINISHED_IN", "SEMI_IN", "RAW_PROCESS_IN").contains(input.type()))
            throw fail("生产产出须关联加工批次；外部入库请不选择生产工单");
        if (input.workOrderId() != null && w == null) {
            boolean closeout=workflowDocument!=null&&!rows("SELECT f.flow_id FROM metal_warehouse_order o JOIN metal_warehouse_flow f ON f.flow_id=o.warehouse_flow_id WHERE o.order_id=? AND f.closeout_id IS NOT NULL",workflowDocument).isEmpty();
            w = closeout?one("SELECT * FROM metal_work_order WHERE work_order_id=? AND del_flag='0'",input.workOrderId()):workOrder(input.workOrderId());
            if (!input.factoryId().equals(number(w.get("factory_id")))) throw fail("工单不属于所选工厂");
        }
        String no = reservedNo;
        Long workId = batch == null ? input.workOrderId() : number(batch.get("work_order_id"));
        long docId = workflowDocument==null?insert("INSERT INTO metal_warehouse_order(order_no,request_id,request_hash,request_actor,factory_id,warehouse_id,kind,type,order_date,work_order_id,batch_id,operator_id,keeper_id,recipient_id,inspector_id,reviewer_id,operator_name,keeper_name,recipient_name,inspector_name,reviewer_name,warehouse_name,reason) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", no, input.requestId(), hash, SecurityUtils.getUserId(), input.factoryId(), input.warehouseId(), t.kind(), input.type(), input.orderDate(), workId, input.batchId(), operators.isEmpty() ? null : operators.get(0), input.keeperId(), input.recipientId(), input.inspectorId(), input.reviewerId(), op, keeper, recipient, inspector, reviewer, h.get("name"), input.reason()):workflowDocument;
        if(workflowDocument!=null)jdbc.update("UPDATE metal_warehouse_order SET status='POSTED',version=version+1 WHERE order_id=?",docId);
        if(workflowDocument==null)jdbc.update("UPDATE metal_warehouse_order SET operator_ids=?,inbound_submission_id=?,outbound_submission_id=? WHERE order_id=?", JSON.toJSONString(operators.stream().map(String::valueOf).toList()), submissionId, outboundSubmissionId, docId);
        if(acidId!=null)jdbc.update("UPDATE metal_warehouse_order SET acid_wash_id=? WHERE order_id=?",acidId,docId);
        if (input.type().equals("SEMI_RETURN") && (input.reason()==null||input.reason().isBlank())) throw fail("请填写半成品还库原因");
        if (Set.of("RAW_RETURN", "SEMI_RETURN").contains(input.type())) {
            var sourceIds = input.lines().stream().map(Line::sourceLineId).filter(Objects::nonNull).toList();
            if (!sourceIds.isEmpty()) {
                String placeholders = String.join(",", Collections.nCopies(sourceIds.size(), "?"));
                Long count = jdbc.queryForObject("SELECT COUNT(DISTINCT order_id) FROM metal_warehouse_order_line WHERE line_id IN (" + placeholders + ")", Long.class, sourceIds.toArray());
                if (count != null && count > 1) throw fail("原料还库的物料明细必须来自同一张生产出库工单");
            }
        }
        int index = 0;
        for (Line line : input.lines()) {
            index++;
            postLine(input, line, t, docId, no, index, batch, s);
        }
        if (batch != null) {
            if(Boolean.TRUE.equals(step(number(batch.get("item_id")),number(batch.get("process_id"))).get("weight_stage_closed")))throw fail("本工序已结束投产，不能追加生产单据");
            semi.checkCapacity(number(batch.get("item_id")));
            if(input.type().equals("SEMI_RETURN")){
                long produced=jdbc.queryForObject("SELECT COALESCE(SUM(l.quantity),0) FROM metal_warehouse_order o JOIN metal_warehouse_order_line l ON l.order_id=o.order_id WHERE o.batch_id=? AND o.status='POSTED' AND o.type IN ('SEMI_IN','FINISHED_IN','RAW_PROCESS_IN')",Long.class,input.batchId());
                validateDownstreamOutput(jdbc,batch,produced);
            }
        }
        if (batch != null && Set.of("SEMI_IN", "FINISHED_IN", "RAW_PROCESS_IN", "RAW_RETURN", "SEMI_RETURN", "PROCESS_RAW_RETURN").contains(input.type()))
            validateBatchWeight(input.batchId());
        if (batch != null) {
            jdbc.update("UPDATE metal_process_batch SET version=version+1 WHERE batch_id=?", input.batchId());
            if (input.type().equals("PRODUCTION_OUT") && "WAITING_MATERIAL".equals(w.get("status")))
                jdbc.update("UPDATE metal_work_order SET status='IN_PROGRESS',version=version+1,update_time=NOW() WHERE work_order_id=?", workId);
            if (Boolean.TRUE.equals(input.settle())) {
                permit("metal:process-batch:settle");
                if (!Set.of("RAW_RETURN", "SEMI_RETURN", "PROCESS_RAW_RETURN", "FINISHED_IN", "SEMI_IN", "RAW_PROCESS_IN").contains(input.type()))
                    throw fail("只有回料或产出单据可结算");
                settleInternal(record("process-batch", input.batchId(), true), input.completedQuantity());
            }
        }
        return Long.toString(docId);
    }

    private void lockBatchSources(Map<String, Object> batch) {
        var s = step(number(batch.get("item_id")), number(batch.get("process_id")));
        source(s.get("material_id")==null?"FINISHED":"RAW", number(s.get(s.get("material_id")==null?"product_id":"material_id")));
        if (rows("SELECT process_id FROM metal_process WHERE process_id=? AND del_flag='0' FOR UPDATE", batch.get("process_id")).isEmpty())
            throw fail("工序已删除");
    }

    void lockPostingSources(Order input) {
        var refs = new TreeMap<String, Map.Entry<String, Long>>();
        for (Line line : input.lines()) {
            String category = line.category();
            Long reference = line.referenceId();
            if (line.inventoryId() != null) {
                var stock = record("inventory", line.inventoryId(), false);
                if (!input.factoryId().equals(number(stock.get("factory_id"))))
                    throw new ServiceException("库存不属于所选工厂", 403);
                category = str(stock.get("category"));
                reference = number(stock.get("reference_id"));
            } else if (line.sourceLineId() != null) {
                var origin = one("SELECT l.category,l.reference_id,o.factory_id FROM metal_warehouse_order_line l JOIN metal_warehouse_order o ON o.order_id=l.order_id WHERE l.line_id=?", line.sourceLineId());
                scope.check(number(origin.get("factory_id")));
                if (!input.factoryId().equals(number(origin.get("factory_id"))))
                    throw new ServiceException("来源单据不属于本厂", 403);
                category = str(origin.get("category"));
                reference = number(origin.get("reference_id"));
            }
            if (category != null && reference != null)
                refs.put((category.equals("RAW") ? "M" : "P") + String.format("%020d", reference), Map.entry(category, reference));
        }
        if (input.batchId() != null) {
            var batch = record("process-batch", input.batchId(), false);
            if (!input.factoryId().equals(number(batch.get("factory_id"))))
                throw new ServiceException("批次不属于本厂", 403);
            var step = step(number(batch.get("item_id")), number(batch.get("process_id")));
            Long product = number(step.get(step.get("material_id")==null?"product_id":"material_id"));
            refs.put((step.get("material_id")==null?"P":"M") + String.format("%020d", product), Map.entry(step.get("material_id")==null?"FINISHED":"RAW", product));
        }
        if(input.batchId()==null&&"PRODUCTION_OUT".equals(input.type())&&input.itemId()!=null&&input.processId()!=null){
            var target=step(input.itemId(),input.processId());
            if(!Objects.equals(number(target.get("work_order_id")),input.workOrderId()))throw fail("产品明细不属于所选工单");
            var work=one("SELECT factory_id FROM metal_work_order WHERE work_order_id=? AND del_flag='0'",input.workOrderId());if(!input.factoryId().equals(number(work.get("factory_id"))))throw new ServiceException("工单不属于本厂",403);
            Long product=number(target.get(target.get("material_id")==null?"product_id":"material_id"));refs.put((target.get("material_id")==null?"P":"M")+String.format("%020d",product),Map.entry(target.get("material_id")==null?"FINISHED":"RAW",product));
        }
        for (var ref : refs.values()) source(ref.getKey(), ref.getValue());
        if (input.batchId() != null) {
            var batch = record("process-batch", input.batchId(), false);
            if (rows("SELECT process_id FROM metal_process WHERE process_id=? AND del_flag='0' FOR UPDATE", batch.get("process_id")).isEmpty())
                throw fail("工序已删除");
        }
    }

    private void postLine(Order input, Line line, Type t, long docId, String no, int sequence, Map<String, Object> batch, Map<String, Object> step) {
        boolean dual=batch!=null&&WarehouseFlowRules.POLICY.equals(batch.get("completion_policy"));BigDecimal processingWeight=line.netWeight();
        if(dual&&Set.of("RAW_RETURN","SEMI_RETURN","PROCESS_RAW_RETURN","SEMI_IN","FINISHED_IN","RAW_PROCESS_IN").contains(input.type()))processingWeight=decimal(one("SELECT l.processor_weight FROM metal_warehouse_flow_line l JOIN metal_warehouse_flow_group g ON g.group_id=l.group_id WHERE g.order_id=? AND l.sequence_no=? AND g.status='CONFIRMED'",docId,sequence).get("processor_weight"));
        Map<String,Object> recoveryContext = null;
        if(ProcessRawReturnService.TYPE.equals(input.type())) {
            recoveryContext=new ProcessRawReturnService(jdbc,scope).context(input.factoryId(),input.workOrderId(),input.itemId(),input.processId(),input.batchId());
            ProcessRawReturnService.validateLine(line,(String)recoveryContext.get("customer"));
            ProcessRawReturnService.require(new BigDecimal(recoveryContext.get("remainingWeight").toString()),processingWeight);
        }
        BigDecimal weight = line.netWeight();
        long qty = line.quantity() == null ? 0 : line.quantity();
        boolean adjust = input.type().equals("ADJUST");
        if (!adjust && (weight.signum() < 0 || (!"RAW_RETURN".equals(input.type()) && weight.signum() == 0) || qty < 0 || qty > 1000000))
            throw fail("RAW_RETURN".equals(input.type()) ? "净重不能小于0，数量须为合法非负整数" : "净重须大于0，数量须为合法非负整数");
        if (line.grossWeight() != null) {
            BigDecimal impurity = line.impurity() == null ? BigDecimal.ZERO : line.impurity();
            if ((!adjust && (line.grossWeight().signum() < 0 || impurity.signum() < 0 || impurity.compareTo(line.grossWeight()) > 0)) || line.grossWeight().subtract(impurity).compareTo(weight) != 0)
                throw fail("净重须等于毛重减杂质");
        }
        Map<String,Object> acid=input.type().startsWith("ACID_")?one("SELECT a.* FROM metal_acid_wash_batch a JOIN metal_warehouse_order o ON o.acid_wash_id=a.acid_wash_id WHERE o.order_id=?",docId):null;
        Map<String, Object> stock = null, origin = null;
        String category = line.category();
        Long reference = line.referenceId();
        String batchNo = line.batchNo();
        if (line.sourceLineId() != null) {
            origin = one("SELECT l.*,o.type source_type,o.status source_status,o.factory_id source_factory,o.batch_id source_batch,o.acid_wash_id source_acid FROM metal_warehouse_order_line l JOIN metal_warehouse_order o ON o.order_id=l.order_id WHERE l.line_id=? FOR UPDATE", line.sourceLineId());
            scope.check(number(origin.get("source_factory")));
            if (!input.factoryId().equals(number(origin.get("source_factory"))) || !"POSTED".equals(origin.get("source_status")))
                throw fail("来源出库单无效");
            boolean raw = input.type().equals("RAW_RETURN");
            if (input.type().equals("SEMI_RETURN") && (!"PRODUCTION_OUT".equals(origin.get("source_type")) || !Objects.equals(input.batchId(), number(origin.get("source_batch"))) || !"SEMI".equals(origin.get("category")))) throw fail("半成品还库须关联当前批次有效半成品领料行");
            if (raw && (!"PRODUCTION_OUT".equals(origin.get("source_type")) || !Objects.equals(input.batchId(), number(origin.get("source_batch"))) || !"RAW".equals(origin.get("category"))))
                throw fail("原料还库须关联同批次原料领料行");
            if (input.type().equals("PRODUCT_RETURN") && !"PRODUCT_OUT".equals(origin.get("source_type")))
                throw fail("成品退还须关联产品出库行");
            if(input.type().equals("ACID_RETURN")&&(!"ACID_OUT".equals(origin.get("source_type"))||!Objects.equals(number(origin.get("source_acid")),number(acid.get("acid_wash_id")))))throw fail("酸洗余料须关联本批次原领料明细");
            if (!Set.of("RAW_RETURN", "SEMI_RETURN", "PRODUCT_RETURN", "ACID_RETURN").contains(input.type())) throw fail("该单据不能关联退还来源");
            var sum = one("SELECT COALESCE(SUM(l.net_weight),0) weight,COALESCE(SUM(l.quantity),0) quantity FROM metal_warehouse_order_line l JOIN metal_warehouse_order o ON o.order_id=l.order_id WHERE l.source_line_id=? AND o.status='POSTED'", line.sourceLineId());
            if (decimal(sum.get("weight")).add(weight).compareTo(decimal(origin.get("net_weight"))) > 0 || (!SemiMeasurementRules.weight(origin)&&number(sum.get("quantity")) + qty > number(origin.get("quantity"))))
                throw fail("累计退还超过原出库数量或净重");
            if(input.type().equals("SEMI_RETURN")&&!SemiMeasurementRules.weight(origin)&&!(batch!=null&&WarehouseFlowRules.POLICY.equals(batch.get("completion_policy"))))SemiFlowRules.release(number(origin.get("quantity"))-number(sum.get("quantity")),decimal(origin.get("net_weight")).subtract(decimal(sum.get("weight"))),qty,weight);
            if (raw) {
                var allowance = RawReturnAllowance.load(jdbc, input.batchId());
                BigDecimal prior=dual?decimal(one("SELECT COALESCE(SUM(COALESCE(l.processor_net_weight,l.net_weight)),0) weight FROM metal_warehouse_order_line l JOIN metal_warehouse_order o ON o.order_id=l.order_id WHERE l.source_line_id=? AND o.status='POSTED'",line.sourceLineId()).get("weight")):decimal(sum.get("weight"));
                RawReturnAllowance.require(decimal(origin.get("net_weight")).subtract(prior), allowance.remaining(), processingWeight, allowance.status());
            }
            category = str(origin.get("category"));
            reference = number(origin.get("reference_id"));
            batchNo = str(origin.get("batch_no"));
        } else if (Set.of("RAW_RETURN", "SEMI_RETURN", "PRODUCT_RETURN", "ACID_RETURN").contains(input.type())) throw fail("请选择原出库明细");
        if (t.sign() < 0 || adjust) {
            if (line.inventoryId() == null) throw fail("请选择库存记录");
            stock = record("inventory", line.inventoryId(), true);
            if (!input.factoryId().equals(number(stock.get("factory_id"))) || !input.warehouseId().equals(number(stock.get("warehouse_id"))))
                throw fail("库存不属于本厂所选库房");
            category = str(stock.get("category"));
            reference = number(stock.get("reference_id"));
            batchNo = str(stock.get("batch_no"));
        }
        boolean weightManaged="SEMI".equals(category) && SemiMeasurementRules.weight(origin!=null?origin:stock!=null?stock:batch!=null?batch:null);
        Long referenceQuantity=weightManaged?SemiMeasurementRules.reference(line.referenceQuantity()):null;
        if(weightManaged && line.quantity()!=null && line.quantity()!=0)throw fail("本半成品按重量管理，请将件数填入参考件数，不要填写有效件数");
        if(!weightManaged&&line.referenceQuantity()!=null)throw fail("当前物料按件管理，不接受参考件数");
        if(weightManaged)qty=0;
        if (stock != null && (t.sign() < 0 || adjust)) validateOwnership(stock);
        if(stock!=null&&stock.get("origin_acid_wash_id")!=null&&(t.sign()<0||adjust)&&!"SETTLED".equals(one("SELECT status FROM metal_acid_wash_batch WHERE acid_wash_id=?",stock.get("origin_acid_wash_id")).get("status")))throw fail("酸洗金属料须结算后才能使用或调整");
        if(adjust && stock!=null)semi.guardStock(number(stock.get("inventory_id")));
        if(stock!=null && "RAW".equals(category) && (t.sign()<0||adjust)) {
            var recovery=new ProcessRawReturnService(jdbc,scope);
            if(input.type().equals("PRODUCTION_OUT"))recovery.guardProduction(number(stock.get("inventory_id")),workOrder(number(batch.get("work_order_id"))));
            else {var closeouts=rows("SELECT c.customer_id FROM metal_warehouse_order o JOIN metal_warehouse_flow f ON f.flow_id=o.warehouse_flow_id JOIN metal_customer_residual_closeout c ON c.closeout_id=f.closeout_id WHERE o.order_id=?",docId);if(!closeouts.isEmpty())recovery.guardCloseout(number(stock.get("inventory_id")),number(closeouts.get(0).get("customer_id")));else recovery.guardStock(number(stock.get("inventory_id")),null,false,adjust);}
        }
        if (category == null || reference == null) throw fail("请选择物料、产品或库存来源");
        if (t.category() != null && !t.category().equals(category)) throw fail("物料分类与单据类型不匹配");
        if ("RAW".equals(category)) {
            if (qty != 0) throw fail("原材料按净重管理，数量须为0");
        } else if (!adjust && !weightManaged && qty < 1) throw fail("成品、半成品数量须大于0");
        if (t.sign() < 0 && stock != null && number(stock.get("origin_batch_id")) != null) {
            var originBatch = record("process-batch", number(stock.get("origin_batch_id")), true);
            if (!"SETTLED".equals(originBatch.get("status")))
                throw fail("产出所属加工批次尚未结算或已冲销，不能出库，请先完成批次结算");
        }
        if (input.type().equals("PRODUCTION_OUT")) {
            if (!Set.of("RAW", "SEMI").contains(category)) throw fail("生产领料仅支持原料或前工序半成品");
            ProcessingInputService.require(jdbc,stock,batch);
            ProcessingInputService.capacity(jdbc,stock,batch,weight,null);
            if(ProcessingPolicy.v2(batch))stock=ProcessingInputService.stock(jdbc,stock);
            if ("SEMI".equals(category)) semi.validateSource(stock,batch,line.releaseId(),line.skipPermitId(),line.skipPermitVersion());
            else if(line.releaseId()!=null||line.skipPermitId()!=null||line.skipPermitVersion()!=null) throw fail("原料不能关联释放记录");
        } else if(stock!=null && t.sign()<0) semi.guardStock(number(stock.get("inventory_id")));

        if (batch != null && Set.of("FINISHED_IN", "SEMI_IN", "RAW_PROCESS_IN").contains(input.type())) {
            if(!"RAW_PROCESS_IN".equals(input.type())&&!reference.equals(number(step.get("product_id")))) throw fail("产出产品必须属于批次产品明细");
            long last = jdbc.queryForObject("SELECT MAX(sequence_no) FROM metal_work_order_step WHERE item_id=?", Long.class, batch.get("item_id"));
            ProcessingPolicy.requireOutput(input.type(),step,number(batch.get("sequence_no")),last);
            if (batchNo != null && !batchNo.isBlank()) throw fail("生产批次号由系统生成");
            long registered = jdbc.queryForObject("SELECT COALESCE(SUM(l.quantity),0) FROM metal_warehouse_order o JOIN metal_warehouse_order_line l ON l.order_id=o.order_id WHERE o.batch_id=? AND o.status='POSTED' AND o.type IN ('SEMI_IN','FINISHED_IN','RAW_PROCESS_IN')", Long.class, input.batchId());
            if(!weightManaged&&!"RAW".equals(category))validateOutputQuantity(number(batch.get("planned_quantity")), registered, qty);validateDownstreamOutput(jdbc,batch, registered + qty);
            batchNo = str(batch.get("batch_no"));
        }
        Long customerId = null;
        String materialOwner=MaterialOwnership.of(origin!=null?origin:stock!=null?stock:Map.of());
        String customerNameSnapshot=null,companySnapshot=null;
        String organization = normalizeOrganization(line.organization());
        if (batch != null && Set.of("FINISHED_IN", "SEMI_IN", "RAW_PROCESS_IN").contains(input.type()))
            organization = str(workOrder(number(batch.get("work_order_id"))).get("customer"));
        else if (origin != null) organization = normalizeOrganization(str(origin.get("organization")));
        else if (stock != null) organization = normalizeOrganization(str(stock.get("organization")));

        if(recoveryContext!=null){organization=(String)recoveryContext.get("customer");batchNo="REC-"+no+"-"+sequence;}
        if("RAW".equals(category)){
            if(Set.of("RAW_IN","OPENING").contains(input.type())){var customer=new RawInventoryCustomers(jdbc).selected(input.factoryId(),line,true);customerId=line.customerId();organization=str(customer.get("company_name"));materialOwner=str(customer.get("material_ownership"));}
            else if("RAW_PROCESS_IN".equals(input.type())){var ownerWork=workOrder(number(batch.get("work_order_id")));customerId=number(ownerWork.get("customer_id"));materialOwner="DIRECT".equals(ownerWork.get("processing_type"))?"FACTORY":"CUSTOMER";RawInventoryCustomers.inherited(line,customerId);organization=customerId==null?"工厂自有":str(one("SELECT company_name FROM metal_customer WHERE customer_id=?",customerId).get("company_name"));}
            else {customerId=acid!=null?number(acid.get("customer_id")):number((origin!=null?origin:stock!=null?stock:Map.<String,Object>of()).get("customer_id"));if(acid!=null)materialOwner=MaterialOwnership.of(acid);RawInventoryCustomers.inherited(line,customerId);if(customerId!=null)organization=str(one("SELECT company_name FROM metal_customer WHERE customer_id=?",customerId).get("company_name"));}
            if("FACTORY".equals(materialOwner))organization="工厂自有";
            if(!Set.of("RAW_IN","OPENING").contains(input.type())&&recoveryContext==null&&line.materialOwnership()!=null&&!line.materialOwnership().equals(materialOwner))throw fail("材料归属必须继承来源");
        }else if(line.customerId()!=null||line.customerVersion()!=null)throw fail("仅原材料入库可选择客户档案");
        if(batch!=null&&!"RAW".equals(category)) {
            var ownerWork=workOrder(number(batch.get("work_order_id")));customerId=number(ownerWork.get("customer_id"));
            var customerSnapshot=JSON.parseObject(Objects.toString(ownerWork.get("customer_snapshot"),"{}"));
            customerNameSnapshot=customerSnapshot==null?null:customerSnapshot.getString("name");
            companySnapshot=customerSnapshot==null?null:customerSnapshot.getString("companyName");
            materialOwner=customerId==null?"UNVERIFIED":"CUSTOMER";
            if(WarehouseFlowRules.POLICY.equals(batch.get("completion_policy"))&&companySnapshot!=null)organization=companySnapshot;
        } else if(origin!=null||stock!=null){var inherited=origin!=null?origin:stock; if(customerId==null)customerId=number(inherited.get("customer_id"));}
        if(recoveryContext!=null&&batch!=null&&WarehouseFlowRules.POLICY.equals(batch.get("completion_policy"))){
            var ownerWork=workOrder(number(batch.get("work_order_id")));
            if(line.materialOwnership()==null)throw fail("加工回收余料须人工明确客户料或工厂料归属");
            materialOwner=WarehouseFlowRules.newOwner(line.materialOwnership(),"FACTORY".equals(line.materialOwnership())?line.customerId():number(ownerWork.get("customer_id")));
            customerId="FACTORY".equals(materialOwner)?null:number(ownerWork.get("customer_id"));
            RawInventoryCustomers.inherited(line,customerId);
            organization=customerId==null?"工厂自有":str(one("SELECT company_name FROM metal_customer WHERE customer_id=?",customerId).get("company_name"));
        }
        var source = source(category, reference);
        if(origin!=null&&!"RAW".equals(category)){
            var inheritedStock=one("SELECT * FROM metal_inventory WHERE inventory_id=?",origin.get("inventory_id"));source=new LinkedHashMap<>(source);
            for(String field:List.of("code","name","specification","source_process_id","source_process_name"))source.put(field,inheritedStock.get(field));
            source.put("product_type",inheritedStock.get("product_type_snapshot"));source.put("metal_type_name",inheritedStock.get("metal_type_name_snapshot"));source.put("metal_type_id",inheritedStock.get("metal_type_id_snapshot"));customerNameSnapshot=str(inheritedStock.get("customer_name_snapshot"));companySnapshot=str(inheritedStock.get("customer_company_snapshot"));
        }
        if ("RAW".equals(category) && Set.of("PRODUCTION_OUT", "PROCESS_RAW_RETURN", "ACID_IN","RAW_PROCESS_IN").contains(input.type())){
            if(batch!=null&&ProcessingPolicy.v2(batch)){if(number(source.get("metal_type_id"))!=ProcessingPolicy.metalId(JSON.parseObject(str(step.get("product_snapshot")))))throw fail("原料与工单金属不一致，不能跨金属投入或产出");}
            else MaterialTypes.requireProduction(number(source.get("metal_type_id")));
        }
        if(batch!=null && !"RAW".equals(category)&&ProcessingPolicy.primaryOutput(input.type())){source=new LinkedHashMap<>(source);var snapshot=JSON.parseObject(str(step.get("product_snapshot")));source.put("code",snapshot.getString("code"));source.put("name",snapshot.getString("name"));source.put("specification",snapshot.getString("specification"));source.put("product_type",snapshot.getString("productType"));source.put("metal_type_name",snapshot.getString("metalTypeName"));source.put("metal_type_id",snapshot.get("metalTypeId"));}
        if (batchNo == null || batchNo.isBlank())
            batchNo = "RAW".equals(category) ? "" : input.orderDate().toString().replace("-", "") + "-" + no + "-" + sequence;
        var provenance=ProcessingOutputOrigin.determine(jdbc,input.type(),category,line,source,origin!=null?origin:stock,batch);
        String rawKind=provenance.rawKind();
        if(input.type().equals("ACID_OUT"))RawMaterialKinds.unprocessed(rawKind);
        if(input.type().equals("ACID_IN")){batchNo=str(acid.get("batch_no"));if("粉料".equals(source.get("material_type")))throw fail("酸洗金属料不能使用粉料物料编码");}
        Long expectedOrigin = batch != null && (!"RAW".equals(category)||"RAW_PROCESS_IN".equals(input.type())) ? input.batchId() : null;
        if (origin != null && !"RAW".equals(category))
            expectedOrigin = number(one("SELECT origin_batch_id FROM metal_inventory WHERE inventory_id=?", origin.get("inventory_id")).get("origin_batch_id"));
        if (origin != null && ("RAW".equals(category) || input.type().equals("SEMI_RETURN"))) {
            stock = record("inventory", number(origin.get("inventory_id")), true);
            if (!input.warehouseId().equals(number(stock.get("warehouse_id"))))
                throw fail("原料还库须退回原出库库存所在仓库");
            if (!"0".equals(stock.get("del_flag"))) throw fail("原出库库存已归档，请先核对来源");
        }
        if (stock == null) {
            var existing = "RAW".equals(category) ? rows("SELECT *,inventory_id id FROM metal_inventory WHERE factory_id=? AND warehouse_id=? AND category=? AND reference_id=? AND batch_no=? AND raw_customer_key=? AND raw_kind_key=? AND raw_origin_key=? AND ownership_status='NORMAL' FOR UPDATE", input.factoryId(), input.warehouseId(), category, reference, batchNo, MaterialOwnership.rawKey(materialOwner,input.factoryId(),customerId,organization),rawKind,provenance.rawKey(expectedOrigin,input.type().equals("ACID_IN")?number(acid.get("acid_wash_id")):null)) : rows("SELECT *,inventory_id id FROM metal_inventory WHERE factory_id=? AND warehouse_id=? AND category=? AND reference_id=? AND batch_no=? FOR UPDATE", input.factoryId(), input.warehouseId(), category, reference, batchNo);
            if(recoveryContext!=null&&!existing.isEmpty())throw fail("加工余料库存批号冲突，请重新创建单据；不能合并已有库存");
            if (existing.isEmpty()) {
                Long originBatch = expectedOrigin;
                long stockId = insert("INSERT INTO metal_inventory(factory_id,warehouse_id,category,reference_id,batch_no,code,name,specification,purity,organization,origin_batch_id,customer_id,raw_material_kind,origin_acid_wash_id,material_ownership,powder_origin,washing_status,smelting_status,origin_policy_version,origin_verification_id) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", input.factoryId(), input.warehouseId(), category, reference, batchNo, source.get("code"), source.get("name"), source.get("specification"), line.purity(), organization, originBatch, customerId,rawKind,input.type().equals("ACID_IN")?number(acid.get("acid_wash_id")):null,materialOwner,provenance.powder(),provenance.washing(),provenance.smelting(),provenance.policy(),provenance.verification());
                jdbc.update("UPDATE metal_inventory SET customer_name_snapshot=?,customer_company_snapshot=?,product_type_snapshot=?,metal_type_name_snapshot=?,metal_type_id_snapshot=?,source_process_id=?,source_process_name=? WHERE inventory_id=?",customerNameSnapshot,"RAW".equals(category)?organization:companySnapshot,source.get("product_type"),source.get("metal_type_name"),source.get("metal_type_id"),batch==null?source.get("source_process_id"):batch.get("process_id"),step==null?source.get("source_process_name"):step.get("process_name"),stockId);
                stock = record("inventory", stockId, true);
            } else {
                stock = existing.get(0);
                if("RAW".equals(category))new ProcessRawReturnService(jdbc,scope).guardAppend(number(stock.get("inventory_id")));
                validateProductStockIdentity(category, str(stock.get("organization")), organization, number(stock.get("origin_batch_id")), expectedOrigin);
                if (!"0".equals(stock.get("del_flag"))) {
                    if (decimal(stock.get("net_weight")).signum() != 0 || number(stock.get("quantity")) != 0 || !rows("SELECT l.line_id FROM metal_warehouse_order_line l JOIN metal_warehouse_order o ON o.order_id=l.order_id WHERE l.inventory_id=? AND (o.type NOT IN ('OPENING','ADJUST','RAW_IN','FINISHED_IN','SEMI_IN','RAW_PROCESS_IN') OR o.batch_id IS NOT NULL) LIMIT 1", stock.get("inventory_id")).isEmpty())
                        throw fail("归档库存存在余额或业务引用，不能恢复");
                    jdbc.update("UPDATE metal_inventory SET del_flag='0',version=version+1,update_time=NOW() WHERE inventory_id=? AND del_flag='2'", stock.get("inventory_id"));
                    stock.put("del_flag", "0");
                }
            }
        }
        if(weightManaged&&!SemiMeasurementRules.weight(stock)){
            if(decimal(stock.get("net_weight")).signum()!=0)throw fail("库存计量方式与工单快照不一致");
            jdbc.update("UPDATE metal_inventory SET measurement_mode='WEIGHT' WHERE inventory_id=?",stock.get("inventory_id"));stock.put("measurement_mode","WEIGHT");
        }
        Long stockId = number(stock.get("inventory_id"));
        long lineId = insert("INSERT INTO metal_warehouse_order_line(order_id,inventory_id,source_line_id,sequence_no,category,reference_id,code,name,specification,batch_no,net_weight,quantity,gross_weight,impurity,purity,organization,purpose,remark,receiving_organization) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", docId, stockId, line.sourceLineId(), sequence, category, reference, stock.get("code"), stock.get("name"), stock.get("specification"), batchNo, weight, qty, line.grossWeight(), line.impurity(), line.purity(), organization, line.purpose(), line.remark(), line.receivingOrganization());
        jdbc.update("UPDATE metal_warehouse_order_line SET material_ownership=?,powder_origin=?,washing_status=?,smelting_status=?,origin_policy_version=?,origin_verification_id=? WHERE line_id=?",materialOwner,provenance.powder(),provenance.washing(),provenance.smelting(),provenance.policy(),provenance.verification(),lineId);
        if("PRODUCTION_OUT".equals(input.type()))ProcessingInputService.qualification(jdbc,lineId,stock,batch);
        var flowMeasurements=rows("SELECT f.*,g.processor_id,g.confirmed_by,g.confirmed_at FROM metal_warehouse_flow_line f JOIN metal_warehouse_flow_group g ON g.group_id=f.group_id WHERE g.order_id=? AND f.sequence_no=?",docId,sequence);
        if(!flowMeasurements.isEmpty()){
            var measured=flowMeasurements.get(0);
            jdbc.update("UPDATE metal_warehouse_order_line SET planned_net_weight=?,processor_net_weight=?,weighing_difference_reason=?,processor_actor_id=?,confirmed_by=?,confirmed_at=? WHERE line_id=?",measured.get("planned_weight"),measured.get("processor_weight"),measured.get("difference_reason"),measured.get("processor_id"),measured.get("confirmed_by"),measured.get("confirmed_at"),lineId);
            jdbc.update("UPDATE metal_warehouse_flow_line SET posted_line_id=? WHERE flow_line_id=?",lineId,measured.get("flow_line_id"));
            if("PRODUCTION_OUT".equals(input.type())&&"RAW".equals(category))jdbc.update("UPDATE metal_warehouse_order_line SET production_supply_kind=? WHERE line_id=?",MaterialOwnership.production(stock,workOrder(number(batch.get("work_order_id")))),lineId);
        }
        if(rawKind!=null)jdbc.update("UPDATE metal_warehouse_order_line SET raw_material_kind=? WHERE line_id=?",rawKind,lineId);
        if(customerId!=null)jdbc.update("UPDATE metal_warehouse_order_line SET customer_id=? WHERE line_id=?",customerId,lineId);
        if(weightManaged)jdbc.update("UPDATE metal_warehouse_order_line SET measurement_mode='WEIGHT',reference_quantity=? WHERE line_id=?",referenceQuantity,lineId);
        if(recoveryContext!=null)new ProcessRawReturnService(jdbc,scope).register(lineId,stockId,input.batchId(),recoveryContext);
        if(input.type().equals("PRODUCTION_OUT")&&"SEMI".equals(category))new SemiSourcePolicyService(jdbc,scope).bind(stock,batch,line.skipPermitId(),line.skipPermitVersion(),lineId);
        if (input.type().equals("PRODUCTION_OUT") && line.releaseId()!=null) semi.reserveUse(line.releaseId(),lineId,stock,batch,qty,weight);
        balance(stock, weight.multiply(BigDecimal.valueOf(t.sign())), qty * t.sign(), docId, lineId, null);
    }

    static void validateProductStockIdentity(String category, String existing, String incoming, Long origin, Long expectedOrigin) {
        validateCustomerIdentity(category, existing, incoming);
        if (!"RAW".equals(category) && !Objects.equals(origin, expectedOrigin))
            throw fail("相同批次库存的生产来源不一致，请使用其他批次号或核对退还来源");
    }

    static void validateCustomerIdentity(String category, String existing, String incoming) {
        if (!"RAW".equals(category) && !normalizeOrganization(existing).equals(normalizeOrganization(incoming)))
            throw fail("同一批次库存的所属客户不同，不能合并；请核对客户或更换批次号");
    }

    static String normalizeOrganization(String value) {
        return value == null ? "" : value.trim();
    }

    static void validateOwnership(Map<String, Object> stock) {
        if ("RAW".equals(stock.get("category")) && "REVIEW".equals(stock.get("ownership_status")))
            throw fail("该库存来源待核对，暂不能新增出库或调整，请先核实各单位余额");
    }

    private void validateBatchWeight(Long batchId) {
        var totals = one("SELECT COALESCE(SUM(CASE WHEN o.type='PRODUCTION_OUT' THEN COALESCE(l.processor_net_weight,l.net_weight) ELSE 0 END),0) issued,COALESCE(SUM(CASE WHEN o.type IN ('RAW_RETURN','SEMI_RETURN','PROCESS_RAW_RETURN','SEMI_IN','FINISHED_IN','RAW_PROCESS_IN') THEN COALESCE(l.processor_net_weight,l.net_weight) ELSE 0 END),0) returned FROM metal_warehouse_order o JOIN metal_warehouse_order_line l ON l.order_id=o.order_id WHERE o.batch_id=? AND o.status='POSTED'", batchId);
        validateProductionWeight(decimal(totals.get("issued")), decimal(totals.get("returned")));
    }

    static void validateProductionWeight(BigDecimal issued, BigDecimal returned) {
        if (issued.signum() <= 0) throw fail("本批次尚未领料，不能登记产出或还料");
        if (returned.compareTo(issued) > 0) throw fail("本批次累计产出净重与还料净重之和不能超过累计领料净重");
    }

    /**
     * 后续工序只按有效上游半成品领料计件，补充原料不产生可完成件数。
     */
    static void validateDownstreamQuantity(long sequence, long issued, long output) {
        if (sequence > 1 && (issued <= 0 || output > issued))
            throw fail("后续工序须先领用上一工序已结算半成品，累计产出及完成件数不能超过实际领用件数；历史异常请先人工核对");
    }

    static long eligibleSemiQuantity(JdbcTemplate jdbc, Map<String, Object> batch) {
        return SemiFlowService.netIssued(jdbc, number(batch.get("batch_id")));
    }

    static void validateDownstreamOutput(JdbcTemplate jdbc, Map<String, Object> batch, long output) {
        if(ProcessingPolicy.v2(batch)){
            if(!"POST_PROCESS".equals(batch.get("process_role")))return;
            if(WeightStageProgressService.semiInput(jdbc,number(batch.get("batch_id"))).signum()<=0)throw fail("后加工须实际领用已核对融定资格的半成品");
            if(MeasurementPolicy.current(jdbc,batch)){
                validateDownstreamQuantity(Math.max(2,number(batch.get("sequence_no"))),eligibleSemiQuantity(jdbc,batch),output);
                return;
            }
        }
        else SemiMeasurementRules.requireIntermediateInput(number(batch.get("sequence_no")),WeightStageProgressService.semiInput(jdbc,number(batch.get("batch_id"))));
        if (number(batch.get("sequence_no")) > 1 && !SemiMeasurementRules.weight(batch) && !WeightStageProgressService.hasWeightInput(jdbc,number(batch.get("batch_id"))))
            validateDownstreamQuantity(number(batch.get("sequence_no")), eligibleSemiQuantity(jdbc, batch), output);
    }

    static void validateOutputQuantity(long planned, long registered, long incoming) {
        if (incoming < 1 || registered > planned - incoming) throw fail("累计产出数量不能超过批次计划数量");
    }

    private void balance(Map<String, Object> stock, BigDecimal delta, long qty, long doc, long line, Long reversal) {
        BusinessDecimal.requireCompatibleBalance(decimal(stock.get("net_weight")),"库存#"+stock.get("inventory_id"));
        BusinessDecimal.requireCompatibleBalance(delta,"流水重量");
        WarehouseReservations.balance(jdbc,stock,delta,qty);
        BigDecimal result = decimal(stock.get("net_weight")).add(delta).setScale(2);
        long quantity = number(stock.get("quantity")) + qty;
        if (result.signum() < 0 || quantity < 0) throw fail("库存不足，不能过账或冲销");
        if (!"RAW".equals(stock.get("category")) && !SemiMeasurementRules.weight(stock) && ((quantity == 0) != (result.signum() == 0)))
            throw fail("成品或半成品的数量与净重须同时归零或同时保留");
        Long refBefore=number(stock.get("reference_quantity")), refAfter=refBefore;
        boolean completeBefore=!Boolean.FALSE.equals(stock.get("reference_complete")), completeAfter=completeBefore;
        if(SemiMeasurementRules.weight(stock)){
            var metadata=one("SELECT reference_quantity FROM metal_warehouse_order_line WHERE line_id=?",line);
            if(reversal!=null){var prior=one("SELECT reference_before,reference_after,reference_complete_before,reference_complete_after,balance_weight FROM metal_stock_ledger WHERE ledger_id=?",reversal);long latest=jdbc.queryForObject("SELECT COALESCE(MAX(original.ledger_id),0) FROM metal_stock_ledger original WHERE original.inventory_id=? AND original.reversal_of IS NULL AND NOT EXISTS(SELECT 1 FROM metal_stock_ledger undone WHERE undone.reversal_of=original.ledger_id)",Long.class,stock.get("inventory_id"));if(latest==reversal && jdbc.queryForObject("SELECT COUNT(*) FROM metal_stock_ledger disturbed WHERE disturbed.inventory_id=? AND disturbed.ledger_id>? AND disturbed.reversal_of<?",Long.class,stock.get("inventory_id"),reversal,reversal)==0 && decimal(stock.get("net_weight")).compareTo(decimal(prior.get("balance_weight")))==0 && Objects.equals(refBefore,number(prior.get("reference_after"))) && completeBefore==!Boolean.FALSE.equals(prior.get("reference_complete_after"))){refAfter=number(prior.get("reference_before"));completeAfter=!Boolean.FALSE.equals(prior.get("reference_complete_before"));}else{refAfter=null;completeAfter=false;}}
            else if(result.signum()==0){refAfter=null;completeAfter=true;}
            else if("ADJUST".equals(one("SELECT type FROM metal_warehouse_order WHERE order_id=?",doc).get("type"))){if(metadata.get("reference_quantity")!=null){refAfter=number(metadata.get("reference_quantity"));completeAfter=true;}}
            else if(decimal(stock.get("net_weight")).signum()==0&&delta.signum()>0){refAfter=number(metadata.get("reference_quantity"));completeAfter=refAfter!=null;}
            else{refAfter=SemiMeasurementRules.referenceBalance(refBefore,number(metadata.get("reference_quantity")),delta.signum()<0,false);completeAfter=completeBefore&&refAfter!=null;}
            jdbc.update("UPDATE metal_inventory SET reference_quantity=?,reference_complete=? WHERE inventory_id=?",refAfter,completeAfter,stock.get("inventory_id"));
        }
        jdbc.update("UPDATE metal_inventory SET net_weight=?,quantity=?,version=version+1,update_time=NOW() WHERE inventory_id=?", result, quantity, stock.get("inventory_id"));
        insert("INSERT INTO metal_stock_ledger(inventory_id,order_id,line_id,delta_weight,delta_quantity,balance_weight,balance_quantity,reversal_of) VALUES(?,?,?,?,?,?,?,?)", stock.get("inventory_id"), doc, line, delta, qty, result, quantity, reversal);
        if(SemiMeasurementRules.weight(stock))jdbc.update("UPDATE metal_stock_ledger SET reference_before=?,reference_after=?,reference_complete_before=?,reference_complete_after=? WHERE ledger_id=LAST_INSERT_ID()",refBefore,refAfter,completeBefore,completeAfter);
        WarehouseResiduals.apply(jdbc,stock,delta,doc,line,reversal);
        stock.put("reference_quantity",refAfter);stock.put("reference_complete",completeAfter);
        stock.put("net_weight", result);
        stock.put("quantity", quantity);
    }

    /**
     * 按版本号修改库存基础信息，并限制已有单据引用的记录。
     */
    public void editStock(Stock input) {
        var stock = lock("inventory", input.id());
        version(stock, input.version());
        validateOwnership(stock);
        if(stock.get("customer_id")!=null&&!normalizeOrganization(str(stock.get("organization"))).equals(normalizeOrganization(input.organization())))throw fail("已关联客户的原料不能修改来源单位");
        if ("RAW".equals(stock.get("category")) && !normalizeOrganization(str(stock.get("organization"))).equals(normalizeOrganization(input.organization())) && !rows("SELECT line_id FROM metal_warehouse_order_line WHERE inventory_id=? LIMIT 1", input.id()).isEmpty())
            throw fail("已有入库记录的原料不能修改来源单位");
        if (!rows("SELECT l.line_id FROM metal_warehouse_order_line l JOIN metal_warehouse_order o ON o.order_id=l.order_id WHERE l.inventory_id=? AND (o.type NOT IN ('OPENING','ADJUST','RAW_IN','FINISHED_IN','SEMI_IN','RAW_PROCESS_IN') OR o.batch_id IS NOT NULL) LIMIT 1", input.id()).isEmpty())
            throw fail("库存已被业务引用，资料快照不能修改");
        jdbc.update("UPDATE metal_inventory SET specification=?,organization=?,purity=?,version=version+1,update_time=NOW() WHERE inventory_id=?", input.specification(), normalizeOrganization(input.organization()), BusinessDecimal.changed(input.purity(),stock.get("purity")==null?null:decimal(stock.get("purity"))), input.id());
    }

    /**
     * 校验库存调整权限，并按请求标识生成调整单据。
     */
    static void validateAdjustmentComponents(String category, BigDecimal net, long quantity, BigDecimal gross, BigDecimal impurity) {
        validateAdjustmentComponents(category, net, quantity, gross, impurity, false);
    }

    static void validateAdjustmentComponents(String category, BigDecimal net, long quantity, BigDecimal gross, BigDecimal impurity, boolean referenceProvided) {
        if ((gross == null) != (impurity == null)) throw fail("毛重差额和杂质差额须同时填写");
        if (gross != null) {
            if (!"RAW".equals(category)) throw fail("仅原材料支持毛重和杂质差额");
            if (gross.subtract(impurity).compareTo(net) != 0) throw fail("净重差额须等于毛重差额减杂质差额");
        }
        if (!referenceProvided && net.signum() == 0 && quantity == 0 && (gross == null || gross.signum() == 0) && (impurity == null || impurity.signum() == 0))
            throw fail("调整差额不能全部为0");
    }

    public String adjustStock(Long id, Adjustment input) {
        permit("metal:inventory:adjust");
        var identity=one("SELECT factory_id FROM metal_inventory WHERE inventory_id=?",id);
        Long replayFactory=number(identity.get("factory_id"));scope.check(replayFactory);scope.lockFactories(List.of(replayFactory));
        uuid(input.requestId());
        String hash = fingerprint(input.toString() + ":" + id);
        String prior = creation("inventory-adjust", input.requestId(), hash);
        if (prior != null) return prior;
        var stock=record("inventory",id,true);
        semi.guardStock(id);
        input=WarehousePrecision.adjustment(input);
        if("RAW".equals(stock.get("category"))&&input.deltaWeight().signum()==0)throw fail("原材料净重差额不能为0");
        if(SemiMeasurementRules.weight(stock)){if(input.deltaQuantity()!=0)throw fail("重量型库存不能调整有效件数，请使用参考件数");SemiMeasurementRules.reference(input.referenceQuantity());}
        else if(input.referenceQuantity()!=null)throw fail("按件库存不接受参考件数");
        validateAdjustmentComponents(str(stock.get("category")), input.deltaWeight(), input.deltaQuantity(), input.deltaGrossWeight(), input.deltaImpurity(), input.referenceQuantity()!=null);
        if ("RAW".equals(stock.get("category")) && input.deltaQuantity() != 0) throw fail("原料不能调整件数");
        Long factory = number(stock.get("factory_id"));
        Line line = new Line(id, null, str(stock.get("category")), number(stock.get("reference_id")), str(stock.get("batch_no")), input.deltaWeight(), input.deltaQuantity(), input.deltaGrossWeight(), input.deltaImpurity(), null, null, null, input.reason(),null,null,null,null,input.referenceQuantity());
        Order order = new Order(input.requestId(), factory, number(stock.get("warehouse_id")), "ADJUST", LocalDate.now(ZoneId.of("Asia/Shanghai")), input.operatorId(), null, input.keeperId(), null, null, null, null, null, null, false, null, input.reason(), List.of(line), null, null);
        String previous = duplicate("metal_warehouse_order", "order_id", order.requestId(), fingerprint(order));
        if (previous != null) return previous;
        version(stock, input.version());
        String saved = postInternal(order, true);
        remember("inventory-adjust", input.requestId(), hash, saved);
        return saved;
    }

    /**
     * 校验加工批次状态和数量后结算，生成报工及损耗统计。
     */
    public void settle(Long id, Action input) {
        var initial = record("process-batch", id, false);
        scope.lockFactories(List.of(number(initial.get("factory_id"))));
        lockBatchSources(initial);
        var batch = lock("process-batch", id);
        version(batch, input.version());
        WarehouseReservations.batch(jdbc,id,0);
        if(WarehouseFlowRules.POLICY.equals(batch.get("completion_policy")))throw fail("请由库管验收整批加工交接后结算");
        if (BatchCompletionRules.POLICY.equals(batch.get("completion_policy"))) throw fail("请使用全部还料或无余料确认完成批次结算");
        settleInternal(batch, input.completedQuantity());
    }

    void settleConfirmed(Long batchId, int completed) {
        permit("metal:process-batch:settle");
        var batch=record("process-batch",batchId,true);
        if (!BatchCompletionRules.POLICY.equals(batch.get("completion_policy"))) throw fail("历史批次请使用原结算流程");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM metal_batch_return_confirmation WHERE batch_id=? AND status='ACTIVE'",Long.class,batchId)!=1L) throw fail("请先确认全部还料或无余料");
        settleInternal(batch,completed);
    }

    private void settleInternal(Map<String, Object> batch, Integer completed) {
        if (!"OPEN".equals(batch.get("status"))) throw fail("批次已结算或已冲销");
        boolean byWeight=SemiMeasurementRules.weight(batch);
        if(byWeight)completed=0;
        if (!byWeight && (completed == null || completed < 1 || completed > number(batch.get("planned_quantity"))))
            throw fail("完成数量须为1至批次加工数量");
        Long batchId = number(batch.get("batch_id"));
        var totals = one("SELECT COALESCE(SUM(CASE WHEN o.type='PRODUCTION_OUT' THEN COALESCE(l.processor_net_weight,l.net_weight) ELSE 0 END),0) issued,COALESCE(SUM(CASE WHEN o.type IN ('RAW_RETURN','SEMI_RETURN','PROCESS_RAW_RETURN','SEMI_IN','FINISHED_IN','RAW_PROCESS_IN') THEN COALESCE(l.processor_net_weight,l.net_weight) ELSE 0 END),0) returned,COALESCE(SUM(CASE WHEN o.type IN ('SEMI_IN','FINISHED_IN','RAW_PROCESS_IN') THEN l.quantity ELSE 0 END),0) output_qty FROM metal_warehouse_order o JOIN metal_warehouse_order_line l ON l.order_id=o.order_id WHERE o.batch_id=? AND o.status='POSTED'", batchId);
        BusinessDecimal.requireCompatibleBalance(decimal(totals.get("issued")),"批次#"+batchId+"历史领料合计");
        BusinessDecimal.requireCompatibleBalance(decimal(totals.get("returned")),"批次#"+batchId+"历史产出及还料合计");
        if(byWeight){validateDownstreamOutput(jdbc,batch,0);long output=jdbc.queryForObject("SELECT COUNT(*) FROM metal_warehouse_order o JOIN metal_warehouse_order_line l ON l.order_id=o.order_id WHERE o.batch_id=? AND o.status='POSTED' AND o.type IN ('SEMI_IN','FINISHED_IN','RAW_PROCESS_IN') AND l.net_weight>0",Long.class,batchId);if(output==0)throw fail("请先登记真实的工序产出净重");validateBatchWeight(batchId);}
        else validateDownstreamOutput(jdbc,batch, completed.longValue());
        if (number(totals.get("output_qty")) != completed.longValue())
            throw fail("半成品或成品产出数量合计必须等于实际完成数量");
        var s = step(number(batch.get("item_id")), number(batch.get("process_id")));
        s=new LinkedHashMap<>(s);s.put("theoretical_loss","PERCENT".equals(s.get("loss_unit"))?BusinessDecimal.round(decimal(s.get("theoretical_loss"))):decimal(s.get("theoretical_loss")));
        Map<String, Object> rule = null;
        BigDecimal tolerance = BigDecimal.ZERO;
        boolean percentage="PERCENT".equals(s.get("loss_unit"));
        var outputWeight=one("SELECT COALESCE(SUM(COALESCE(l.processor_net_weight,l.net_weight)),0) weight FROM metal_warehouse_order o JOIN metal_warehouse_order_line l ON l.order_id=o.order_id WHERE o.batch_id=? AND o.status='POSTED' AND o.type IN ('SEMI_IN','FINISHED_IN','RAW_PROCESS_IN')",batchId);
        BigDecimal output=decimal(outputWeight.get("weight")),returns=decimal(totals.get("returned")).subtract(output);
        LossCalculator.PercentageResult rate=percentage?LossCalculator.percentage(decimal(totals.get("issued")),returns,output,decimal(s.get("theoretical_loss"))):null;
        var calculation = percentage?new LossCalculator.Result(rate.loss(),byWeight?BigDecimal.ZERO:rate.loss().divide(BigDecimal.valueOf(completed),2,java.math.RoundingMode.HALF_UP),rate.threshold(),rate.abnormal()):byWeight ? new LossCalculator.Result(decimal(totals.get("issued")).subtract(decimal(totals.get("returned"))),BigDecimal.ZERO,decimal(s.get("theoretical_loss")),false) : LossCalculator.calculate(decimal(totals.get("issued")), decimal(totals.get("returned")), completed, decimal(s.get("theoretical_loss")), tolerance);
        var materials = rows("SELECT l.code,l.name,SUM(COALESCE(l.processor_net_weight,l.net_weight)) weight FROM metal_warehouse_order o JOIN metal_warehouse_order_line l ON l.order_id=o.order_id WHERE o.batch_id=? AND o.status='POSTED' AND o.type='PRODUCTION_OUT' GROUP BY l.category,l.reference_id,l.code,l.name ORDER BY l.category,l.reference_id", batchId);
        for(var material:materials)BusinessDecimal.requireCompatibleBalance(decimal(material.get("weight")),"批次#"+batchId+"领料类别资料("+material.get("code")+")");
        var allocated = LossCalculator.allocate(calculation.loss(), materials.stream().map(r -> decimal(r.get("weight"))).toList());
        long report = insert("INSERT INTO metal_work_report(batch_id,factory_id,item_id,process_id,completed_quantity,issued_weight,returned_weight,loss_weight,average_loss,theoretical_loss,tolerance,threshold_value,rule_id,rule_version,status) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", batchId, batch.get("factory_id"), batch.get("item_id"), batch.get("process_id"), completed, totals.get("issued"), totals.get("returned"), calculation.loss(), calculation.average(), s.get("theoretical_loss"), tolerance, calculation.threshold(), rule == null ? null : rule.get("rule_id"), rule == null ? null : rule.get("version"), calculation.abnormal() ? "ABNORMAL" : "NORMAL");
        jdbc.update("UPDATE metal_work_report SET numeric_policy_version=2,loss_unit=?,net_input_weight=?,loss_rate_percent=? WHERE report_id=?",percentage?"PERCENT":"G_PER_PIECE",percentage?decimal(totals.get("issued")).subtract(returns):null,rate==null?null:rate.rate(),report);
        if(WarehouseFlowRules.POLICY.equals(batch.get("completion_policy"))){
            var received=one("SELECT COALESCE(SUM(l.net_weight),0) weight FROM metal_warehouse_order o JOIN metal_warehouse_order_line l ON l.order_id=o.order_id WHERE o.batch_id=? AND o.status='POSTED' AND o.type IN ('SEMI_IN','FINISHED_IN','RAW_PROCESS_IN','RAW_RETURN','SEMI_RETURN','PROCESS_RAW_RETURN')",batchId);
            var mass=WarehouseFlowRules.mass(decimal(totals.get("issued")),output,returns,decimal(received.get("weight")));
            jdbc.update("UPDATE metal_work_report SET processor_output_weight=?,processor_returned_weight=?,keeper_received_weight=?,handover_difference=? WHERE report_id=?",output,returns,mass.received(),mass.handoverDifference(),report);
        }
        for (int i = 0; i < materials.size(); i++) {
            var m = materials.get(i);
            insert("INSERT INTO metal_report_material(report_id,code,name,issued_weight,loss_weight,average_loss) VALUES(?,?,?,?,?,?)", report, m.get("code"), m.get("name"), m.get("weight"), allocated.get(i), byWeight?BigDecimal.ZERO:allocated.get(i).divide(BigDecimal.valueOf(completed), 2, RoundingMode.HALF_UP));
        }
        if (calculation.abnormal()) {
            var product = JSON.parseObject(str(s.get("product_snapshot")));
            long warning = insert("INSERT INTO metal_warning_record(report_id,factory_id,process_name,product_category,specification,order_no,threshold_value,theoretical_loss,average_loss) VALUES(?,?,?,?,?,?,?,?,?)", report, batch.get("factory_id"), s.get("process_name"), Objects.toString(product.get("category"),Objects.toString(product.get("materialType"),"原料加工")), product.get("specification"), one("SELECT order_no FROM metal_work_order WHERE work_order_id=?", batch.get("work_order_id")).get("order_no"), calculation.threshold(), s.get("theoretical_loss"), calculation.average());
            jdbc.update("UPDATE metal_warning_record SET loss_unit=?,loss_rate_percent=? WHERE warning_id=?",percentage?"PERCENT":"G_PER_PIECE",rate==null?null:rate.rate(),warning);
            history(warning, "GENERATED", rule == null ? "按工单工序理论损耗快照判定" : "按配置规则生成");
        }
        jdbc.update("UPDATE metal_process_batch SET status='SETTLED',completed_quantity=?,version=version+1 WHERE batch_id=?", completed, batchId);
        semi.checkCapacity(number(batch.get("item_id")));
        refreshOrder(number(batch.get("work_order_id")));
    }

    void refreshOrder(Long id) {
        if ("CLOSED_PARTIAL".equals(one("SELECT status FROM metal_work_order WHERE work_order_id=?",id).get("status"))) return;
        long incomplete=0;
        for(var step:rows("SELECT s.*,i.quantity FROM metal_work_order_step s JOIN metal_work_order_item i ON i.item_id=s.item_id WHERE i.work_order_id=?",id)) if(!WeightStageProgressService.covered(jdbc,step,semi)) incomplete++;

        long open = jdbc.queryForObject("SELECT COUNT(*) FROM metal_process_batch WHERE work_order_id=? AND status='OPEN'", Long.class, id);
        long issued = jdbc.queryForObject("SELECT COUNT(*) FROM metal_warehouse_order WHERE work_order_id=? AND type='PRODUCTION_OUT' AND status='POSTED'", Long.class, id);
        boolean newFlow = jdbc.queryForObject("SELECT COUNT(*) FROM metal_work_order_review WHERE work_order_id=? AND action='CREATE'", Long.class, id) > 0;
        String status = incomplete == 0 && open == 0 ? "COMPLETED" : issued > 0 ? "IN_PROGRESS" : newFlow ? "WAITING_MATERIAL" : "APPROVED";
        WorkOrderCompletionService.transition(jdbc,id,status,SecurityUtils.getUserId());
    }

    /**
     * 校验预警状态和版本后登记处理结果。
     */
    public void handleWarning(Long id, Action input) {
        var warning = lock("warning-record", id);
        version(warning, input.version());
        if (!"PENDING".equals(warning.get("status")) || truth(warning.get("source_void")))
            throw fail("预警已处理或来源已作废");
        if (input.reason() == null || input.reason().isBlank()) throw fail("请填写处理结果");
        jdbc.update("UPDATE metal_warning_record SET status='HANDLED',result=?,handler_id=?,handler_name=?,handled_time=NOW(),version=version+1 WHERE warning_id=?", input.reason().strip(), SecurityUtils.getUserId(), actor(), id);
        history(id, "HANDLED", input.reason().strip());
    }

    private void history(Long id, String action, String result) {
        insert("INSERT INTO metal_warning_history(warning_id,action,result,actor_id,actor_name) VALUES(?,?,?,?,?)", id, action, result, SecurityUtils.getUserId(), actor());
    }

    /**
     * 查询指定工单的生产损耗汇总。
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<Map<String, Object>> summaries(Long workOrderId) {
        var w = one("SELECT factory_id FROM metal_work_order WHERE work_order_id=? AND del_flag='0'", workOrderId);
        scope.check(number(w.get("factory_id")));
        var result = cleaned(rows("SELECT CAST(r.item_id AS CHAR) item_id,CAST(r.process_id AS CHAR) process_id,s.process_name,SUM(r.loss_weight) loss_weight,CAST(ROUND(SUM(r.loss_weight)/SUM(r.completed_quantity),6) AS DECIMAL(20,6)) average_loss,SUM(r.completed_quantity) completed_quantity,CASE WHEN SUM(r.status='ABNORMAL')>0 THEN 'ABNORMAL' ELSE 'NORMAL' END status FROM metal_work_report r JOIN metal_process_batch b ON b.batch_id=r.batch_id JOIN metal_work_order_step s ON s.item_id=r.item_id AND s.process_id=r.process_id WHERE b.work_order_id=? AND r.voided=FALSE AND b.status='SETTLED' GROUP BY r.item_id,r.process_id,s.process_name,s.sequence_no ORDER BY r.item_id,s.sequence_no", workOrderId));
        var metrics=new ProcessConsumptionService(jdbc,scope).settledReportMetrics(workOrderId);
        for(var row:result){var values=metrics.get(row.get("itemId")+":"+row.get("processId"));if(values!=null)for(String key:List.of("lossUnit","measurementMode","thresholdValue","thresholdMin","thresholdMax","thresholdWarning","lossRatePercent","lossRateWarning","settledNetInputWeight","netInputWeight"))row.put(key,values.get(key));}
        return result;
    }

    /**
     * 查询指定工单产品工序的加工报工记录。
     */
    public List<Map<String, Object>> reportsForStep(Long itemId, Long processId) {
        var s = step(itemId, processId);
        var w = one("SELECT factory_id FROM metal_work_order WHERE work_order_id=?", s.get("work_order_id"));
        scope.check(number(w.get("factory_id")));
        return cleaned(rows("SELECT r.*,r.report_id id,b.measurement_mode,b.batch_no" + reportWeightColumns("r") + " FROM metal_work_report r JOIN metal_process_batch b ON b.batch_id=r.batch_id WHERE r.item_id=? AND r.process_id=? ORDER BY r.report_id", itemId, processId));
    }

    /**
     * 校验版本和冲销原因后冲销非生产关联单据。
     */
    public void reverseOrder(Long id, Action input) {
        var doc = lock("warehouse-order", id);
        if(doc.get("warehouse_flow_id")!=null)throw fail("交接单据须整体冲销仓储申请或关联加工批次");
        if(doc.get("acid_wash_id")!=null)throw fail("酸洗单据请整体冲销酸洗批次");
        version(doc, input.version());
        if (doc.get("batch_id") != null) {
            if(doc.get("inbound_submission_id")!=null)throw fail("生产分仓入库请整体冲销关联加工批次");
            var batch=record("process-batch",number(doc.get("batch_id")),true);
            boolean output=BatchCompletionRules.POLICY.equals(batch.get("completion_policy"))&&Set.of("SEMI_IN","FINISHED_IN","RAW_PROCESS_IN").contains(doc.get("type"))&&"OPEN".equals(batch.get("status"))&&jdbc.queryForObject("SELECT COUNT(*) FROM metal_batch_return_confirmation WHERE batch_id=? AND status='ACTIVE'",Long.class,doc.get("batch_id"))==0;
            if(!output){
                if(!ProcessRawReturnService.TYPE.equals(doc.get("type")))throw fail("生产单据请整体冲销关联加工批次");
                if(!"OPEN".equals(batch.get("status")))throw fail("已结算加工余料请整体冲销关联加工批次");
                if(BatchCompletionRules.POLICY.equals(batch.get("completion_policy")))throw fail("本批次全部还料须整体冲销关联加工批次");
            }
        }
        if (input.reason() == null || input.reason().isBlank()) throw fail("请填写冲销原因");
        reverseDocuments(List.of(doc), input.reason());
        if(doc.get("batch_id")!=null)jdbc.update("UPDATE metal_process_batch SET version=version+1 WHERE batch_id=?",doc.get("batch_id"));
    }

    /**
     * 校验批次及后续业务引用后整体冲销加工批次。
     */
    public void reverseBatch(Long id, Action input) {
        var batch = lock("process-batch", id);
        WarehouseReservations.batch(jdbc,id,0);
        version(batch, input.version());
        if ("VOID".equals(batch.get("status"))) throw fail("批次已冲销");
        if("COMPLETED".equals(one("SELECT status FROM metal_work_order WHERE work_order_id=?",batch.get("work_order_id")).get("status")))semi.guardWork(number(batch.get("work_order_id")));
        semi.guardBatch(id);
        if(Boolean.TRUE.equals(step(number(batch.get("item_id")),number(batch.get("process_id"))).get("weight_stage_closed")))throw fail("本工序已结束投产，请先受控重新打开再冲销");
        if ("CLOSED_PARTIAL".equals(one("SELECT status FROM metal_work_order WHERE work_order_id=?",batch.get("work_order_id")).get("status"))) throw fail("请先撤销部分关闭再冲销批次");
        if (input.reason() == null || input.reason().isBlank()) throw fail("请填写冲销原因");
        var docs = rows("SELECT *,order_id id FROM metal_warehouse_order WHERE batch_id=? AND status='POSTED' ORDER BY order_id DESC FOR UPDATE", id);
        reverseDocuments(docs, input.reason());
        jdbc.update("UPDATE metal_warehouse_flow SET status='VOID',version=version+1,reason=? WHERE batch_id=? AND status='POSTED'",input.reason(),id);
        jdbc.update("UPDATE metal_process_batch SET status='VOID',version=version+1 WHERE batch_id=?", id);
        jdbc.update("UPDATE metal_batch_return_confirmation SET status='VOID',version=version+1 WHERE batch_id=?",id);
        jdbc.update("UPDATE metal_work_report SET voided=TRUE WHERE batch_id=?", id);
        for (var warning : rows("SELECT w.warning_id FROM metal_warning_record w JOIN metal_work_report r ON r.report_id=w.report_id WHERE r.batch_id=? FOR UPDATE", id)) {
            jdbc.update("UPDATE metal_warning_record SET source_void=TRUE,version=version+1 WHERE warning_id=?", warning.get("warning_id"));
            history(number(warning.get("warning_id")), "SOURCE_VOID", input.reason());
        }
        refreshOrder(number(batch.get("work_order_id")));
    }

    private void reverseDocuments(List<Map<String, Object>> docs, String reason) {
        Set<Long> ids = new HashSet<>();
        docs.forEach(d -> ids.add(number(d.get("order_id"))));
        if (docs.stream().anyMatch(d -> !"POSTED".equals(d.get("status")))) throw fail("单据已冲销");
        // Releases carry no physical ledger. Check their relationships separately for ADJUST,
        // without blocking legitimate production-batch reversals that restore released stock.
        var adjustedSemis = new TreeSet<Long>();
        for (var doc : docs) {
            if (!"ADJUST".equals(doc.get("type"))) continue;
            for (var stock : rows("SELECT DISTINCT i.inventory_id FROM metal_stock_ledger l JOIN metal_inventory i ON i.inventory_id=l.inventory_id JOIN metal_warehouse_order o ON o.order_id=l.order_id WHERE o.order_id=? AND o.type='ADJUST' AND i.category='SEMI' AND l.reversal_of IS NULL", doc.get("order_id")))
                adjustedSemis.add(number(stock.get("inventory_id")));
        }
        for (Long stock : adjustedSemis) record("inventory", stock, true);
        for (Long stock : adjustedSemis) semi.guardAdjustmentReversal(stock);
        Set<Long> originStocks = new HashSet<>();
        for (var doc : docs) {
            Long id = number(doc.get("order_id"));
            if(!rows("SELECT r.reservation_id FROM metal_stock_reservation r JOIN metal_warehouse_order_line l ON l.line_id=r.source_line_id WHERE l.order_id=? AND r.status='ACTIVE' LIMIT 1",id).isEmpty())throw fail("原出库单存在待验收退回引用，请先完成或取消退回申请");
            if(ProcessRawReturnService.TYPE.equals(doc.get("type")))for(var recovered:rows("SELECT r.inventory_id FROM metal_process_raw_return_line r JOIN metal_warehouse_order_line l ON l.line_id=r.line_id WHERE l.order_id=?",id))originStocks.add(number(recovered.get("inventory_id")));
            for (var stock : rows("SELECT DISTINCT l.inventory_id FROM metal_warehouse_order_line l JOIN metal_inventory i ON i.inventory_id=l.inventory_id WHERE l.order_id=? AND (i.origin_batch_id IS NOT NULL AND ? IN ('SEMI_IN','FINISHED_IN','RAW_PROCESS_IN') OR ?='OPENING')", id, doc.get("type"), doc.get("type")))
                originStocks.add(number(stock.get("inventory_id")));
            for (var ref : rows("SELECT DISTINCT o.order_id FROM metal_warehouse_order_line x JOIN metal_warehouse_order o ON o.order_id=x.order_id JOIN metal_warehouse_order_line source ON source.line_id=x.source_line_id WHERE source.order_id=? AND o.status='POSTED'", id))
                if (!ids.contains(number(ref.get("order_id")))) throw fail("单据存在退还引用，不能冲销");
        }
        for (var doc : docs)
            for (var l : rows("SELECT * FROM metal_stock_ledger WHERE order_id=? AND reversal_of IS NULL AND (delta_weight>0 OR delta_quantity>0)", doc.get("order_id")))
                for (var dependency : rows("SELECT DISTINCT x.order_id FROM metal_stock_ledger x JOIN metal_warehouse_order o ON o.order_id=x.order_id WHERE x.inventory_id=? AND x.ledger_id>? AND o.status='POSTED' AND x.reversal_of IS NULL", l.get("inventory_id"), l.get("ledger_id")))
                    if (!ids.contains(number(dependency.get("order_id"))))
                        throw fail("入库记录存在后续业务引用，不能冲销");
        for (Long stock : originStocks)
            for (var ref : rows("SELECT DISTINCT l.order_id FROM metal_stock_ledger l JOIN metal_warehouse_order o ON o.order_id=l.order_id WHERE l.inventory_id=? AND o.status='POSTED'", stock))
                if (!ids.contains(number(ref.get("order_id")))) throw fail("产出库存已被后续业务引用，不能冲销");
        var ledgers = new ArrayList<Map<String, Object>>();
        for (Long id : ids)
            ledgers.addAll(rows("SELECT * FROM metal_stock_ledger WHERE order_id=? AND reversal_of IS NULL ORDER BY ledger_id DESC", id));
        ledgers.sort(Comparator.comparing((Map<String, Object> r) -> number(r.get("ledger_id"))).reversed());
        for (var ledger : ledgers) {
            Long stockId = number(ledger.get("inventory_id"));
            var stock = record("inventory", stockId, true);
            balance(stock, decimal(ledger.get("delta_weight")).negate(), -number(ledger.get("delta_quantity")), number(ledger.get("order_id")), number(ledger.get("line_id")), number(ledger.get("ledger_id")));
        }
        for (Long id : ids)
            jdbc.update("UPDATE metal_warehouse_order SET status='VOID',reason=?,version=version+1 WHERE order_id=?", reason, id);
    }
    /**
     * 获取当前业务可选择的关联资料。
     */
    /**
     * Read only the entry snapshot; never expose the full production work-order detail.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public WarehouseWorkOrderContext workOrderContext(Long factoryId, Long workOrderId) {
        permitOption("work-order-context");
        if (factoryId == null || factoryId <= 0 || workOrderId == null || workOrderId <= 0)
            throw fail("请选择有效的工厂和生产工单");
        scope.check(factoryId);
        var headers = rows("SELECT customer,status,task_type,processing_policy_version FROM metal_work_order WHERE work_order_id=? AND factory_id=? AND del_flag='0'", workOrderId, factoryId);
        if (headers.isEmpty()) throw fail("工单不存在、已删除或不属于当前工厂，请刷新选择");
        var header = headers.get(0);
        if (!Set.of("WAITING_MATERIAL", "IN_PROGRESS").contains(header.get("status")))
            throw fail("工单状态已变化，请刷新选择待领料或生产中的工单");
        var itemRows = rows("SELECT item_id,product_id,material_id,planned_input_weight,CAST(COALESCE(material_snapshot,product_snapshot) AS CHAR) product_snapshot FROM metal_work_order_item WHERE work_order_id=? ORDER BY sequence_no,item_id", workOrderId);
        var planRows = rows("SELECT s.item_id,s.process_id,s.process_name,s.sequence_no,s.measurement_mode,s.weight_stage_closed,s.process_role,s.output_stage FROM metal_work_order_step s JOIN metal_work_order_item i ON i.item_id=s.item_id WHERE i.work_order_id=? ORDER BY i.sequence_no,s.sequence_no,s.process_id", workOrderId);
        Map<Long, List<WarehouseWorkOrderContext.Plan>> plans = new HashMap<>();
        for (var row : planRows)
            plans.computeIfAbsent(number(row.get("item_id")), key -> new ArrayList<>()).add(new WarehouseWorkOrderContext.Plan(str(row.get("process_id")), str(row.get("process_name")), number(row.get("sequence_no")).intValue(),SemiMeasurementRules.mode(row.get("measurement_mode")),Boolean.TRUE.equals(row.get("weight_stage_closed")),str(row.get("process_role")),str(row.get("output_stage"))));
        List<WarehouseWorkOrderContext.Item> items = new ArrayList<>();
        for (var row : itemRows) {
            var snapshot = JSON.parseObject(str(row.get("product_snapshot")));
            items.add(new WarehouseWorkOrderContext.Item(str(row.get("item_id")), str(row.get("product_id")), new WarehouseWorkOrderContext.Snapshot(snapshot.getString("code"), snapshot.getString("name"), snapshot.getString("specification"),snapshot.getString("metalTypeId")), List.copyOf(plans.getOrDefault(number(row.get("item_id")), List.of())),str(row.get("material_id")),str(row.get("planned_input_weight")),snapshot.getString("metalTypeId")));
        }
        return new WarehouseWorkOrderContext(workOrderId.toString(), factoryId.toString(), str(header.get("customer")), List.copyOf(items),Objects.toString(header.get("task_type"),"PRODUCTION"),header.get("processing_policy_version")==null?1:number(header.get("processing_policy_version")).intValue());
    }

    // Candidate data has its own permissions: a warehouse dictionary reader must not read stock or source documents.
    private void permitOption(String kind) {
        String order = "metal:warehouse-order:add,metal:warehouse-order:import";
        String inventory = "metal:inventory:list,metal:inventory:query";
        String batch = "metal:process-batch:list,metal:process-batch:query,metal:process-batch:add";
        String rule = "metal:warning-rule:add,metal:warning-rule:edit";
        String allowed = switch (kind) {
            case "factories" ->
                    "metal:warehouse-order:receive,metal:warehouse-order:accept,metal:warehouse-order:handover,metal:warehouse:list,metal:warehouse:query,metal:warehouse:add,metal:warehouse:edit," + inventory + ",metal:inventory:add,metal:inventory:adjust,metal:warehouse-order:list,metal:warehouse-order:query," + order + "," + batch + ",metal:warning-rule:list," + rule + ",metal:warning-record:list,metal:warning-record:query";
            case "warehouses" -> inventory + ",metal:inventory:add,metal:warehouse-order:handover," + order;
            case "people" -> "metal:inventory:add,metal:inventory:adjust,metal:warehouse-order:handover," + order;
            case "materials" -> "metal:inventory:add,metal:warehouse-order:handover," + order;
            case "customers" -> "metal:inventory:add," + order;
            case "products" -> "metal:inventory:add," + order + "," + rule;
            case "processes" -> rule;
            case "work-order-context" -> order;
            case "work-orders" -> "metal:process-batch:add," + order;
            case "batches" -> batch + ",metal:warehouse-order:handover," + order;
            case "stocks" -> inventory + ",metal:inventory:adjust," + order;
            case "out-lines" -> "metal:warehouse-order:query," + order;
            default -> throw fail("选项无效");
        };
        if (!scope.isAdmin() && Arrays.stream(allowed.split(",")).noneMatch(SecurityUtils::hasPermi))
            throw new ServiceException("没有此候选数据的访问权限", 403);
    }

    private List<Map<String, Object>> outLines(Long factoryId) {
        var data = rows("SELECT l.line_id id,o.order_id,o.order_no,o.warehouse_name,CONCAT(o.order_no,' / ',l.name) name,l.code,l.specification,l.organization,l.category,l.reference_id,l.net_weight,l.quantity,l.measurement_mode,l.reference_quantity,o.batch_id,o.type,l.inventory_id,l.batch_no,i.warehouse_id,sb.sequence_no origin_process_sequence,ss.process_name origin_process_name,sw.order_no origin_work_order_no,l.net_weight-COALESCE(r.returned,0) remaining_weight,b.batch_status,b.issued_weight,b.output_weight,b.returned_weight FROM metal_warehouse_order_line l JOIN metal_warehouse_order o ON o.order_id=l.order_id LEFT JOIN metal_inventory i ON i.inventory_id=l.inventory_id LEFT JOIN metal_process_batch sb ON sb.batch_id=i.origin_batch_id LEFT JOIN metal_work_order_step ss ON ss.item_id=sb.item_id AND ss.process_id=sb.process_id LEFT JOIN metal_work_order sw ON sw.work_order_id=sb.work_order_id LEFT JOIN (" + RawReturnAllowance.totalsSql(true) + ") b ON b.batch_id=o.batch_id LEFT JOIN (SELECT rl.source_line_id,SUM(rl.net_weight) returned FROM metal_warehouse_order_line rl JOIN metal_warehouse_order ro ON ro.order_id=rl.order_id WHERE ro.factory_id=? AND ro.status='POSTED' AND rl.source_line_id IS NOT NULL GROUP BY rl.source_line_id) r ON r.source_line_id=l.line_id WHERE o.factory_id=? AND o.kind='OUT' AND o.status='POSTED' ORDER BY l.line_id", factoryId, factoryId, factoryId);
        for (var row : data) {
            if ("PRODUCTION_OUT".equals(row.get("type")) && Set.of("RAW","SEMI").contains(str(row.get("category")))) {
                var available = RawReturnAllowance.remaining(row.get("issued_weight"), row.get("output_weight"), row.get("returned_weight"));
                row.put("batch_remaining_weight", available);
                row.put("returnable_weight", RawReturnAllowance.limit(decimal(row.get("remaining_weight")), available, row.get("batch_status")));
            }
            if (!"PRODUCTION_OUT".equals(row.get("type")) || !Set.of("RAW","SEMI").contains(str(row.get("category")))) row.remove("batch_status");
            if("SEMI".equals(row.get("category")) && row.get("batch_id")!=null){long issued=SemiFlowService.netIssued(jdbc,number(row.get("batch_id")));long produced=jdbc.queryForObject("SELECT COALESCE(SUM(l.quantity),0) FROM metal_warehouse_order o JOIN metal_warehouse_order_line l ON l.order_id=o.order_id WHERE o.batch_id=? AND o.status='POSTED' AND o.type IN ('SEMI_IN','FINISHED_IN','RAW_PROCESS_IN')",Long.class,row.get("batch_id"));row.put("batch_returnable_quantity",Math.max(0,issued-produced));}
            row.put("remaining_quantity", SemiFlowService.num(row.get("quantity")) - java.util.Optional.ofNullable(jdbc.queryForObject("SELECT COALESCE(SUM(l.quantity),0) FROM metal_warehouse_order_line l JOIN metal_warehouse_order o ON o.order_id=l.order_id WHERE l.source_line_id=? AND o.status='POSTED'", Long.class, row.get("id"))).orElse(0L));
            row.remove("issued_weight");
            row.remove("output_weight");
            row.remove("returned_weight");
        }
        return cleaned(data);
    }

    public record ProductionStockSnapshot(List<Map<String,Object>> stocks,Map<String,Object> entryContext) {}

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public ProductionStockSnapshot productionStockSnapshot(Long factoryId,Long warehouseId,Long workOrderId,Long itemId,Long processId) {
        permitOption("stocks");
        if(factoryId==null||warehouseId==null||workOrderId==null||itemId==null||processId==null)throw fail("请先选择工厂、仓库、工单、产品明细和工序");
        scope.check(factoryId);
        var h=one("SELECT factory_id,status FROM metal_warehouse WHERE warehouse_id=? AND del_flag='0'",warehouseId);
        if(!factoryId.equals(number(h.get("factory_id"))))throw new ServiceException("库房不属于本厂",403);
        if(!"0".equals(h.get("status")))throw fail("库房已停用");
        var stocks=cleaned(semi.candidates(factoryId,warehouseId,workOrderId,itemId,processId));
        var target=one("SELECT s.sequence_no,s.measurement_mode FROM metal_work_order_step s JOIN metal_work_order_item i ON i.item_id=s.item_id WHERE s.item_id=? AND s.process_id=? AND i.work_order_id=?",itemId,processId,workOrderId);
        long capacity=semi.entryCapacity(itemId,number(target.get("sequence_no")));
        var context=new LinkedHashMap<String,Object>();context.putAll(Map.of("factoryId",factoryId.toString(),"warehouseId",warehouseId.toString(),"workOrderId",workOrderId.toString(),"itemId",itemId.toString(),"processId",processId.toString(),"externalEntryCapacity",Long.toString(capacity)));context.put("capacityKind","SOURCE_MODE");context.put("weightEntryNote","重量型半成品按库存及释放净重领用，不使用参考件数计算接入额度");return new ProductionStockSnapshot(stocks,context);
    }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> processRawReturnContext(Long factoryId,Long workOrderId,Long itemId,Long processId,Long batchId){permitOption("work-order-context");return new ProcessRawReturnService(jdbc,scope).context(factoryId,workOrderId,itemId,processId,batchId);}
    public List<Map<String,Object>> productionStocks(Long factoryId,Long warehouseId,Long workOrderId,Long itemId,Long processId){permitOption("stocks");return cleaned(semi.candidates(factoryId,warehouseId,workOrderId,itemId,processId));}

    public List<Map<String, Object>> options(String kind, Long factoryId, Long referenceId) {
        return options(kind, factoryId, referenceId, null);
    }

    public List<Map<String, Object>> options(String kind, Long factoryId, Long referenceId, String outputType) {
        permitOption(kind);
        if (kind.equals("factories")) {
            Long own = scope.currentFactory();
            return cleaned(own == null ? rows("SELECT factory_id id,factory_name name FROM metal_factory WHERE del_flag='0' ORDER BY factory_id") : rows("SELECT factory_id id,factory_name name FROM metal_factory WHERE factory_id=? AND del_flag='0'", own));
        }
        if (factoryId == null) throw fail("请选择工厂");
        scope.check(factoryId);
        return switch (kind) {
            case "customers" -> new RawInventoryCustomers(jdbc).options(factoryId);
            case "warehouses" ->
                    cleaned(rows("SELECT warehouse_id id,name FROM metal_warehouse WHERE factory_id=? AND del_flag='0' AND status='0' ORDER BY sort_order,warehouse_id", factoryId));
            case "people" ->
                    cleaned(rows("SELECT u.user_id id,COALESCE(NULLIF(u.nick_name,''),u.user_name) name,u.phonenumber phone," + PersonOptions.roles("u") + " role_names FROM sys_user u WHERE u.factory_id=? AND u.status='0' AND u.del_flag='0' ORDER BY u.user_id", factoryId));
            case "materials" ->
                    cleaned(rows("SELECT m.material_id id,m.material_code code,CONCAT(t.name,m.material_type) name,m.material_type,m.metal_type_id,t.name metal_type_name,m.specification FROM metal_material m JOIN metal_type t ON t.metal_type_id=m.metal_type_id WHERE m.del_flag='0' ORDER BY m.material_id"));
            case "products" ->
                    cleaned(rows("SELECT product_id id,product_code code,product_name name,product_category category,specification FROM metal_product WHERE del_flag='0' ORDER BY product_id"));
            case "processes" ->
                    cleaned(rows("SELECT p.process_id id,p.process_name name FROM metal_product_process x JOIN metal_process p ON p.process_id=x.process_id WHERE x.product_id=? AND p.del_flag='0' ORDER BY x.sequence_no", referenceId));
            case "work-orders" -> {
                if (outputType == null) yield cleaned(rows("SELECT work_order_id id,order_no name,status FROM metal_work_order WHERE factory_id=? AND del_flag='0' AND status IN ('WAITING_MATERIAL','IN_PROGRESS') ORDER BY work_order_id", factoryId));
                if (!Set.of("SEMI_IN", "FINISHED_IN", "RAW_PROCESS_IN").contains(outputType)) throw fail("请选择有效的产出入库类型");
                String comparison = "SEMI_IN".equals(outputType) ? "<" : "=";
                yield cleaned(rows("SELECT w.work_order_id id,w.order_no name,w.status FROM metal_work_order w WHERE w.factory_id=? AND w.del_flag='0' AND w.status IN ('WAITING_MATERIAL','IN_PROGRESS') AND EXISTS(SELECT 1 FROM metal_work_order_item i JOIN metal_work_order_step s ON s.item_id=i.item_id WHERE i.work_order_id=w.work_order_id AND s.sequence_no " + comparison + " (SELECT MAX(last_step.sequence_no) FROM metal_work_order_step last_step WHERE last_step.item_id=i.item_id)) ORDER BY w.work_order_id", factoryId));
            }
            case "stocks" ->
                    cleaned(rows("SELECT i.inventory_id id,i.name,i.code,i.specification,i.batch_no,i.category,i.warehouse_id,i.reference_id,i.net_weight,i.quantity,i.measurement_mode,i.reference_quantity,i.reference_complete,i.organization,i.customer_id,i.material_ownership,i.ownership_status,COALESCE((SELECT SUM(r.weight) FROM metal_stock_reservation r WHERE r.inventory_id=i.inventory_id AND r.status='ACTIVE'),0) reserved_weight,i.net_weight-COALESCE((SELECT SUM(r.weight) FROM metal_stock_reservation r WHERE r.inventory_id=i.inventory_id AND r.status='ACTIVE'),0) available_weight,i.origin_batch_id,b.status origin_batch_status,s.process_name origin_process_name,b.sequence_no origin_process_sequence,w.order_no origin_work_order_no FROM metal_inventory i LEFT JOIN metal_process_batch b ON b.batch_id=i.origin_batch_id LEFT JOIN metal_work_order w ON w.work_order_id=b.work_order_id LEFT JOIN metal_work_order_step s ON s.item_id=b.item_id AND s.process_id=b.process_id WHERE i.factory_id=? AND i.del_flag='0' AND i.origin_recovery_line_id IS NULL AND NOT EXISTS(SELECT 1 FROM metal_process_raw_return_line recovery WHERE recovery.inventory_id=i.inventory_id) ORDER BY i.inventory_id", factoryId));
            case "batches" ->
                    cleaned(rows("SELECT b.batch_id id,CONCAT(b.batch_no,' / ',s.process_name) name,b.batch_no,b.planned_quantity,b.version,b.completion_policy,b.work_order_id,b.item_id,b.process_id,(EXISTS(SELECT 1 FROM metal_warehouse_order io JOIN metal_warehouse_order_line il ON il.order_id=io.order_id WHERE io.batch_id=b.batch_id AND io.status='POSTED' AND io.type='PRODUCTION_OUT' AND il.category='SEMI' AND il.net_weight>0) AND EXISTS(SELECT 1 FROM metal_warehouse_order oo JOIN metal_warehouse_order_line ol ON ol.order_id=oo.order_id WHERE oo.batch_id=b.batch_id AND oo.status='POSTED' AND oo.type IN ('SEMI_IN','FINISHED_IN','RAW_PROCESS_IN') AND ol.net_weight>0) AND (SELECT COALESCE(SUM(CASE WHEN ro.type='PRODUCTION_OUT' THEN rl.net_weight WHEN ro.type IN ('SEMI_IN','FINISHED_IN','RAW_PROCESS_IN','RAW_RETURN','SEMI_RETURN','PROCESS_RAW_RETURN') THEN -rl.net_weight ELSE 0 END),0) FROM metal_warehouse_order ro JOIN metal_warehouse_order_line rl ON rl.order_id=ro.order_id WHERE ro.batch_id=b.batch_id AND ro.status='POSTED')>0) recovery_eligible FROM metal_process_batch b JOIN metal_work_order w ON w.work_order_id=b.work_order_id JOIN metal_work_order_step s ON s.item_id=b.item_id AND s.process_id=b.process_id WHERE b.factory_id=? AND b.status='OPEN' ORDER BY b.batch_id", factoryId));
            case "out-lines" -> outLines(factoryId);
            default -> throw fail("选项无效");
        };
    }
}
