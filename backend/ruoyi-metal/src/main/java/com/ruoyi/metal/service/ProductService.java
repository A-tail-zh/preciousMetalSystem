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
public class ProductService extends FactoryCatalogService<ProductRecord> {
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
        super.prepare(row,old);
        int priorVersion=old==null?2:version(old);
        int currentVersion=old==null?2:row.getCalculationVersion()==null?priorVersion:row.getCalculationVersion();
        if(currentVersion<1||currentVersion>2||priorVersion==2&&currentVersion!=2)throw new ServiceException("不能退回旧版产品计算规则");
        row.setCalculationVersion(currentVersion);
        if(currentVersion==1){
            if(row.getMetalTypeId()==null)row.setMetalTypeId(old.getMetalTypeId()==null?1L:old.getMetalTypeId());
            if(row.getProductType()==null)row.setProductType(ProductTypes.legacy(row.getCategory()));
            if(!ProductTypes.LEGACY.contains(row.getProductType()==null?"":row.getProductType()))throw new ServiceException("旧版产品类型无效");
        }else{
            if(row.getMetalTypeId()==null)throw new ServiceException("请选择金属类型");
            ProductTypes.require(row.getProductType());
        }
        String metalName=quoteRules.metalName(row.getMetalTypeId());
        scope.requireCatalog("metal",row.getMetalTypeId(),row.getFactoryId());
        String category=metalName+row.getProductType();
        if(row.getCategory()!=null&&!row.getCategory().equals(category))throw new ServiceException("金属、产品类型和原类别不一致");
        row.setMetalTypeName(metalName);row.setCategory(category);
        boolean sameIdentity=old!=null&&priorVersion==currentVersion&&Objects.equals(old.getCategory(),category);
        if(old!=null&&!sameIdentity){WorkOrderReferences.product(jdbc,old.getId());WarehouseReferences.product(jdbc,old.getId());}
        if(currentVersion==1){
            if(!sameIdentity||!Objects.equals(old.getDimensions(),row.getDimensions()))throw new ServiceException("旧版产品只允许维护非几何信息；修改尺寸请明确转为新版产品类型");
            var fields=DIMENSIONS.get(ProductTypes.geometry(row.getProductType()));
            if(row.getDimensions()==null||!row.getDimensions().keySet().equals(new HashSet<>(fields)))throw new ServiceException("旧版尺寸不完整，请核对历史资料");
            for(String key:fields){
                try{
                    BigDecimal value=new BigDecimal(row.getDimensions().get(key));
                    if(value.signum()<=0||value.scale()>6||value.precision()-value.scale()>14)throw new IllegalArgumentException();
                }catch(RuntimeException e){throw new ServiceException("旧版尺寸无效，请核对历史资料："+VolumeCalculator.dimensionLabel(key));}
            }
            if(fields.contains("outerDiameter")&&new BigDecimal(row.getDimensions().get("outerDiameter")).compareTo(new BigDecimal(row.getDimensions().get("innerDiameter")))<=0)throw new ServiceException("外径必须大于内径");
            row.setVolume(old.getVolume());row.setWeight(old.getWeight());
        }else{
            row.setDimensions(ProductGeometry.normalize(row.getProductType(),row.getDimensions()));
            boolean sameDimensions=sameIdentity&&Objects.equals(old.getDimensions(),row.getDimensions());
            var rule=quoteRules.volumeRuleForMetal(row.getMetalTypeId());
            row.setVolume(sameDimensions&&old.getVolume()!=null?old.getVolume():ProductGeometry.volume(row.getProductType(),row.getDimensions(),ProductGeometry.parameter(rule,"piValue"),ProductGeometry.parameter(rule,"curvedBottomFactor")));
            row.setWeight(ProductGeometry.weight(row.getVolume(),quoteRules.rule(row.getMetalTypeId()).get("density")));
        }
        row.setSpecification(specification(row));
        row.setPurity(BusinessDecimal.changed(row.getPurity(),old==null?null:old.getPurity()));
        Map<Long,BigDecimal> previousLosses=new HashMap<>();
        Map<Long,String> previousUnits=new HashMap<>();
        if(old!=null){
            if(old.getProcesses()!=null)old.getProcesses().forEach(step->{previousLosses.put(step.getProcessId(),step.getLoss());previousUnits.put(step.getProcessId(),step.getLossUnit());});
            // mapper.lock loads the product row only; old route decimals must be read before comparing edits.
            if(previousLosses.isEmpty()&&old.getId()!=null)jdbc.query("SELECT process_id,theoretical_loss,loss_unit FROM metal_product_process WHERE product_id=?",(org.springframework.jdbc.core.RowCallbackHandler)rs->{previousLosses.put(rs.getLong("process_id"),rs.getBigDecimal("theoretical_loss"));previousUnits.put(rs.getLong("process_id"),Objects.toString(rs.getString("loss_unit"),"G_PER_PIECE"));},old.getId());
        }
        if(row.getProcesses()!=null)for(var step:row.getProcesses()){
            if(!Set.of("PERCENT","G_PER_PIECE").contains(Objects.toString(step.getLossUnit(),"")))throw new ServiceException("理论损耗单位无效");
            if("PERCENT".equals(step.getLossUnit())){
                if(step.getLoss()==null||step.getLoss().signum()<0||step.getLoss().compareTo(BigDecimal.valueOf(100))>0)throw new ServiceException("理论损耗率须为0至100%");
                step.setLoss(BusinessDecimal.round(step.getLoss()));
            }else{
                if(!"G_PER_PIECE".equals(previousUnits.get(step.getProcessId()))||!previousLosses.containsKey(step.getProcessId())||step.getLoss().compareTo(previousLosses.get(step.getProcessId()))!=0)throw new ServiceException("旧理论损耗仅保留历史值，请明确配置百分比");
                step.setLoss(previousLosses.get(step.getProcessId()));
            }
        }
        if (row.getProcesses() == null || row.getProcesses().size() > 100)
            throw new ServiceException("工序配置无效，最多100道工序");
        for(int i=0;i<row.getProcesses().size();i++){
            String mode=SemiMeasurementRules.mode(row.getProcesses().get(i).getMeasurementMode());
            if(i==row.getProcesses().size()-1&&mode.equals("WEIGHT"))throw new ServiceException("最后一道工序产出成品，须按件管理");
            if(mode.equals("WEIGHT")&&"G_PER_PIECE".equals(row.getProcesses().get(i).getLossUnit()))throw new ServiceException("按重量管理须先重新配置损耗百分比");
        }
        List<Long> ids = row.getProcesses().stream().map(ProductStep::getProcessId).toList();
        if (new HashSet<>(ids).size() != ids.size()) throw new ServiceException("同一工序不能重复选择");
        for (Long id : ids.stream().sorted().toList()) {
            scope.requireCatalog("process",id,row.getFactoryId());
            if (jdbc.queryForList("SELECT process_id FROM metal_process WHERE process_id=? AND del_flag='0' FOR UPDATE", id).isEmpty())
                throw new ServiceException("工序不存在或已删除");
        }
        if (!ids.isEmpty()) {
            String marks=String.join(",",Collections.nCopies(ids.size(),"?"));
            var types=new HashMap<Long,String>();
            for(var process:jdbc.queryForList("SELECT p.process_id,t.type_name FROM metal_process p JOIN metal_process_type t ON t.type_id=p.process_type WHERE p.process_id IN ("+marks+")",ids.toArray()))
                types.put(((Number)process.get("process_id")).longValue(),Objects.toString(process.get("type_name"),""));
            if(types.size()!=ids.size())throw new ServiceException("工序类型不存在，请重新选择有效工序");
            ProcessingPolicy.route("PRODUCTION",ids.stream().map(types::get).toList(),row.getProcesses().stream().map(ProductStep::getMeasurementMode).toList());
        }
    }

    /**
     * 规格由当前类别的全部尺寸生成，单位与尺寸录入一致（cm）。
     */
    public static int version(ProductRecord row){return row.getCalculationVersion()==null?1:row.getCalculationVersion();}

    @org.springframework.transaction.annotation.Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Map<String,Object> preview(ProductCalculationRequest input,Long factory){
        scope.requireCatalog("metal",input.metalTypeId(),scope.resolveFactory(factory));return preview(input);
    }
    @org.springframework.transaction.annotation.Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Map<String,Object> preview(ProductCalculationRequest input){
        quoteRules.metalName(input.metalTypeId());
        var dimensions=ProductGeometry.normalize(input.productType(),input.dimensions());
        var rule=quoteRules.volumeRuleForMetal(input.metalTypeId());
        var volume=ProductGeometry.volume(input.productType(),dimensions,ProductGeometry.parameter(rule,"piValue"),ProductGeometry.parameter(rule,"curvedBottomFactor"));
        var weight=ProductGeometry.weight(volume,quoteRules.rule(input.metalTypeId()).get("density"));
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("volume",volume==null?null:volume.toPlainString());result.put("weight",weight==null?null:weight.toPlainString());
        result.put("dimensions",dimensions);result.put("volumeRule",rule);result.put("calculationVersion",2);
        return result;
    }

    static String specification(ProductRecord row) {
        if(version(row)==2)return ProductGeometry.specification(row.getProductType(),row.getDimensions());
        Map<String, String> labels = Map.of("innerDiameter", "内径", "innerHeight", "内高",
                "wallThickness", "壁厚", "bottomThickness", "底厚", "rHeight", "R高度",
                "outerDiameter", "外径", "height", "高度", "diameter", "直径", "length", "长度");
        return DIMENSIONS.getOrDefault(row.getProductType()==null?row.getCategory():ProductTypes.geometry(row.getProductType()), List.of()).stream()
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
            jdbc.update("INSERT INTO metal_product_process(product_id,process_id,sequence_no,theoretical_loss,measurement_mode,loss_unit) VALUES(?,?,?,?,?,?)", row.getId(), step.getProcessId(), ++order, step.getLoss(),step.getMeasurementMode(),step.getLossUnit());
    }

    /**
     * 为查询结果批量补充关联资料。
     */
    @Override
    protected void hydrate(List<ProductRecord> rows) {
        if (rows.isEmpty()) return;
        rows.forEach(r -> r.setSpecification(specification(r)));
        Map<Long, ProductRecord> indexed = new HashMap<>();
        rows.forEach(r -> {
            r.setProcesses(new ArrayList<>());
            indexed.put(r.getId(), r);
        });
        String placeholders = String.join(",", Collections.nCopies(rows.size(), "?"));
        jdbc.query("SELECT pp.product_id,pp.process_id,pp.theoretical_loss,pp.measurement_mode,pp.loss_unit,p.process_name FROM metal_product_process pp JOIN metal_process p ON p.process_id=pp.process_id WHERE pp.product_id IN (" + placeholders + ") ORDER BY pp.product_id,pp.sequence_no", (org.springframework.jdbc.core.RowCallbackHandler) r -> {
            ProductStep s = new ProductStep();
            s.setProcessId(r.getLong("process_id"));
            s.setLoss(r.getBigDecimal("theoretical_loss"));
            s.setLossUnit(r.getString("loss_unit"));
            s.setMeasurementMode(r.getString("measurement_mode"));
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
