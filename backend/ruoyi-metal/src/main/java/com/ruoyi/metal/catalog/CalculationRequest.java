package com.ruoyi.metal.catalog;

import java.util.Map;

import jakarta.validation.constraints.*;

/**
 * 产品体积计算数据模型，承载业务字段及适用的参数校验规则。
 */
public record CalculationRequest(@NotBlank String category, @NotNull Map<String, String> dimensions,
                                 @NotNull @Min(1) @Max(1000000) Integer quantity) {
}
