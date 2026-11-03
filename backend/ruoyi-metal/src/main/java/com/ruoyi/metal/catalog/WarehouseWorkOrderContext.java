package com.ruoyi.metal.catalog;

import java.util.List;

/** Minimal immutable work-order snapshot for authorized warehouse entry. */
public record WarehouseWorkOrderContext(String workOrderId,String factoryId,String customer,List<Item> items,String taskType,int processingPolicyVersion) {
 public WarehouseWorkOrderContext(String workOrderId,String factoryId,String customer,List<Item> items){this(workOrderId,factoryId,customer,items,"PRODUCTION",1);}
 public record Snapshot(String code,String name,String specification,String metalTypeId) {public Snapshot(String code,String name,String specification){this(code,name,specification,null);}}
 public record Plan(String processId,String processName,int sequenceNo,String measurementMode,boolean weightStageClosed,String processRole,String outputStage) {
  public Plan(String processId,String processName,int sequenceNo,String measurementMode,boolean weightStageClosed){this(processId,processName,sequenceNo,measurementMode,weightStageClosed,null,null);}
  public Plan(String processId,String processName,int sequenceNo){this(processId,processName,sequenceNo,"PIECE",false);}
 }
 public record Item(String id,String productId,Snapshot snapshot,List<Plan> plans,String materialId,String plannedInputWeight,String metalTypeId) {public Item(String id,String productId,Snapshot snapshot,List<Plan> plans){this(id,productId,snapshot,plans,null,null,null);}}
}
