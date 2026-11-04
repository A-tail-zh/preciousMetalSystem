package com.ruoyi.metal.catalog;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Positive;

public class FactoryCatalogRecord extends MasterRecord {
    @Positive @JsonFormat(shape=JsonFormat.Shape.STRING)
    private Long factoryId;
    private String factoryName;
    public Long getFactoryId(){return factoryId;}
    public void setFactoryId(Long value){factoryId=value;}
    public String getFactoryName(){return factoryName;}
    public void setFactoryName(String value){factoryName=value;}
}
