package com.ruoyi.metal.catalog;
import java.time.LocalDate;import java.util.List;
import jakarta.validation.Valid;import jakarta.validation.constraints.*;
import com.ruoyi.metal.catalog.WarehouseRequests.Line;
public final class OutboundSubmissionRequests {
 private OutboundSubmissionRequests(){}
 public record People(@Size(max=20) List<@NotNull @Positive Long> operatorIds,@NotNull @Positive Long recipientId,@NotNull @Positive Long keeperId,@Positive Long reviewerId){}
 public record Group(@NotNull @Positive Long warehouseId,@Valid People peopleOverride,@NotNull @Size(max=1000) List<@NotNull @Valid Line> lines){}
 public record Request(@NotBlank String requestId,@NotNull @Positive Long factoryId,@NotBlank String type,@NotNull LocalDate orderDate,@Size(max=20) List<@NotNull @Positive Long> operatorIds,@Positive Long recipientId,@Positive Long keeperId,@Positive Long reviewerId,@Positive Long workOrderId,@Positive Long itemId,@Positive Long processId,@Positive Long batchId,@Min(0) Integer batchVersion,@NotNull @Size(min=1,max=1000) List<@NotNull @Valid Group> groups){}
 public record Preview(@NotNull @Positive Long factoryId,@NotNull @Positive Long workOrderId,@NotNull @Positive Long itemId,@NotNull @Positive Long processId,@Positive Long batchId,@Min(0) Integer batchVersion,@NotNull @Size(min=1,max=1000) List<@NotNull @Valid Group> groups){public Request request(){return new Request(null,factoryId,"PRODUCTION_OUT",null,null,null,null,null,workOrderId,itemId,processId,batchId,batchVersion,groups);}}
}
