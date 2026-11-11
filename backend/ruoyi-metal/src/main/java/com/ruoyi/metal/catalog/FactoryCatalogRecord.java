package com.ruoyi.metal.catalog;

import java.util.LinkedHashMap;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Positive;

public class FactoryCatalogRecord extends MasterRecord {
    @Positive @JsonFormat(shape=JsonFormat.Shape.STRING)
    private Long factoryId;
    private String factoryName;
    private Map<String, Object> storageConfiguration;
    public Long getFactoryId(){return factoryId;}
    public void setFactoryId(Long value){factoryId=value;}
    public String getFactoryName(){return factoryName;}
    public void setFactoryName(String value){factoryName=value;}
    public Map<String, Object> getStorageConfiguration(){return storageConfiguration;}
    public <T> void setStorageConfiguration(Map<String, T> value){storageConfiguration = value == null ? null : new LinkedHashMap<String, Object>(value);}
}
