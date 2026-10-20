package com.ruoyi.metal.catalog;

import java.util.List;

/** Minimal immutable work-order snapshot for authorized warehouse entry. */
public record WarehouseWorkOrderContext(String workOrderId,String factoryId,String customer,List<Item> items) {
 public record Snapshot(String code,String name,String specification) {}
 public record Plan(String processId,String processName,int sequenceNo) {}
 public record Item(String id,String productId,Snapshot snapshot,List<Plan> plans) {}
}
