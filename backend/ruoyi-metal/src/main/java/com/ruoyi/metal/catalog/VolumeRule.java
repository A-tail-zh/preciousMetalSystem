package com.ruoyi.metal.catalog;

import java.math.BigDecimal;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonFormat;

/** Shared geometry parameter, independent of metal density and prices. */
public record VolumeRule(
    @NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=5,fraction=15)
    @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal piValue,
    @NotNull @Min(0) Integer version,
    @DecimalMin(value="0",inclusive=false) @Digits(integer=14,fraction=6)
    @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal curvedBottomFactor) {
    // Older callers only update pi; an absent factor preserves the locked database value.
    public VolumeRule(BigDecimal piValue,Integer version){this(piValue,version,null);}
}
