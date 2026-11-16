package com.ruoyi.metal.catalog;

import java.util.*;
import java.math.BigDecimal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 产线数据模型，承载业务字段及适用的参数校验规则。
 */
public class LineRecord extends MasterRecord {
    @NotBlank
    @Size(max = 64)
    private String code;
    @NotBlank
    @Size(max = 100)
    private String name;
    @Positive
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long factoryId;
    @NotNull
    @tools.jackson.databind.annotation.JsonSerialize(contentUsing = tools.jackson.databind.ser.std.ToStringSerializer.class)
    private List<@NotNull @Positive Long> productIds = new ArrayList<>();
    private List<String> productNames = new ArrayList<>();
    private String factoryName;

    public String getCode() {
        return code;
    }

    public void setCode(String v) {
        code = v == null ? null : v.strip();
    }

    public String getName() {
        return name;
    }

    public void setName(String v) {
        name = v == null ? null : v.strip();
    }

    public Long getFactoryId() {
        return factoryId;
    }

    public void setFactoryId(Long v) {
        factoryId = v;
    }

    public List<Long> getProductIds() {
        return productIds;
    }

    public void setProductIds(List<Long> v) {
        productIds = v;
    }

    public List<String> getProductNames() {
        return productNames;
    }

    public void setProductNames(List<String> v) {
        productNames = v;
    }

    public String getFactoryName() {
        return factoryName;
    }

    public void setFactoryName(String v) {
        factoryName = v == null ? null : v.strip();
    }
}
