package com.ruoyi.metal.catalog;
import java.time.LocalDate;import java.util.List;
import jakarta.validation.Valid;import jakarta.validation.constraints.*;
import com.ruoyi.metal.catalog.WarehouseRequests.Line;
public final class OutboundSubmissionRequests {
 private OutboundSubmissionRequests(){}
 public record People(@Size(max=20) List<@NotNull @Positive Long> operatorIds,@NotNull @Positive Long recipientId,@NotNull @Positive Long keeperId,@Positive Long reviewerId){}
 public record Group(@NotNull @Positive Long warehouseId,@Valid People peopleOverride,@NotNull @Size(max=1000) List<@NotNull @Valid Line> lines){}
 public record Request(@NotBlank String requestId,@NotNull @Positive Long factoryId,@NotBlank String type,@NotNull LocalDate orderDate,@Size(max=20) List<@NotNull @Positive Long> operatorIds,@Positive Long recipientId,@Positive Long keeperId,@Positive Long reviewerId,@Positive Long workOrderId,@Positive Long itemId,@Positive Long processId,@Positive Long batchId,@Min(0) Integer batchVersion,@NotNull @Size(min=1,max=1000) List<@NotNull @Valid Group> groups,@Pattern(regexp="NEW|APPEND") String batchAction,@Min(0) @Max(1000000) Integer plannedQuantity){
  public Request(String requestId,Long factoryId,String type,LocalDate orderDate,List<Long> operatorIds,Long recipientId,Long keeperId,Long reviewerId,Long workOrderId,Long itemId,Long processId,Long batchId,Integer batchVersion,List<Group> groups){this(requestId,factoryId,type,orderDate,operatorIds,recipientId,keeperId,reviewerId,workOrderId,itemId,processId,batchId,batchVersion,groups,null,null);}
  @Override public String toString(){return "Request[requestId="+requestId+", factoryId="+factoryId+", type="+type+", orderDate="+orderDate+", operatorIds="+operatorIds+", recipientId="+recipientId+", keeperId="+keeperId+", reviewerId="+reviewerId+", workOrderId="+workOrderId+", itemId="+itemId+", processId="+processId+", batchId="+batchId+", batchVersion="+batchVersion+", groups="+groups+(batchAction==null&&plannedQuantity==null?"":", batchAction="+batchAction+", plannedQuantity="+plannedQuantity)+"]";}
 }
 public record Preview(@NotNull @Positive Long factoryId,@NotNull @Positive Long workOrderId,@NotNull @Positive Long itemId,@NotNull @Positive Long processId,@Positive Long batchId,@Min(0) Integer batchVersion,@NotNull @Size(min=1,max=1000) List<@NotNull @Valid Group> groups,@Pattern(regexp="NEW|APPEND") String batchAction,@Min(0) @Max(1000000) Integer plannedQuantity){
  public Preview(Long factoryId,Long workOrderId,Long itemId,Long processId,Long batchId,Integer batchVersion,List<Group> groups){this(factoryId,workOrderId,itemId,processId,batchId,batchVersion,groups,null,null);}
  public Request request(){return new Request(null,factoryId,"PRODUCTION_OUT",null,null,null,null,null,workOrderId,itemId,processId,batchId,batchVersion,groups,batchAction,plannedQuantity);}
 }
}
