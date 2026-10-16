package com.ruoyi.metal.catalog;

import java.util.*;
import java.math.BigDecimal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 产品数据模型，承载业务字段及适用的参数校验规则。
 */
public class ProductRecord extends MasterRecord {
    @NotBlank
    @Size(max = 64)
    private String code;
    @NotBlank
    @Size(max = 100)
    private String name;
    @NotBlank
    private String category;
    @Size(max = 200)
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private String specification;
    @NotNull
    private Map<String, String> dimensions;
    @DecimalMin("0")
    @Digits(integer = 14, fraction = 6)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal volume;
    @DecimalMin("0")
    @DecimalMax("100")
    @Digits(integer = 14, fraction = 6)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal purity;
    @DecimalMin("0")
    @Digits(integer = 14, fraction = 6)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal weight;
    @NotNull
    @Valid
    private List<ProductStep> processes = new ArrayList<>();

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

    public String getCategory() {
        return category;
    }

    public void setCategory(String v) {
        category = v == null ? null : v.strip();
    }

    public String getSpecification() {
        return specification;
    }

    public void setSpecification(String v) {
        specification = v == null ? null : v.strip();
    }

    public Map<String, String> getDimensions() {
        return dimensions;
    }

    public void setDimensions(Map<String, String> v) {
        dimensions = v;
    }

    public BigDecimal getVolume() {
        return volume;
    }

    public void setVolume(BigDecimal v) {
        volume = v;
    }

    public BigDecimal getPurity() {
        return purity;
    }

    public void setPurity(BigDecimal v) {
        purity = v;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public void setWeight(BigDecimal v) {
        weight = v;
    }

    public List<ProductStep> getProcesses() {
        return processes;
    }

    public void setProcesses(List<ProductStep> v) {
        processes = v;
    }
}
