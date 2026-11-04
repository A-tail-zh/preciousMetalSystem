package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.lang.reflect.*;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ruoyi.metal.catalog.WorkOrderRequest;
import com.ruoyi.common.exception.ServiceException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkOrderRawRequestTest {
 @Test void legacyNestedFingerprintRemainsByteCompatible(){
  var plan=new WorkOrderRequest.Plan(3L,LocalDateTime.of(2026,11,3,9,0),LocalDateTime.of(2026,11,3,10,0));
  var item=new WorkOrderRequest.Item(null,2L,4,List.of(plan));
  var request=new WorkOrderRequest(null,null,"uuid","客户",1L,2L,3L,LocalDate.of(2026,11,3),List.of(item));
  assertEquals("WorkOrderRequest[id=null, version=null, requestId=uuid, customer=客户, factoryId=1, productionLineId=2, ownerId=3, orderDate=2026-11-03, items=[Item[id=null, productId=2, quantity=4, plans=[Plan[processId=3, startTime=2026-11-03T09:00, endTime=2026-11-03T10:00]]]]]",request.fingerprintInput());
 }
 @Test void rawItemHasNoVirtualProductOrPieces(){
  assertDoesNotThrow(()->WorkOrderService.validateItem(new WorkOrderRequest.Item(null,null,0,List.of(),2L,BigDecimal.TEN),true));
  assertThrows(ServiceException.class,()->WorkOrderService.validateItem(new WorkOrderRequest.Item(null,3L,1,List.of(),2L,BigDecimal.TEN),true));
  assertThrows(ServiceException.class,()->WorkOrderService.validateItem(new WorkOrderRequest.Item(null,null,0,List.of(),2L,new BigDecimal("0.001")),true));
 }
 @Test void productionRequiresProductAndPieces(){
  assertDoesNotThrow(()->WorkOrderService.validateItem(new WorkOrderRequest.Item(null,3L,1,List.of()),false));
  assertThrows(ServiceException.class,()->WorkOrderService.validateItem(new WorkOrderRequest.Item(null,null,0,List.of(),2L,BigDecimal.TEN),false));
 }
 @SuppressWarnings("unchecked")
 private Map<String,Object> customer(JdbcTemplate jdbc,Long customerId)throws Exception{
  var request=new WorkOrderRequest(null,null,"uuid","伪造客户",1L,2L,3L,LocalDate.of(2026,11,3),List.of(),customerId,null,"DIRECT","RAW_PROCESSING");
  var method=WorkOrderService.class.getDeclaredMethod("customer",WorkOrderRequest.class,Map.class);method.setAccessible(true);
  try{return (Map<String,Object>)method.invoke(new WorkOrderService(jdbc,null,null,null),request,null);}
  catch(InvocationTargetException e){throw (Exception)e.getCause();}
 }
 @Test void factoryRawTaskNeedsNoCustomerAndCannotSmuggleCustomerIdentity()throws Exception{
  var jdbc=mock(JdbcTemplate.class);
  assertEquals(Map.of("name","工厂自有"),customer(jdbc,null));
  assertThrows(ServiceException.class,()->customer(jdbc,9L));
  verifyNoInteractions(jdbc);
 }
}
