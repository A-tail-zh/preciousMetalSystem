package com.ruoyi.metal.service;

import com.ruoyi.metal.security.FactoryScope;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ProductionSourceRegressionTest {
 @Test void intermediateDispatchCountsReceiptAndHandoverLossAndReversalRemovesBoth() throws Exception {
  for(boolean reversed:List.of(false,true)){
   var jdbc=mock(JdbcTemplate.class);
   when(jdbc.queryForList(startsWith("SELECT ev.*,e.order_id,s.output_category"),eq(7L))).thenReturn(List.of(Map.of("event_type","INPUT","weight","3.00","stock_category","RAW","line_data","{\"netWeight\":\"3.00\"}")));
   when(jdbc.queryForObject(startsWith("SELECT COALESCE(SUM(st.net_weight)"),eq(BigDecimal.class),eq(7L))).thenReturn(new BigDecimal(reversed?"3.00":"2.00"));
   when(jdbc.queryForList(startsWith("SELECT CAST(e.result AS CHAR) result FROM metal_storage_event e JOIN metal_storage_entry"),eq(7L))).thenReturn(reversed?List.of():List.of(Map.of("result","{\"sentWeight\":\"1.00\",\"receivedWeight\":\"0.99\"}")));
   var method=ProductionExecutionService.class.getDeclaredMethod("orderBalance",long.class);method.setAccessible(true);
   var report=(Map<?,?>)method.invoke(new ProductionExecutionService(jdbc,mock(FactoryScope.class)),7L);
   assertEquals(reversed?"0.00":"0.99",report.get("intermediateDispatchWeight"));assertEquals(reversed?"0.00":"0.01",report.get("handoverLossWeight"));assertEquals("0.00",report.get("balanceDifferenceWeight"));
  }
 }
 @Test void crossOrderReturnsReduceNetTransferAndReissuedClonesAreStillInternal() throws Exception {
  for(boolean reissued:List.of(false,true)){
   var jdbc=mock(JdbcTemplate.class);var events=new ArrayList<Map<String,Object>>();
   events.add(Map.of("event_type","INPUT","weight","100.00","source_order_id",0L,"stock_category","RAW","line_data","{\"netWeight\":\"100.00\"}"));
   if(reissued)events.add(Map.of("event_type","INPUT","weight","20.00","source_order_id",8L,"stock_origin_order_id",7L,"stock_category","SEMI","line_data","{\"netWeight\":\"20.00\"}"));
   when(jdbc.queryForList(startsWith("SELECT ev.*,e.order_id,s.output_category"),eq(7L))).thenReturn(events);
   when(jdbc.queryForList(startsWith("SELECT CAST(l.data AS CHAR) line_data,ev.line_id FROM metal_production_execution_event"),eq(7L),eq(7L))).thenReturn(List.of(Map.of("line_id",700L,"line_data","{\"netWeight\":\"40.00\",\"sentWeight\":\"40.00\"}")));
   when(jdbc.queryForList(startsWith("SELECT DISTINCT r.id,CAST(r.result AS CHAR) result"),eq(700L))).thenReturn(List.of(Map.of("id",800L,"result","{\"receivedWeight\":\"20.00\"}")));
   when(jdbc.queryForObject(startsWith("SELECT COALESCE(SUM(st.net_weight)"),eq(BigDecimal.class),eq(7L))).thenReturn(new BigDecimal(reissued?"60.00":"80.00"));
   when(jdbc.queryForList(startsWith("SELECT input_weight,output_weight"),eq(7L))).thenReturn(List.of(Map.of("input_weight",reissued?"20.00":"0.00")));
   var method=ProductionExecutionService.class.getDeclaredMethod("orderBalance",long.class);method.setAccessible(true);
   var report=(Map<?,?>)method.invoke(new ProductionExecutionService(jdbc,mock(FactoryScope.class)),7L);
   assertEquals("100.00",report.get("externalInputWeight"));assertEquals("20.00",report.get("transferredToOtherOrdersWeight"));assertEquals("0.00",report.get("balanceDifferenceWeight"));
  }
 }
 @Test void internalReturnStaysInIntermediateStockInsteadOfLeavingTheOrder() throws Exception {
  var jdbc=mock(JdbcTemplate.class);
  var incoming=Map.<String,Object>of("event_type","INPUT","weight","3.00","source_order_id",0L,"stock_category","RAW","line_data","{\"netWeight\":\"3.00\"}");
  var returned=Map.<String,Object>of("event_type","RETURN","weight","1.00","stock_origin_order_id",7L,"line_data","{\"netWeight\":\"0.99\",\"actualNetWeight\":\"0.99\",\"sentWeight\":\"1.00\"}");
  when(jdbc.queryForList(startsWith("SELECT ev.*,e.order_id,s.output_category"),eq(7L))).thenReturn(List.of(incoming,returned));
  when(jdbc.queryForObject(startsWith("SELECT COALESCE(SUM(st.net_weight)"),eq(BigDecimal.class),eq(7L))).thenReturn(new BigDecimal("2.99"));
  var method=ProductionExecutionService.class.getDeclaredMethod("orderBalance",long.class);method.setAccessible(true);
  var report=(Map<?,?>)method.invoke(new ProductionExecutionService(jdbc,mock(FactoryScope.class)),7L);
  assertEquals("0.00",report.get("originalReturnWeight"));assertEquals("2.99",report.get("intermediateStockWeight"));assertEquals("0.00",report.get("balanceDifferenceWeight"));
 }
 @Test void cyclicOrEmptyIntermediateSourceFailsInsteadOfInventingComposition() throws Exception {
  for(boolean cycle:List.of(true,false)){
   var jdbc=mock(JdbcTemplate.class);
   when(jdbc.queryForObject("SELECT order_id FROM metal_production_execution WHERE id=?",Long.class,1L)).thenReturn(7L);
   when(jdbc.queryForObject("SELECT order_id FROM metal_production_execution WHERE id=?",Long.class,2L)).thenReturn(7L);
   when(jdbc.queryForObject("SELECT processing_type FROM metal_production_order WHERE id=?",String.class,7L)).thenReturn("DIRECT_CUSTOM");
   var input=Map.<String,Object>of("weight","50.00","customer_weight","25.00","factory_weight","25.00","origin_order_id",7L,"identity_snapshot","{\"sourceExecutionId\":\""+(cycle?"1":"2")+"\"}");
   when(jdbc.queryForList(startsWith("SELECT ev.*,st.origin_order_id"),eq(1L))).thenReturn(List.of(input));
   when(jdbc.queryForList(startsWith("SELECT ev.*,st.origin_order_id"),eq(2L))).thenReturn(List.of());
   var method=ProductionExecutionService.class.getDeclaredMethod("netSources",long.class);method.setAccessible(true);
   var failure=assertThrows(java.lang.reflect.InvocationTargetException.class,()->method.invoke(new ProductionExecutionService(jdbc,mock(FactoryScope.class)),1L));
   assertInstanceOf(com.ruoyi.common.exception.ServiceException.class,failure.getCause());
   assertTrue(failure.getCause().getMessage().contains(cycle?"循环":"缺少"));
  }
 }
 @Test void emptyOrderPurityDoesNotRestrictStockPurityOrSkipMetalAndSpecificationChecks(){
  var target=new HashMap<String,Object>(Map.of("metalTypeId","25","specification","统一规格"));
  var source=new HashMap<String,Object>(target);
  for(String actual:List.of("99.00","50.00","")){
   source.put("purity",actual);assertTrue(ProductionExecutionService.matchesSemiIdentity(source,target));
   target.put("purity","  ");assertTrue(ProductionExecutionService.matchesSemiIdentity(source,target));target.remove("purity");
  }
  target.put("purity",null);source.put("purity","99.00");assertTrue(ProductionExecutionService.matchesSemiIdentity(source,target));
  source.put("metalTypeId","26");assertFalse(ProductionExecutionService.matchesSemiIdentity(source,target));
  source.put("metalTypeId","25");source.put("specification","其他规格");assertFalse(ProductionExecutionService.matchesSemiIdentity(source,target));
 }
 @Test void semiIdentityRequiresFrozenSpecificationAndPurityWithNumericEquality(){
  var target=Map.<String,Object>of("metalTypeId","25","specification","统一规格","purity","99.000000");
  var source=new HashMap<String,Object>(target);source.put("purity","99.00");
  assertTrue(ProductionExecutionService.matchesSemiIdentity(source,target));
  source.put("purity","50.00");assertFalse(ProductionExecutionService.matchesSemiIdentity(source,target));
  source.remove("purity");assertFalse(ProductionExecutionService.matchesSemiIdentity(source,target));
  source.put("purity","99.00");source.put("specification","其他规格");assertFalse(ProductionExecutionService.matchesSemiIdentity(source,target));
  source.put("specification","统一规格");source.put("metalTypeId","26");assertFalse(ProductionExecutionService.matchesSemiIdentity(source,target));
 }
 @Test void intermediateUsesFinalSourceBalanceAfterPartialOutputAndLaterReturn() throws Exception {
  for(String type:List.of("DIRECT_CUSTOM","SHORTAGE_CUSTOM","MATERIAL_CUSTOM")){
   var jdbc=mock(JdbcTemplate.class);
   when(jdbc.queryForObject("SELECT order_id FROM metal_production_execution WHERE id=?",Long.class,1L)).thenReturn(7L);
   when(jdbc.queryForObject("SELECT order_id FROM metal_production_execution WHERE id=?",Long.class,2L)).thenReturn(7L);
   when(jdbc.queryForObject("SELECT processing_type FROM metal_production_order WHERE id=?",String.class,7L)).thenReturn(type);
   var original=Map.<String,Object>of("weight","100.00","customer_weight","50.00","factory_weight","50.00","document_id",10L,"line_id",11L,"origin_order_id",0L);
   var inherited=Map.<String,Object>of("weight","50.00","customer_weight","25.00","factory_weight","25.00","document_id",20L,"line_id",21L,"origin_order_id",7L,"identity_snapshot","{\"sourceExecutionId\":\"1\",\"originOutputWeight\":\"50.00\"}");
   when(jdbc.queryForList(startsWith("SELECT ev.*,st.origin_order_id"),eq(1L))).thenReturn(List.of(original));
   when(jdbc.queryForList(startsWith("SELECT ev.*,st.origin_order_id"),eq(2L))).thenReturn(List.of(inherited));
   when(jdbc.queryForMap(startsWith("SELECT COALESCE(SUM(returned_weight)"),eq(10L),eq(11L))).thenReturn(Map.of("returned_weight","50.00","factory_returned_weight","50.00"));
   when(jdbc.queryForMap(startsWith("SELECT COALESCE(SUM(returned_weight)"),eq(20L),eq(21L))).thenReturn(Map.of("returned_weight","10.00","factory_returned_weight","0.00"));
   var service=new ProductionExecutionService(jdbc,mock(FactoryScope.class));
   var method=ProductionExecutionService.class.getDeclaredMethod("netSources",long.class);method.setAccessible(true);
   assertEquals(List.of(new BigDecimal("40.00"),new BigDecimal("0.00")),method.invoke(service,2L),type);
   // Reversing the original return restores the downstream composition as well.
   when(jdbc.queryForMap(startsWith("SELECT COALESCE(SUM(returned_weight)"),eq(10L),eq(11L))).thenReturn(Map.of("returned_weight","0.00","factory_returned_weight","0.00"));
   assertEquals(List.of(new BigDecimal("20.00"),new BigDecimal("20.00")),method.invoke(service,2L),type);
  }
 }
}
