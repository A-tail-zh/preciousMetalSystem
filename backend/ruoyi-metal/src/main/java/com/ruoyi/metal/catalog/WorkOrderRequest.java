package com.ruoyi.metal.catalog;

import java.util.*;
import java.time.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 生产工单数据模型，承载业务字段及适用的参数校验规则。
 */
public record WorkOrderRequest(
        @Positive Long id, @Min(0) Integer version,
        @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String requestId,
        @NotBlank @Size(max = 100) String customer, @NotNull @Positive Long factoryId,
        @NotNull @Positive Long productionLineId, @NotNull @Positive Long ownerId,
        @NotNull @JsonFormat(pattern = "yyyy-MM-dd") LocalDate orderDate,
        @NotNull @Size(min = 1, max = 100) List<@NotNull @Valid Item> items
) {
    public record Item(@Positive Long id, @NotNull @Positive Long productId,
                       @NotNull @Min(1) @Max(1000000) Integer quantity,
                       @NotNull @Size(min = 1, max = 100) List<@NotNull @Valid Plan> plans) {
    }

    public record Plan(@NotNull @Positive Long processId,
                       @NotNull @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
                       @NotNull @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
    }

    public record Action(@NotNull @Min(0) Integer version, @Size(max = 500) String reason) {
    }
}
