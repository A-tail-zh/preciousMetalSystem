package com.ruoyi.metal.catalog;

import java.math.BigDecimal;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonFormat;

public record MetalQuoteRule(
    @NotNull @Positive Long metalTypeId,
    @NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=14,fraction=6) @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal density,
    @NotNull @DecimalMin("0") @Digits(integer=14,fraction=6) @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal powderPrice,
    @NotNull @DecimalMin("0") @Digits(integer=14,fraction=6) @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal nonPowderPrice,
    @NotNull @DecimalMin("0") @Digits(integer=14,fraction=6) @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal shortagePrice,
    @NotNull @DecimalMin("0") @Digits(integer=14,fraction=6) @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal lossPrice,
    @NotNull @Min(0) Integer version) {}
