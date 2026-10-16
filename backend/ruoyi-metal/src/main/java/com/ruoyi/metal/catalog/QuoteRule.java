package com.ruoyi.metal.catalog;

import java.math.BigDecimal;

import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 报价规则数据模型，承载业务字段及适用的参数校验规则。
 */
public class QuoteRule {
    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    @Digits(integer = 14, fraction = 6)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal basePrice;
    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    @Digits(integer = 5, fraction = 15)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal piValue;
    @NotNull
    @Min(0)
    private Integer version;

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(BigDecimal v) {
        basePrice = v;
    }

    public BigDecimal getPiValue() {
        return piValue;
    }

    public void setPiValue(BigDecimal v) {
        piValue = v;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer v) {
        version = v;
    }
}
