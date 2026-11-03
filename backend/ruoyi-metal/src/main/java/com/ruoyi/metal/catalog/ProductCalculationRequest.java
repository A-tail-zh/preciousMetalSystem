package com.ruoyi.metal.catalog;
import java.util.Map;
import jakarta.validation.constraints.*;
public record ProductCalculationRequest(@NotNull @Positive Long metalTypeId,@NotBlank String productType,@NotNull Map<String,String> dimensions) {}
