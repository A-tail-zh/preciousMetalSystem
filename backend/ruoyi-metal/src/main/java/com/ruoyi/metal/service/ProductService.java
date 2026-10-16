package com.ruoyi.metal.service;

import java.util.*;
import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ruoyi.metal.catalog.*;
import com.ruoyi.metal.mapper.ProductMapper;
import com.ruoyi.metal.security.FactoryScope;
import com.ruoyi.common.exception.ServiceException;

/**
 * 产品业务服务，负责业务校验、数据处理和相关持久化操作。
 */
@Service
public class ProductService extends MasterService<ProductRecord> {
    private final JdbcTemplate jdbc;
    private final QuoteRuleService quoteRules;
    public static final Map<String, List<String>> DIMENSIONS = Map.of(
            "铱坩埚弧底", List.of("innerDiameter", "innerHeight", "wallThickness", "bottomThickness", "rHeight"),
            "铱坩埚平底", List.of("innerDiameter", "innerHeight", "wallThickness", "bottomThickness"),
            "铱坩埚梯形", List.of("innerDiameter", "innerHeight", "wallThickness", "bottomThickness"),
            "铱环", List.of("outerDiameter", "innerDiameter", "height"), "铱杆实心", List.of("diameter", "length"),
            "铱杆空心", List.of("outerDiameter", "innerDiameter", "length"));

    public ProductService(ProductMapper mapper, FactoryScope scope, JdbcTemplate jdbc, QuoteRuleService quoteRules) {
        super(mapper, scope);
        this.jdbc = jdbc;
        this.quoteRules = quoteRules;
    }

    /**
     * 保存前校验业务数据，并准备需要持久化的字段。
     */
    @Override
    protected void prepare(ProductRecord row, ProductRecord old) {
        List<String> fields = DIMENSIONS.get(row.getCategory());
        if (fields == null || row.getDimensions() == null || !row.getDimensions().keySet().equals(new HashSet<>(fields)))
            throw new ServiceException("请填写类别对应的尺寸，不得保留其他类别尺寸");
        for (String key : fields) {
            try {
                BigDecimal value = new BigDecimal(row.getDimensions().get(key));
                if (value.signum() <= 0 || value.scale() > 6 || value.precision() - value.scale() > 14)
                    throw new IllegalArgumentException();
            } catch (Exception e) {
                throw new ServiceException("尺寸必须为正数，最多14位整数、6位小数：" + key);
            }
        }
        if (fields.contains("outerDiameter") && new BigDecimal(row.getDimensions().get("outerDiameter")).compareTo(new BigDecimal(row.getDimensions().get("innerDiameter"))) <= 0)
            throw new ServiceException("外径必须大于内径");
        row.setSpecification(specification(row));
        row.setVolume(VolumeCalculator.supported(row.getCategory()) ? VolumeCalculator.stored(VolumeCalculator.calculate(row.getCategory(), row.getDimensions(), quoteRules.get().getPiValue())) : null);
        if (row.getProcesses() == null || row.getProcesses().size() > 100)
            throw new ServiceException("工序配置无效，最多100道工序");
        List<Long> ids = row.getProcesses().stream().map(ProductStep::getProcessId).toList();
        if (new HashSet<>(ids).size() != ids.size()) throw new ServiceException("同一工序不能重复选择");
        for (Long id : ids.stream().sorted().toList())
            if (jdbc.queryForList("SELECT process_id FROM metal_process WHERE process_id=? AND del_flag='0' FOR UPDATE", id).isEmpty())
                throw new ServiceException("工序不存在或已删除");
    }

    /**
     * 规格由当前类别的全部尺寸生成，单位与尺寸录入一致（cm）。
     */
    static String specification(ProductRecord row) {
        Map<String, String> labels = Map.of("innerDiameter", "内径", "innerHeight", "内高",
                "wallThickness", "壁厚", "bottomThickness", "底厚", "rHeight", "R高度",
                "outerDiameter", "外径", "height", "高度", "diameter", "直径", "length", "长度");
        return DIMENSIONS.getOrDefault(row.getCategory(), List.of()).stream()
                .filter(key -> row.getDimensions() != null && row.getDimensions().get(key) != null)
                .map(key -> labels.get(key) + new BigDecimal(row.getDimensions().get(key)).stripTrailingZeros().toPlainString() + "cm")
                .collect(java.util.stream.Collectors.joining("；"));
    }

    /**
     * 保存主记录的关联关系。
     */
    @Override
    protected void relations(ProductRecord row) {
        jdbc.update("DELETE FROM metal_product_process WHERE product_id=?", row.getId());
        int order = 0;
        for (ProductStep step : row.getProcesses())
            jdbc.update("INSERT INTO metal_product_process(product_id,process_id,sequence_no,theoretical_loss) VALUES(?,?,?,?)", row.getId(), step.getProcessId(), ++order, step.getLoss());
    }

    /**
     * 为查询结果批量补充关联资料。
     */
    @Override
    protected void hydrate(List<ProductRecord> rows) {
        if (rows.isEmpty()) return;
        var pi = rows.stream().anyMatch(r -> VolumeCalculator.supported(r.getCategory())) ? quoteRules.get().getPiValue() : null;
        rows.forEach(r -> r.setSpecification(specification(r)));
        rows.forEach(r -> r.setVolume(VolumeCalculator.supported(r.getCategory()) ? VolumeCalculator.stored(VolumeCalculator.calculate(r.getCategory(), r.getDimensions(), pi)) : null));
        Map<Long, ProductRecord> indexed = new HashMap<>();
        rows.forEach(r -> {
            r.setProcesses(new ArrayList<>());
            indexed.put(r.getId(), r);
        });
        String placeholders = String.join(",", Collections.nCopies(rows.size(), "?"));
        jdbc.query("SELECT pp.product_id,pp.process_id,pp.theoretical_loss,p.process_name FROM metal_product_process pp JOIN metal_process p ON p.process_id=pp.process_id WHERE pp.product_id IN (" + placeholders + ") ORDER BY pp.product_id,pp.sequence_no", (org.springframework.jdbc.core.RowCallbackHandler) r -> {
            ProductStep s = new ProductStep();
            s.setProcessId(r.getLong("process_id"));
            s.setLoss(r.getBigDecimal("theoretical_loss"));
            s.setProcessName(r.getString("process_name"));
            indexed.get(r.getLong("product_id")).getProcesses().add(s);
        }, rows.stream().map(ProductRecord::getId).toArray());
    }

    /**
     * 检查业务引用和保护规则，不满足删除条件时抛出异常。
     */
    @Override
    protected void deletable(ProductRecord row) {
        WorkOrderReferences.product(jdbc, row.getId());
        WarehouseReferences.product(jdbc, row.getId());
        if (jdbc.queryForObject("SELECT COUNT(*) FROM metal_line_product WHERE product_id=?", Integer.class, row.getId()) > 0)
            throw new ServiceException("产品被产线引用，不能删除：" + row.getName());
    }
}
