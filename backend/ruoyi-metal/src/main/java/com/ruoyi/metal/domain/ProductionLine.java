package com.ruoyi.metal.domain;

/**
 * 产线数据模型，承载业务字段及适用的参数校验规则。
 */
public class ProductionLine {
    private String productionLineId;
    private String productionLineName;
    private String factoryId;
    public String getProductionLineId() { return productionLineId; }
    public void setProductionLineId(String v) { productionLineId = v; }
    public String getProductionLineName() { return productionLineName; }
    public void setProductionLineName(String v) { productionLineName = v; }
    public String getFactoryId() { return factoryId; }
    public void setFactoryId(String v) { factoryId = v; }
}
