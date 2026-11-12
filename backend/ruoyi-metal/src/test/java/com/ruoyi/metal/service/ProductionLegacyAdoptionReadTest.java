package com.ruoyi.metal.service;

import com.alibaba.fastjson2.JSON;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductionLegacyAdoptionReadTest {
 static Map<String,Object> source(long id,String input,String output,String carry){
  var row=new HashMap<String,Object>();row.put("id",id);row.put("factory_id",1L);row.put("order_id",11L);row.put("order_item_id",14L);row.put("order_step_id",32L);row.put("status","OPEN");row.put("input_weight",input);row.put("output_weight",output);row.put("carry_out_weight",carry);row.put("legacy_adoption_operation_id",77L);return row;
 }
 static Map<String,Object> mapping(long source,String weight){return new HashMap<>(Map.of("sourceExecutionId",source,"targetExecutionId",101L,"batchId",201L,"weight",weight,"quantity",0,"kind","WIP","netCustomerWeight",source==15?"200.00":"0.00","netFactoryWeight",source==15?"4800.00":weight));}
 static class Fixture {
  final Map<Long,Map<String,Object>> sources=new HashMap<>();final List<Map<String,Object>> mappings=new ArrayList<>();
  final Map<String,Object> result=new HashMap<>();String action="LEGACY_ADOPT",targetCarry="120.00";
  final JdbcTemplate jdbc=mock(JdbcTemplate.class,inv->{String sql=inv.getArgument(0);
   if(inv.getMethod().getName().equals("queryForObject")){
    if(sql.startsWith("SELECT order_id"))return 11L;if(sql.startsWith("SELECT processing_type"))return "DIRECT_CUSTOM";if(sql.startsWith("SELECT COUNT"))return 0;return null;
   }
   if(inv.getMethod().getName().equals("queryForList")){
    if(sql.startsWith("SELECT action"))return List.of(Map.of("action",action,"result",JSON.toJSONString(result)));
    if(sql.startsWith("SELECT ev.weight,ev.quantity,e.order_step_id"))return List.of(Map.of("weight","4880.00","quantity",0,"order_step_id",32L));
    if(sql.startsWith("SELECT id FROM metal_production_batch"))return sql.contains("WHERE factory_id=?")?List.of(Map.of("id",201L)):List.of();
    if(sql.startsWith("SELECT carry_in_weight"))return List.of(Map.of("carry_in_weight",targetCarry,"carry_in_quantity",0));
    if(sql.startsWith("SELECT * FROM metal_production_execution")){if(sql.contains("WHERE order_id=?"))return new ArrayList<>(sources.values());long id=((Number)inv.getArgument(sql.contains("factory_id=?")?2:1)).longValue();return sources.containsKey(id)?List.of(sources.get(id)):List.of();}
    if(sql.startsWith("SELECT ev.*,st.origin_order_id"))return List.of(Map.of("document_id",39L,"line_id",41L,"customer_weight","200.00","factory_weight","4800.00"));
    if(sql.startsWith("SELECT id,weight FROM metal_production_execution_event"))return List.of(Map.of("id",26L,"weight","4880.00"));
    return List.of();
   }
   if(inv.getMethod().getName().equals("queryForMap"))return new HashMap<String,Object>();
   return org.mockito.Answers.RETURNS_DEFAULTS.answer(inv);
  });
  Fixture(){result.put("factoryId",1);result.put("orderId",11);result.put("orderItemId",14);result.put("legacyExecutionMappings",mappings);sources.put(15L,source(15,"5000.00","4880.00","120.00"));mappings.add(mapping(15,"120.00"));}
 }
 @Test void adoptionMovesOnlyRemainingResponsibilityWithoutChangingOldNetOrOutput(){
  var f=new Fixture();var balance=new ProductionSourceBalances(f.jdbc);
  assertEquals(List.of(new BigDecimal("200.00"),new BigDecimal("4800.00")),balance.net(15));
  assertEquals(List.of(new BigDecimal("195.20"),new BigDecimal("4684.80")),balance.output(15,26));
  assertEquals(List.of(new BigDecimal("0.00"),new BigDecimal("0.00")),balance.remaining(15));
  assertEquals(List.of(new BigDecimal("195.20"),new BigDecimal("4684.80")),balance.output(15,26));
  when(f.jdbc.queryForMap(org.mockito.ArgumentMatchers.startsWith("SELECT COALESCE(SUM(returned_weight)"),org.mockito.ArgumentMatchers.eq(39L),org.mockito.ArgumentMatchers.eq(41L))).thenReturn(Map.of("returned_weight","115.20","factory_returned_weight","115.20"));
  var afterCarriedReturn=new ProductionSourceBalances(f.jdbc);assertEquals(balance.net(15),afterCarriedReturn.net(15));assertEquals(balance.output(15,26),afterCarriedReturn.output(15,26));
 }
 @Test void groupedTargetCarryMustEqualAllSourceMappings(){
  var f=new Fixture();f.sources.put(16L,source(16,"11666.70","0","11666.70"));f.mappings.add(mapping(16,"11666.70"));
  assertNull(ProductionExecutionService.legacyAdoption(f.jdbc,f.sources.get(15L)));
  f.targetCarry="11786.70";assertNotNull(ProductionExecutionService.legacyAdoption(f.jdbc,f.sources.get(15L)));assertNotNull(ProductionExecutionService.legacyAdoption(f.jdbc,f.sources.get(16L)));
  f.sources.get(16L).put("carry_out_weight","0");assertNull(ProductionExecutionService.legacyAdoption(f.jdbc,f.sources.get(15L)));
 }
 @Test void operationScopeActionAndAmountsCannotBeReplacedByBodyFlagsOrMarkerAlone(){
  var f=new Fixture();var source=f.sources.get(15L);assertNotNull(ProductionExecutionService.legacyAdoption(f.jdbc,source));
  f.action="SPLIT";assertNull(ProductionExecutionService.legacyAdoption(f.jdbc,source));f.action="LEGACY_ADOPT";
  f.result.put("factoryId",2);assertNull(ProductionExecutionService.legacyAdoption(f.jdbc,source));f.result.put("factoryId",1);
  source.put("carry_out_weight","119.99");assertNull(ProductionExecutionService.legacyAdoption(f.jdbc,source));source.put("carry_out_weight","120.00");
  source.remove("legacy_adoption_operation_id");source.put("legacyAdopted",true);assertNull(ProductionExecutionService.legacyAdoption(f.jdbc,source));
 }
 @Test void adoptedHistoryStaysReadOnlyAndReturnsTheActualDestination(){
  var f=new Fixture();var row=new ProductionExecutionService(f.jdbc,null).view(f.sources.get(15L));
  assertEquals(true,row.get("readOnly"));assertEquals("LEGACY_ADOPTED",row.get("writeBlockedReasonCode"));assertEquals("201",row.get("targetBatchId"));assertEquals("101",row.get("targetExecutionId"));
  assertThrows(com.ruoyi.common.exception.ServiceException.class,()->ProductionExecutionService.requireMappedProduction(f.sources.get(15L)));
 }
 @Test void emptyCoverageRequiresZeroFactsAndAnExplicitEmptyMapping(){
  var f=new Fixture();var empty=source(6,"0","0","0");var map=new HashMap<String,Object>(Map.of("sourceExecutionId",6L,"batchId",201L,"weight","0","quantity",0,"kind","EMPTY"));map.put("targetExecutionId",null);f.mappings.clear();f.mappings.add(map);
  assertNotNull(ProductionExecutionService.legacyAdoption(f.jdbc,empty));empty.put("input_weight","1");assertNull(ProductionExecutionService.legacyAdoption(f.jdbc,empty));
 }
 @Test void onlyValidatedCoverageRemovesOldOpenWorkAndPendingDocumentsStillBlock()throws Exception{
  var f=new Fixture();var service=new ProductionExecutionService(f.jdbc,null);var method=ProductionExecutionService.class.getDeclaredMethod("noOpenWork",long.class);method.setAccessible(true);
  assertDoesNotThrow(()->method.invoke(service,11L));
  when(f.jdbc.queryForObject("SELECT COUNT(*) FROM metal_storage_document WHERE order_id=? AND deleted=0 AND status IN ('DRAFT','PENDING_REVIEW','RETURNED')",Integer.class,11L)).thenReturn(1);
  var blocked=assertThrows(java.lang.reflect.InvocationTargetException.class,()->method.invoke(service,11L));assertTrue(blocked.getCause().getMessage().contains("待处理单据"));
  f.action="OTHER";var invalid=assertThrows(java.lang.reflect.InvocationTargetException.class,()->method.invoke(service,11L));assertTrue(invalid.getCause().getMessage().contains("历史"));
 }
 @Test void legacyOutputUsesAcceptedOriginalFactEvenAfterStockIsConsumed(){
  var f=new Fixture();var component=new HashMap<String,Object>(Map.of("stockId",17L,"stockSliceId",301L,"batchId",201L,"sourceExecutionId",15L,"productionEventId",26L,"weight","4880.00","quantity",0));
  f.result.put("stockComponents",new ArrayList<>(List.of(component)));
  var batch=new HashMap<String,Object>(Map.of("id",201L,"factory_id",1L,"order_id",11L,"order_item_id",14L,"origin_action","MIGRATION","origin_operation_id",77L));var service=new ProductionBatchService(f.jdbc,null,null);
  assertEquals("4880.00",service.legacyOutputs(batch).get(0).get("weight"));
  ((List<Object>)f.result.get("stockComponents")).add(component);assertThrows(com.ruoyi.common.exception.ServiceException.class,()->service.legacyOutputs(batch));
 }
 @Test void reversalKeepsUnadoptedLegacyCleanupButRetiredOrCorruptMarkersFailClosed(){
  for(String marker:List.of("NONE","VALID","CORRUPT")){
   var f=new Fixture();var source=f.sources.get(15L);if(marker.equals("NONE"))source.remove("legacy_adoption_operation_id");if(marker.equals("CORRUPT"))f.action="OTHER";
   when(f.jdbc.queryForList(org.mockito.ArgumentMatchers.startsWith("SELECT e.*,s.sequence_no"),org.mockito.ArgumentMatchers.eq(15L),org.mockito.ArgumentMatchers.eq(1L))).thenReturn(List.of(source));
   when(f.jdbc.queryForList("SELECT * FROM metal_production_execution_event WHERE factory_id=? AND document_id=? AND reversed=0 FOR UPDATE",1L,39L)).thenReturn(List.of(Map.of("event_type","INPUT","weight","100.00","quantity",0)));
   when(f.jdbc.queryForList(org.mockito.ArgumentMatchers.startsWith("SELECT ev.*,st.origin_order_id,CAST(st.identity_snapshot AS CHAR) identity_snapshot,e.batch_id"),org.mockito.ArgumentMatchers.eq(11L))).thenReturn(List.of());
   var service=new ProductionExecutionService(f.jdbc,null);var doc=Map.<String,Object>of("executionId",15L,"id",39L);
   if(marker.equals("NONE")){
    assertDoesNotThrow(()->service.reverseDocument(1L,doc));
    verify(f.jdbc).update("UPDATE metal_production_execution SET input_weight=input_weight-?,input_quantity=input_quantity-?,version=version+1 WHERE id=?","100.00",0,15L);
   }else{
    assertThrows(com.ruoyi.common.exception.ServiceException.class,()->service.reverseDocument(1L,doc));
    verify(f.jdbc,never()).update(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any(Object[].class));
   }
  }
 }
 @Test void reportShowsConfirmedLegacyBookContributionsWithoutMakingHistoryWritable(){
  var f=new Fixture();var scope=mock(com.ruoyi.metal.security.FactoryScope.class);when(scope.isAdmin()).thenReturn(true);when(scope.resolveFactory(1L)).thenReturn(1L);
  when(f.jdbc.queryForList(org.mockito.ArgumentMatchers.startsWith("SELECT * FROM metal_production_order WHERE id=?"),org.mockito.ArgumentMatchers.eq(11L),org.mockito.ArgumentMatchers.eq(1L))).thenReturn(List.of(Map.of("id",11L,"factory_id",1L,"status","IN_PROGRESS")));
  when(f.jdbc.queryForList(org.mockito.ArgumentMatchers.startsWith("SELECT e.*,s.measurement_mode,s.output_category"),org.mockito.ArgumentMatchers.eq(11L),org.mockito.ArgumentMatchers.eq(1L))).thenReturn(List.of(f.sources.get(15L)));
  when(f.jdbc.queryForObject(org.mockito.ArgumentMatchers.startsWith("SELECT COALESCE(SUM(st.net_weight)"),org.mockito.ArgumentMatchers.eq(BigDecimal.class),org.mockito.ArgumentMatchers.eq(11L))).thenReturn(BigDecimal.ZERO);
  var service=new ProductionExecutionService(f.jdbc,scope);var report=service.report(11L,1L);var row=(Map<?,?>)((List<?>)report.get("executions")).get(0);
  assertEquals(true,row.get("sourceCompositionKnown"));assertEquals("200.00",row.get("customerInputWeight"));assertEquals("4800.00",row.get("factoryInputWeight"));assertEquals(true,row.get("readOnly"));assertEquals("LEGACY_ADOPTED",row.get("mappingStatus"));
  f.action="OTHER";row=(Map<?,?>)((List<?>)service.report(11L,1L).get("executions")).get(0);assertEquals(false,row.get("sourceCompositionKnown"));assertNull(row.get("customerInputWeight"));assertEquals(true,row.get("readOnly"));
 }
}
