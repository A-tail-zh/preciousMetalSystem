package com.ruoyi.metal.catalog;
import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
/**
 * 仓储业务请求模型，集中定义库房、库存、单据和加工批次的参数校验规则。
 */
public final class WarehouseRequests {
 private WarehouseRequests(){}
 public record Warehouse(Long id,Integer version,String requestId,@NotNull Long factoryId,@NotBlank @Size(max=100) String name,@Min(0) int sortOrder,@Pattern(regexp="0|1") String status){}
 public record Rule(Long id,Integer version,String requestId,@NotNull Long factoryId,@NotNull Long productId,@NotNull Long processId,@NotNull @DecimalMin("0") @Digits(integer=14,fraction=6) @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal tolerance,@Pattern(regexp="0|1") String status){}
 public record Batch(@NotBlank String requestId,@NotNull Long workOrderId,@NotNull Long itemId,@NotNull Long processId,@Min(1) @Max(1000000) int plannedQuantity){}
 public record Action(@NotNull @Min(0) Integer version,@Size(max=500) String reason,Integer completedQuantity){}
 public record Stock(Long id,Integer version,@Size(max=200) String specification,@Size(max=100) String organization,@DecimalMin("0") @DecimalMax("100") @Digits(integer=6,fraction=6) @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal purity){}
 public record Adjustment(@NotBlank String requestId,@NotNull Integer version,@NotNull Long operatorId,@NotNull Long keeperId,@NotNull @Digits(integer=14,fraction=6) @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal deltaWeight,@NotNull @Min(-1000000) @Max(1000000) Long deltaQuantity,@NotBlank @Size(max=500) String reason){}
 public record Order(@NotBlank String requestId,@NotNull Long factoryId,@NotNull Long warehouseId,@NotBlank String type,@NotNull LocalDate orderDate,Long operatorId,@Size(max=20) List<@NotNull Long> operatorIds,@NotNull Long keeperId,Long recipientId,Long inspectorId,Long reviewerId,Long workOrderId,Long batchId,Integer batchVersion,Boolean settle,Integer completedQuantity,@Size(max=500) String reason,@NotNull @Size(min=1,max=1000) List<@NotNull @Valid Line> lines,Long itemId,Long processId){}
 public record Line(@JsonFormat(shape=JsonFormat.Shape.STRING) Long inventoryId,@JsonFormat(shape=JsonFormat.Shape.STRING) Long sourceLineId,String category,@JsonFormat(shape=JsonFormat.Shape.STRING) Long referenceId,@Size(max=160) String batchNo,@NotNull @Digits(integer=14,fraction=6) @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal netWeight,Long quantity,@DecimalMin("0") @Digits(integer=14,fraction=6) @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal grossWeight,@DecimalMin("0") @Digits(integer=14,fraction=6) @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal impurity,@DecimalMin("0") @DecimalMax("100") @Digits(integer=6,fraction=6) @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal purity,@Size(max=100) String organization,@Size(max=200) String purpose,@Size(max=500) String remark){}
}
