package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.ruoyi.metal.catalog.WorkOrderRequest;
import com.ruoyi.common.exception.ServiceException;
import static org.junit.jupiter.api.Assertions.*;

class MeasurementSnapshotTest {
 private final Map<String,Object> step=Map.of("processId","30","measurementMode","WEIGHT","loss","9.123456","lossUnit","G_PER_PIECE","processRole","SMELTING");
 private final Map<String,Object> old=Map.of("id","10","productId","20","snapshot",Map.of("name","历史产品"),"plans",List.of(step));
 private WorkOrderRequest.Item input(Long id,long product,long process,String loss){
  var start=LocalDateTime.of(2026,11,3,9,0);
  return new WorkOrderRequest.Item(id,product,5,List.of(new WorkOrderRequest.Plan(process,start,start.plusHours(1),loss==null?null:new BigDecimal(loss))));
 }
 @Test void metadataAndScheduleEditsKeepTheOldUnitAndFullLossPrecision(){
  assertDoesNotThrow(()->WorkOrderService.validateRetainedItems(List.of(input(10L,20,30,"9.123456")),List.of(old),false));
  assertEquals("WEIGHT",step.get("measurementMode"));assertEquals("9.123456",step.get("loss"));
  assertFalse(MeasurementPolicy.current((Map<String,Object>)old.get("snapshot")));
 }
 @Test void productsCannotBeSwappedOnASavedOrder(){assertThrows(ServiceException.class,()->WorkOrderService.validateRetainedItems(List.of(input(10L,21,30,null)),List.of(old),false));}
 @Test void stepOrLossChangesRequireANewOrder(){
  assertThrows(ServiceException.class,()->WorkOrderService.validateRetainedItems(List.of(input(10L,20,31,null)),List.of(old),false));
  assertThrows(ServiceException.class,()->WorkOrderService.validateRetainedItems(List.of(input(10L,20,30,"9.12")),List.of(old),false));
 }
 @Test void addingOrRemovingItemsCannotReplaceSavedSnapshots(){
  assertThrows(ServiceException.class,()->WorkOrderService.validateRetainedItems(List.of(),List.of(old),false));
  assertThrows(ServiceException.class,()->WorkOrderService.validateRetainedItems(List.of(input(null,20,30,null)),List.of(old),false));
 }
}
