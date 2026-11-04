package com.ruoyi.metal.service;

import java.util.*;
import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.metal.catalog.QuoteRule;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;

/**
 * 报价规则业务服务，负责业务校验、数据处理和相关持久化操作。
 */
@Service
public class QuoteRuleService {
    private final JdbcTemplate jdbc;
    private final com.ruoyi.metal.security.FactoryScope scope;

    public QuoteRuleService(JdbcTemplate jdbc) { this(jdbc,new com.ruoyi.metal.security.FactoryScope(jdbc,null)); }
    @org.springframework.beans.factory.annotation.Autowired
    public QuoteRuleService(JdbcTemplate jdbc,com.ruoyi.metal.security.FactoryScope scope) { this.jdbc=jdbc;this.scope=scope; }

    /**
     * 读取当前报价规则。
     */
    public QuoteRule get() {
        return jdbc.queryForObject("SELECT base_price,pi_value,version FROM metal_quote_rule WHERE rule_id=1", (r, n) -> {
            QuoteRule q = new QuoteRule();
            q.setBasePrice(r.getBigDecimal(1));
            q.setPiValue(r.getBigDecimal(2));
            q.setVersion(r.getInt(3));
            return q;
        });
    }

    /**
     * 校验版本后保存报价规则。
     */
    @Transactional
    public void update(QuoteRule value) {
        throw new ServiceException("请按金属类型保存密度及加工单价配置");
    }

