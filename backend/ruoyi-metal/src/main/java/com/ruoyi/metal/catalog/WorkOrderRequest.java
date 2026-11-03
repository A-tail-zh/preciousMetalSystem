package com.ruoyi.metal.catalog;

import java.util.*;
import java.time.*;
import java.math.BigDecimal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 生产工单数据模型，承载业务字段及适用的参数校验规则。
 */
public record WorkOrderRequest(
        @Positive Long id, @Min(0) Integer version,
        @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String requestId,
        @Size(max = 100) String customer, @NotNull @Positive Long factoryId,
        @NotNull @Positive Long productionLineId, @NotNull @Positive Long ownerId,
        @NotNull @JsonFormat(pattern = "yyyy-MM-dd") LocalDate orderDate,
        @NotNull @Size(min = 1, max = 100) List<@NotNull @Valid Item> items,
        @Positive Long customerId, @Min(0) Integer customerVersion,
        @Pattern(regexp="MATERIAL|DIRECT|SHORTAGE",message="请选择有效加工类型") String processingType,
        @Pattern(regexp="PRODUCTION|RAW_PROCESSING",message="请选择有效任务类型") String taskType
) {
    public WorkOrderRequest(Long id,Integer version,String requestId,String customer,Long factoryId,Long productionLineId,Long ownerId,LocalDate orderDate,List<Item> items){
        this(id,version,requestId,customer,factoryId,productionLineId,ownerId,orderDate,items,null,null,null,null);
    }
    public WorkOrderRequest(Long id,Integer version,String requestId,String customer,Long factoryId,Long productionLineId,Long ownerId,LocalDate orderDate,List<Item> items,Long customerId,Integer customerVersion,String processingType){
        this(id,version,requestId,customer,factoryId,productionLineId,ownerId,orderDate,items,customerId,customerVersion,processingType,null);
    }
    public String fingerprintInput(){
        String value=toString();
        if(taskType==null)value=value.substring(0,value.lastIndexOf(", taskType="))+"]";
        return customerId==null&&customerVersion==null&&processingType==null&&taskType==null?value.substring(0,value.lastIndexOf(", customerId="))+"]":value;
    }
    public record Item(@Positive Long id, @Positive Long productId,
                       @Min(0) @Max(1000000) Integer quantity,
                       @NotNull @Size(min = 1, max = 100) List<@NotNull @Valid Plan> plans,
                       @Positive Long materialId, @DecimalMin(value="0",inclusive=false) BigDecimal plannedInputWeight) {
        public Item(Long id,Long productId,Integer quantity,List<Plan> plans){this(id,productId,quantity,plans,null,null);}
        @Override public String toString(){
            String value="Item[id="+id+", productId="+productId+", quantity="+quantity+", plans="+plans;
            return value+(materialId==null&&plannedInputWeight==null?"":", materialId="+materialId+", plannedInputWeight="+plannedInputWeight)+"]";
        }
    }

    public record Plan(@NotNull @Positive Long processId,
                       @NotNull @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
                       @NotNull @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
                       @DecimalMin("0") @DecimalMax("100") BigDecimal theoreticalLoss) {
        public Plan(Long processId,LocalDateTime startTime,LocalDateTime endTime){this(processId,startTime,endTime,null);}
        @Override public String toString(){return "Plan[processId="+processId+", startTime="+startTime+", endTime="+endTime+(theoreticalLoss==null?"":", theoreticalLoss="+theoreticalLoss)+"]";}
    }

    public record Action(@NotNull @Min(0) Integer version, @Size(max = 500) String reason) {
    }
}
