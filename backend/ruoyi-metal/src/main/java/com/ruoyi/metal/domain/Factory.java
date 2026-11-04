package com.ruoyi.metal.domain;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;

/** Factory response. Decimal and identifier values remain strings on the wire. */
public class Factory {
    private String factoryId;
    private String factoryName;
    private String area;
    private String address;
    private String introduction;
    private Integer version;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;
    private List<ProductionLine> productionLines = new ArrayList<>();
    public String getFactoryId() { return factoryId; }
    public void setFactoryId(String v) { factoryId = v; }
    public String getFactoryName() { return factoryName; }
    public void setFactoryName(String v) { factoryName = v; }
    public String getArea() { return area; }
    public void setArea(String v) { area = v; }
    public String getAddress() { return address; }
    public void setAddress(String v) { address = v; }
    public String getIntroduction() { return introduction; }
    public void setIntroduction(String v) { introduction = v; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer v) { version = v; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date v) { updateTime = v; }
    public List<ProductionLine> getProductionLines() { return productionLines; }
    public void setProductionLines(List<ProductionLine> v) { productionLines = v; }
    public List<String> getProductionLineIds() {
        return productionLines.stream().map(ProductionLine::getProductionLineId).toList();
    }
}
