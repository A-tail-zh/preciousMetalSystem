package com.ruoyi.metal.catalog;
import java.time.LocalDate;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.ruoyi.metal.catalog.WarehouseRequests.Line;
public final class InboundSubmissionRequests {
 private InboundSubmissionRequests(){}
 public record People(@NotNull @Size(min=1,max=20) List<@NotNull Long> operatorIds,@NotNull Long keeperId,Long inspectorId,Long reviewerId){}
 public record Group(@NotNull Long warehouseId,@Valid People peopleOverride,@NotNull @Size(min=1,max=1000) List<@NotNull @Valid Line> lines){}
 public record Request(@NotBlank String requestId,@NotNull Long factoryId,@NotBlank String type,@NotNull LocalDate orderDate,@NotNull @Size(min=1,max=20) List<@NotNull Long> operatorIds,@NotNull Long keeperId,Long inspectorId,Long reviewerId,Long workOrderId,Long itemId,Long processId,Long batchId,Integer batchVersion,Boolean settle,Integer completedQuantity,@NotNull @Size(min=1,max=1000) List<@NotNull @Valid Group> groups){}
}
