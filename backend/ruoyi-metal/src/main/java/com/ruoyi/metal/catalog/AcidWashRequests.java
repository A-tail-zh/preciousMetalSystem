package com.ruoyi.metal.catalog;

import java.time.LocalDate;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.ruoyi.metal.catalog.WarehouseRequests.Line;

public final class AcidWashRequests {
 private AcidWashRequests(){}
 public record Group(@NotNull @Positive Long warehouseId,@NotEmpty @Size(max=1000) List<@NotNull @Valid Line> lines){}
 public record Start(@NotBlank String requestId,@NotNull @Positive Long factoryId,@NotNull @Positive Long processId,
  @NotNull @Min(0) Integer processVersion,@NotNull LocalDate orderDate,
  @NotEmpty @Size(max=20) List<@NotNull @Positive Long> operatorIds,@NotNull @Positive Long keeperId,
  @Size(max=500) String reason,@NotEmpty @Size(max=1000) List<@NotNull @Valid Group> groups){}
 public record Finish(@NotBlank String requestId,@NotNull @Min(0) Integer version,@NotNull LocalDate orderDate,
  @NotEmpty @Size(max=20) List<@NotNull @Positive Long> operatorIds,@NotNull @Positive Long keeperId,
  @NotNull Boolean physicalChecked,@Size(max=500) String reason,
  @NotEmpty @Size(max=1000) List<@NotNull @Valid Group> outputs,
  @NotNull @Size(max=1000) List<@NotNull @Valid Group> returns){}
}