    public List<Map<String,Object>> metals(){return metals(null);}
    public List<Map<String,Object>> metals(Long requested){
        Long factory=scope.resolveFactory(requested);
        return jdbc.queryForList("SELECT CAST(metal_type_id AS CHAR) id,name,CAST(factory_id AS CHAR) factoryId FROM metal_type WHERE factory_id=? ORDER BY metal_type_id",factory);
    }
    public String metalName(Long id) {
        scope.catalogFactory("metal",id);
        var rows=jdbc.queryForList("SELECT name FROM metal_type WHERE metal_type_id=?",id);
        if(rows.size()!=1) throw new ServiceException("请选择有效金属类型");
        return rows.get(0).get("name").toString();
    }
    @Transactional(rollbackFor=Exception.class)
    public void removeMetal(Long id) {
        Long factory=scope.catalogFactory("metal",id);scope.lockFactories(List.of(factory));
        if(jdbc.queryForList("SELECT metal_type_id FROM metal_type WHERE metal_type_id=? AND factory_id=? FOR UPDATE",id,factory).isEmpty())throw new ServiceException("金属类型不存在或已删除");
        // Include deleted master records: their IDs may still be used by historical documents.
        for(String table:List.of("metal_product","metal_material","metal_type_quote_rule","metal_process_batch"))
            if(jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE metal_type_id=?",Integer.class,id)>0)throw new ServiceException("金属类型已被产品、物料、报价或加工记录引用，不能删除");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM metal_inventory WHERE metal_type_id_snapshot=?",Integer.class,id)>0
            ||jdbc.queryForObject("SELECT COUNT(*) FROM metal_saved_quotation WHERE JSON_CONTAINS(JSON_EXTRACT(item_snapshot,'$[*].metalTypeId'),JSON_QUOTE(?)) OR JSON_CONTAINS(JSON_EXTRACT(item_snapshot,'$[*].metalTypeId'),CAST(? AS JSON))",Integer.class,id.toString(),id.toString())>0)
            throw new ServiceException("金属类型已被库存或历史报价引用，不能删除");
        try{if(jdbc.update("DELETE FROM metal_type WHERE metal_type_id=? AND factory_id=?",id,factory)!=1)throw new ServiceException("金属类型不存在或已删除");}
        catch(org.springframework.dao.DataIntegrityViolationException e){throw new ServiceException("金属类型已被引用，不能删除");}
    }
    @Transactional
    public String addMetal(String name){return addMetal(name,null);}
    @Transactional
    public String addMetal(String name,Long requested) {
        Long factory=scope.resolveFactory(requested);scope.lockFactories(List.of(factory));
        if(!SecurityUtils.isAdmin(SecurityUtils.getUserId()) && !SecurityUtils.hasPermi("metal:material:add") && !SecurityUtils.hasPermi("metal:material:edit") && !SecurityUtils.hasPermi("metal:product:add") && !SecurityUtils.hasPermi("metal:product:edit") && !SecurityUtils.hasPermi("metal:quote-rule:edit")) throw new ServiceException("没有新增金属类型权限",403);
        if(name==null || name.isBlank() || name.strip().length()>100) throw new ServiceException("金属类型名称须为1至100个字符");
        String clean=name.strip();
        try { jdbc.update("INSERT INTO metal_type(factory_id,name,create_by) VALUES(?,?,?)",factory,clean,SecurityUtils.getUsername()); }
        catch(org.springframework.dao.DuplicateKeyException e){throw new ServiceException("金属类型已存在，请直接选择",409);}
        return jdbc.queryForObject("SELECT CAST(metal_type_id AS CHAR) FROM metal_type WHERE factory_id=? AND name=?",String.class,factory,clean);
    }
    private Long ruleFactory(Long metalTypeId,Long requested){
        Long factory=metalTypeId==null?scope.resolveFactory(requested):scope.catalogFactory("metal",metalTypeId);
        if(requested!=null&&!requested.equals(factory))throw new ServiceException("金属不属于当前工厂",403);
        return factory;
    }
    public List<Map<String,Object>> rules(Long metalTypeId){return rules(metalTypeId,null);}
    public List<Map<String,Object>> rules(Long metalTypeId,Long requested){
        Long factory=ruleFactory(metalTypeId,requested);
        return jdbc.queryForList("SELECT CAST(m.metal_type_id AS CHAR) metalTypeId,m.name metalTypeName,CAST(m.factory_id AS CHAR) factoryId,CASE WHEN r.del_flag='0' THEN CAST(r.density AS CHAR) END density,CAST(g.pi_value AS CHAR) piValue,g.version piVersion,CASE WHEN r.del_flag='0' THEN CAST(r.powder_price AS CHAR) END powderPrice,CASE WHEN r.del_flag='0' THEN CAST(r.non_powder_price AS CHAR) END nonPowderPrice,CASE WHEN r.del_flag='0' THEN CAST(r.shortage_price AS CHAR) END shortagePrice,CASE WHEN r.del_flag='0' THEN CAST(r.loss_price AS CHAR) END lossPrice,COALESCE(r.version,0) version,CASE WHEN r.del_flag='0' THEN 1 ELSE 0 END configured,r.update_time updateTime FROM metal_type m LEFT JOIN metal_type_quote_rule r ON r.metal_type_id=m.metal_type_id JOIN metal_factory_volume_rule g ON g.factory_id=m.factory_id WHERE m.factory_id=?"+(metalTypeId==null?"":" AND m.metal_type_id=?")+" ORDER BY m.metal_type_id",metalTypeId==null?new Object[]{factory}:new Object[]{factory,metalTypeId});
    }
    public List<Map<String,Object>> productRules(Long metalTypeId){return productRules(metalTypeId,null);}
    public List<Map<String,Object>> productRules(Long metalTypeId,Long requested){
        Long factory=ruleFactory(metalTypeId,requested);
        return jdbc.queryForList("SELECT CAST(m.metal_type_id AS CHAR) metalTypeId,m.name metalTypeName,CAST(m.factory_id AS CHAR) factoryId,CASE WHEN r.del_flag='0' THEN CAST(r.density AS CHAR) END density,COALESCE(r.version,0) version FROM metal_type m LEFT JOIN metal_type_quote_rule r ON r.metal_type_id=m.metal_type_id WHERE m.factory_id=?"+(metalTypeId==null?"":" AND m.metal_type_id=?")+" ORDER BY m.metal_type_id",metalTypeId==null?new Object[]{factory}:new Object[]{factory,metalTypeId});
    }
    public Map<String,Object> productRule(Long id) {
        if(id==null||id<=0)throw new ServiceException("请选择有效金属类型");
        var rows=productRules(id);
        if(rows.size()!=1)throw new ServiceException("请选择有效金属类型");
        return rows.get(0);
    }
    public Map<String,Object> rule(Long id) {
        metalName(id);
        return rules(id).get(0);
    }
    public java.math.BigDecimal pi(Long id) {
        return new java.math.BigDecimal(volumeRuleForMetal(id).get("piValue").toString());
    }
    public Map<String,Object> volumeRule(){return volumeRule(null);}
    public Map<String,Object> volumeRuleForMetal(Long id){return volumeRule(scope.catalogFactory("metal",id));}
    public Map<String,Object> volumeRule(Long requested){
        Long factory=scope.resolveFactory(requested);
        return jdbc.queryForMap("SELECT CAST(factory_id AS CHAR) factoryId,CAST(pi_value AS CHAR) piValue,CAST(curved_bottom_factor AS CHAR) curvedBottomFactor,version,update_time updateTime FROM metal_factory_volume_rule WHERE factory_id=?",factory);
    }
    @Transactional(rollbackFor=Exception.class)
    public void updateVolumeRule(com.ruoyi.metal.catalog.VolumeRule value){updateVolumeRule(value,null);}
    @Transactional(rollbackFor=Exception.class)
    public void updateVolumeRule(com.ruoyi.metal.catalog.VolumeRule value,Long requested) {
        if(!SecurityUtils.isAdmin(SecurityUtils.getUserId())&&!SecurityUtils.hasPermi("metal:quote-rule:edit"))throw new ServiceException("没有报价规则修改权限",403);
        Long factory=scope.resolveFactory(requested);scope.lockFactories(List.of(factory));
        var old=jdbc.queryForMap("SELECT version,curved_bottom_factor FROM metal_factory_volume_rule WHERE factory_id=? FOR UPDATE",factory);
        if(((Number)old.get("version")).intValue()!=value.version())throw new ServiceException("公共体积参数已被修改，请刷新后重试",409);
        var pi=BusinessDecimal.positive(value.piValue(),"π值");
        if(pi.precision()-pi.scale()>5)throw new ServiceException("π值四舍五入后超过5位整数范围");
        var factor=BusinessDecimal.positive(value.curvedBottomFactor()==null?(java.math.BigDecimal)old.get("curved_bottom_factor"):value.curvedBottomFactor(),"弧底系数");
        jdbc.update("UPDATE metal_factory_volume_rule SET pi_value=?,curved_bottom_factor=?,version=version+1,update_by=?,update_time=NOW() WHERE factory_id=?",pi,factor,SecurityUtils.getUsername(),factory);
    }
    @Transactional(rollbackFor=Exception.class)
    public void update(com.ruoyi.metal.catalog.MetalQuoteRule value) {
        if(!SecurityUtils.isAdmin(SecurityUtils.getUserId())&&!SecurityUtils.hasPermi("metal:quote-rule:edit")) throw new ServiceException("没有报价规则修改权限",403);
        metalName(value.metalTypeId());
        jdbc.queryForList("SELECT metal_type_id FROM metal_type WHERE metal_type_id=? FOR UPDATE",value.metalTypeId());
        var rows=jdbc.queryForList("SELECT version FROM metal_type_quote_rule WHERE metal_type_id=? FOR UPDATE",value.metalTypeId());
        if((rows.isEmpty()?0:((Number)rows.get(0).get("version")).intValue())!=value.version()) throw new ServiceException("配置已被修改，请刷新后重试",409);
        var density=BusinessDecimal.positive(value.density(),"密度");
        var prices=List.of(value.powderPrice(),value.nonPowderPrice(),value.shortagePrice(),value.lossPrice());
        if(prices.stream().anyMatch(p->p.signum()<0))throw new ServiceException("单价不能小于0");
        jdbc.update("INSERT INTO metal_type_quote_rule(metal_type_id,density,pi_value,powder_price,non_powder_price,shortage_price,loss_price,version,update_by) VALUES(?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE del_flag='0',density=VALUES(density),powder_price=VALUES(powder_price),non_powder_price=VALUES(non_powder_price),shortage_price=VALUES(shortage_price),loss_price=VALUES(loss_price),version=VALUES(version),update_by=VALUES(update_by),update_time=NOW()",value.metalTypeId(),density,pi(value.metalTypeId()),BusinessDecimal.round(value.powderPrice()),BusinessDecimal.round(value.nonPowderPrice()),BusinessDecimal.round(value.shortagePrice()),BusinessDecimal.round(value.lossPrice()),value.version()+1,SecurityUtils.getUsername());
    }

    @Transactional(rollbackFor=Exception.class)
    public void remove(Long metalTypeId,Integer version) {
        if(!SecurityUtils.isAdmin(SecurityUtils.getUserId())&&!SecurityUtils.hasPermi("metal:quote-rule:edit"))throw new ServiceException("没有报价规则修改权限",403);
        if(metalTypeId==null||metalTypeId<=0||version==null||version<0)throw new ServiceException("金属类型或版本无效");
        metalName(metalTypeId);
        jdbc.queryForList("SELECT metal_type_id FROM metal_type WHERE metal_type_id=? FOR UPDATE",metalTypeId);
        var rows=jdbc.queryForList("SELECT version,del_flag FROM metal_type_quote_rule WHERE metal_type_id=? FOR UPDATE",metalTypeId);
        if(rows.isEmpty()||((Number)rows.get(0).get("version")).intValue()!=version||!"0".equals(rows.get(0).get("del_flag")))throw new ServiceException("配置已被修改或删除，请刷新后重试",409);
        jdbc.update("UPDATE metal_type_quote_rule SET del_flag='2',version=version+1,update_by=?,update_time=NOW() WHERE metal_type_id=?",SecurityUtils.getUsername(),metalTypeId);
    }

    /**
     * 使用当前报价规则计算产品体积并返回结果。
     */
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> calculate(com.ruoyi.metal.catalog.CalculationRequest input,Long factory){
        scope.requireCatalog("metal",input.metalTypeId(),scope.resolveFactory(factory));
        return calculate(input);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String, Object> calculate(com.ruoyi.metal.catalog.CalculationRequest input) {
        if(input.quantity()==null||input.quantity()<1||input.quantity()>1000000)throw new ServiceException("商品数量须为1～1000000的整数");
        if(input.processingType()==null&&(input.powderWeight()!=null||input.nonPowderWeight()!=null||input.lossWeight()!=null))throw new ServiceException("请选择加工类型");
        if(input.processingType()!=null)processingName(input.processingType());
        if(input.metalTypeId()==null)throw new ServiceException("请选择金属类型");
        String shape=ProductTypes.require(input.productType());
        String name=metalName(input.metalTypeId());
        if(input.category()!=null&&!input.category().equals(name+shape))throw new ServiceException("金属、产品类型和原类别不一致");
        var rule=rule(input.metalTypeId());var geometryRule=volumeRuleForMetal(input.metalTypeId());
        var dimensions=ProductGeometry.normalize(shape,input.dimensions());
        var volume=ProductGeometry.volume(shape,dimensions,ProductGeometry.parameter(geometryRule,"piValue"),ProductGeometry.parameter(geometryRule,"curvedBottomFactor"));
        if(volume==null||rule.get("density")==null)throw new ServiceException("请先配置金属密度及该类型所需的公共计算参数");
        if(new BigDecimal(rule.get("density").toString()).signum()<=0)throw new ServiceException("请先配置大于0的金属密度");
        var weight=ProductGeometry.weight(volume,rule.get("density"));
        var totalWeight=BusinessDecimal.round(weight.multiply(BigDecimal.valueOf(input.quantity())));
        var result=new LinkedHashMap<String,Object>();
        result.put("pricingEnabled",false);result.put("metalTypeId",input.metalTypeId().toString());result.put("metalTypeName",name);result.put("productType",shape);result.put("category",name+shape);result.put("quantity",input.quantity());result.put("dimensions",dimensions);
        result.put("volume",volume.toPlainString());result.put("unitWeight",weight.toPlainString());result.put("totalWeight",totalWeight.toPlainString());result.put("rule",rule);result.put("volumeRule",geometryRule);result.put("calculationVersion",2);
        if(input.processingType()!=null){
            var loss=quotationWeight(input.lossWeight(),"损失重量");
            BigDecimal powderAmount=new BigDecimal("0.00"),nonPowderAmount=powderAmount,productAmount=powderAmount,lossAmount;
            if("SHORTAGE_CUSTOM".equals(input.processingType())){
                if(input.powderWeight()!=null||input.nonPowderWeight()!=null)throw new ServiceException("欠料定制无需填写来料粉料或非粉料重量");
                var price=quotationPrice(rule,"shortagePrice","欠料加工单价");
                productAmount=BusinessDecimal.round(totalWeight.multiply(price));
                lossAmount=BusinessDecimal.round(loss.multiply(price));
            }else{
                var powder=quotationWeight(input.powderWeight(),"来料粉料重量");
                var nonPowder=quotationWeight(input.nonPowderWeight(),"来料非粉料重量");
                powderAmount=BusinessDecimal.round(powder.multiply(quotationPrice(rule,"powderPrice","粉料加工单价")));
                nonPowderAmount=BusinessDecimal.round(nonPowder.multiply(quotationPrice(rule,"nonPowderPrice","非粉料加工单价")));
                lossAmount=BusinessDecimal.round(loss.multiply(quotationPrice(rule,"lossPrice","损耗单价")));
                result.put("powderWeight",powder.toPlainString());result.put("nonPowderWeight",nonPowder.toPlainString());
            }
            result.put("pricingEnabled",true);result.put("processingType",input.processingType());result.put("processingTypeName",processingName(input.processingType()));result.put("lossWeight",loss.toPlainString());
            result.put("powderAmount",powderAmount.toPlainString());result.put("nonPowderAmount",nonPowderAmount.toPlainString());result.put("productAmount",productAmount.toPlainString());result.put("lossAmount",lossAmount.toPlainString());
            result.put("totalPrice",BusinessDecimal.round(powderAmount.add(nonPowderAmount).add(productAmount).add(lossAmount)).toPlainString());
        }
        return result;
    }

    private static String processingName(String type){
        return switch(type){case "MATERIAL_CUSTOM"->"来料定制";case "DIRECT_CUSTOM"->"直接定制";case "SHORTAGE_CUSTOM"->"欠料定制";default->throw new ServiceException("请选择有效加工类型");};
    }
    private static BigDecimal quotationWeight(String value,String label){
        if(value==null)throw new ServiceException("请输入"+label);
        if(!value.matches("[0-9]{1,14}(\\.[0-9]{1,6})?"))throw new ServiceException(label+"须为非负十进制数字，最多14位整数、6位小数");
        return BusinessDecimal.round(new BigDecimal(value));
    }
    private static BigDecimal quotationPrice(Map<String,Object> rule,String key,String label){
        if(rule.get(key)==null)throw new ServiceException("请先配置所选金属的"+label);
        var value=new BigDecimal(rule.get(key).toString());
        if(value.signum()<0)throw new ServiceException(label+"不能小于0，请核对配置");
        return BusinessDecimal.round(value);
    }

    /**
     * 获取报价所需的基础资料和规则上下文。
     */
    public Map<String,Object> context(){return context(null);}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String, Object> context(Long requested) {
        Long factory=scope.resolveFactory(requested);
        List<Map<String, Object>> products = jdbc.queryForList("SELECT CAST(product_id AS CHAR) id,product_name name,product_category category,CAST(metal_type_id AS CHAR) metalTypeId,product_type productType,calculation_version calculationVersion,specification,dimensions FROM metal_product WHERE del_flag='0' AND factory_id=? ORDER BY product_id",factory);
        products.forEach(p -> p.put("dimensions", JSON.parseObject(p.get("dimensions").toString())));
        return Map.of("metalTypes",metals(factory),"rules",rules(null,factory),"productTypes",ProductTypes.ALL,"products",products,"calculationEnabled",true,"pricingEnabled",true,"volumeRule",volumeRule(factory),"processingTypes",List.of("MATERIAL_CUSTOM","DIRECT_CUSTOM","SHORTAGE_CUSTOM").stream().map(type->Map.of("value",type,"label",processingName(type))).toList());
    }
}
