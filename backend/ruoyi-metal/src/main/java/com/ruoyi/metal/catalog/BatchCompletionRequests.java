package com.ruoyi.metal.catalog;

import java.time.LocalDate;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.ruoyi.metal.catalog.WarehouseRequests.Line;

public final class BatchCompletionRequests {
 private BatchCompletionRequests(){}
 public record ReturnDocument(@NotBlank @Pattern(regexp="RAW_RETURN|SEMI_RETURN|PROCESS_RAW_RETURN") String type,
  @NotNull Long warehouseId,@NotNull LocalDate orderDate,@NotEmpty @Size(max=20) List<@NotNull Long> operatorIds,
  @NotNull Long keeperId,Long inspectorId,Long reviewerId,@NotBlank @Size(max=500) String reason,
  @NotEmpty @Size(max=1000) List<@NotNull @Valid Line> lines){}
 public record ReturnConfirmation(@NotBlank @Size(max=64) String requestId,@NotNull @Min(0) Integer version,
  @NotBlank @Pattern(regexp="NONE|RETURNED") String mode,@NotNull Boolean physicalChecked,
  @Size(max=500) String reason,@NotNull @Size(max=100) List<@NotNull @Valid ReturnDocument> documents){}
}
