package com.ruoyi.metal.service;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.ruoyi.common.exception.ServiceException;
import static org.junit.jupiter.api.Assertions.*;
class ProductionExecutionServiceTest {
 @Test void directReportUsesActualReturnedSourceWeightButIntermediateStockKeepsComposition() throws Exception {
  var jdbc=org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
  org.mockito.Mockito.when(jdbc.queryForObject("SELECT order_id FROM metal_production_execution WHERE id=?",Long.class,1L)).thenReturn(7L);
  org.mockito.Mockito.when(jdbc.queryForObject("SELECT processing_type FROM metal_production_order WHERE id=?",String.class,7L)).thenReturn("DIRECT_CUSTOM");
  var event=new HashMap<String,Object>(Map.of("customer_weight","40.00","factory_weight","60.00","document_id",2L,"line_id",3L,"origin_order_id",6L));
  org.mockito.Mockito.when(jdbc.queryForList(org.mockito.ArgumentMatchers.startsWith("SELECT ev.*,st.origin_order_id"),org.mockito.ArgumentMatchers.eq(1L))).thenReturn(List.of(event));
  org.mockito.Mockito.when(jdbc.queryForMap(org.mockito.ArgumentMatchers.startsWith("SELECT COALESCE(SUM(returned_weight)"),org.mockito.ArgumentMatchers.eq(2L),org.mockito.ArgumentMatchers.eq(3L))).thenReturn(Map.of("returned_weight","30.00","factory_returned_weight","10.00"));
  var service=new ProductionExecutionService(jdbc,org.mockito.Mockito.mock(com.ruoyi.metal.security.FactoryScope.class));
  var method=ProductionExecutionService.class.getDeclaredMethod("netSources",long.class);method.setAccessible(true);
  assertEquals(List.of(new BigDecimal("20.00"),new BigDecimal("50.00")),method.invoke(service,1L));
  event.put("origin_order_id",7L);
  assertEquals(List.of(new BigDecimal("28.00"),new BigDecimal("42.00")),method.invoke(service,1L));
 }
 @Test void unusedBatchRequiresNoExecutionHistoryAndNoOutstandingDocuments(){
  var row=new HashMap<String,Object>();row.put("status","OPEN");row.put("inputWeight","0.00");
  assertEquals("",ProductionExecutionService.unusedReason(row,false,List.of()));
  assertTrue(ProductionExecutionService.unusedReason(row,true,List.of()).contains("实际领料"));
  assertTrue(ProductionExecutionService.unusedReason(row,false,List.of(Map.of("number","OUT-7","status","DRAFT"))).contains("OUT-7（草稿）"));
  assertTrue(ProductionExecutionService.unusedReason(row,false,List.of(Map.of("number","OUT-8","status","PENDING_REVIEW"))).contains("待复验"));
  row.put("inputWeight","1.00");assertTrue(ProductionExecutionService.unusedReason(row,false,List.of()).contains("实际领料"));
  row.put("status","CLOSED");assertFalse(ProductionExecutionService.unusedReason(row,false,List.of()).isEmpty());
 }
 @Test void batchEditRejectsForeignFactoryBeforeAnyReadOrWrite(){
  var jdbc=org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);var scope=org.mockito.Mockito.mock(com.ruoyi.metal.security.FactoryScope.class);org.mockito.Mockito.when(scope.isAdmin()).thenReturn(true);org.mockito.Mockito.when(scope.resolveFactory(9L)).thenThrow(new ServiceException("跨厂拒绝",403));
  assertThrows(ServiceException.class,()->new ProductionExecutionService(jdbc,scope).edit(2,Map.of("factoryId",9)));org.mockito.Mockito.verifyNoInteractions(jdbc);
 }
 @Test void lossUsesLargestRemainderAndConservesEveryCent(){
  assertEquals(List.of(new BigDecimal("0.00"),new BigDecimal("0.01")),ProductionExecutionService.allocateLoss(new BigDecimal("0.01"),new BigDecimal("1.00"),new BigDecimal("2.00")));
  assertEquals(List.of(new BigDecimal("0.01"),new BigDecimal("0.00")),ProductionExecutionService.allocateLoss(new BigDecimal("0.01"),new BigDecimal("1.00"),new BigDecimal("1.00")));
  for(int cents=0;cents<=100;cents++){var loss=BigDecimal.valueOf(cents,2);var split=ProductionExecutionService.allocateLoss(loss,new BigDecimal("0.27"),new BigDecimal("0.73"));assertEquals(loss,split.get(0).add(split.get(1)));}
  assertThrows(ServiceException.class,()->ProductionExecutionService.allocateLoss(new BigDecimal("1.01"),BigDecimal.ONE,BigDecimal.ZERO));
 }
 @Test void completedStageMustMatchOrderedPrefixExactlyIncludingRepeatedProcesses(){
  assertTrue(ProductionExecutionService.matchesStage(List.of("1","2","1"),List.of("1","2","1")));
  assertFalse(ProductionExecutionService.matchesStage(List.of("1","2","1"),List.of("1","2")));
  assertFalse(ProductionExecutionService.matchesStage(List.of("1","2"),List.of("2","1")));
  assertFalse(ProductionExecutionService.matchesStage(List.of("1"),List.of("1","2")));
 }
 @Test void incomingUsesReceiptAndOutgoingUsesSentWeightSoHandoverLossIsNotProcessLoss(){
  var line=new HashMap<String,Object>();line.put("netWeight","97.00");line.put("actualNetWeight","97.00");line.put("sentWeight","100.00");
  assertEquals(new BigDecimal("97.00"),ProductionExecutionService.documentWeight("PRODUCTION_OUT",line));
  for(String type:List.of("SEMI_IN","PRODUCT_IN","RAW_RETURN","RECOVERY_RETURN"))assertEquals(new BigDecimal("100.00"),ProductionExecutionService.documentWeight(type,line));
 }
 @Test void fullyLostHandoverStillHasAnActualIssue(){var line=new HashMap<String,Object>();line.put("netWeight","0.00");line.put("sentWeight","2.00");assertTrue(ProductionExecutionService.validDocumentWeight("PRODUCTION_OUT",line));assertEquals(new BigDecimal("0.00"),ProductionExecutionService.documentWeight("PRODUCTION_OUT",line));line.put("sentWeight","0.00");assertFalse(ProductionExecutionService.validDocumentWeight("PRODUCTION_OUT",line));}
 @Test void weightedFinishedGoodsUseExplicitReferenceCountWithoutConvertingWeight(){var e=Map.<String,Object>of("measurement_mode","WEIGHT");var line=new HashMap<String,Object>();line.put("netWeight","300.00");line.put("quantity",0);assertThrows(ServiceException.class,()->ProductionExecutionService.productionQuantity("PRODUCT_IN",e,line));line.put("referenceQuantity",2);assertEquals(2,ProductionExecutionService.productionQuantity("PRODUCT_IN",e,line));assertEquals(0,line.get("quantity"));line.put("referenceQuantity","2.50");assertThrows(ServiceException.class,()->ProductionExecutionService.productionQuantity("PRODUCT_IN",e,line));}
 @Test void closedStepRecognizesJdbcBooleanAndNumericRepresentations(){assertTrue(ProductionExecutionService.closed(true));assertTrue(ProductionExecutionService.closed(1));assertFalse(ProductionExecutionService.closed(false));assertFalse(ProductionExecutionService.closed(0));}
 @Test void stepCompletionRejectsForgedFactoryBeforeReadingAnyOrder(){
  var jdbc=org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);var scope=org.mockito.Mockito.mock(com.ruoyi.metal.security.FactoryScope.class);org.mockito.Mockito.when(scope.isAdmin()).thenReturn(true);org.mockito.Mockito.when(scope.resolveFactory(9L)).thenThrow(new ServiceException("跨厂拒绝",403));
  var service=new ProductionExecutionService(jdbc,scope);assertThrows(ServiceException.class,()->service.completeStep(1,2,Map.of("factoryId",9)));org.mockito.Mockito.verifyNoInteractions(jdbc);
 }
 @Test void idempotentResultKeepsNullFieldsAndIdenticalDateStrings(){
  var raw=new LinkedHashMap<String,Object>();raw.put("id",900L);raw.put("planned_quantity",null);raw.put("loss_weight",null);raw.put("create_time",java.time.LocalDateTime.of(2026,11,9,18,20,21));raw.put("closed_at",java.sql.Timestamp.valueOf("2026-11-09 19:21:22"));
  var result=new ProductionExecutionService(null,null).view(raw);String saved=com.alibaba.fastjson2.JSON.toJSONString(result,com.alibaba.fastjson2.JSONWriter.Feature.WriteMapNullValue);var replay=com.alibaba.fastjson2.JSON.parseObject(saved);
  assertEquals(result,replay);assertTrue(replay.containsKey("plannedQuantity"));assertNull(replay.get("plannedQuantity"));assertTrue(replay.containsKey("lossWeight"));assertEquals("2026-11-09 18:20:21",result.get("createTime"));assertEquals("2026-11-09 19:21:22",result.get("closedAt"));
 }
 @Test void piecesAcceptDecimalTransportOnlyIfExactlyIntegral(){assertEquals(2,ProductionExecutionService.number("2.00"));assertThrows(ServiceException.class,()->ProductionExecutionService.number("2.01"));}
 @Test void supplementRequiresExplicitBatchEvenWithCompleteCreateFields(){
  var jdbc=org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
  var service=new ProductionExecutionService(jdbc,null);
  var doc=new HashMap<String,Object>(Map.of("type","PRODUCTION_OUT","executionMode","EXISTING","orderId","7","orderItemId","3","orderStepId","4","processorId","5"));
  assertTrue(assertThrows(ServiceException.class,()->service.ensureExecutionForOutbound(1,doc,List.of())).getMessage().contains("请选择加工批次"));
  org.mockito.Mockito.verifyNoInteractions(jdbc);
  doc.put("executionMode","INVALID");
  assertTrue(assertThrows(ServiceException.class,()->service.ensureExecutionForOutbound(1,doc,List.of())).getMessage().contains("领料方式无效"));
 }
 @Test void outboundEnsureRequiresCreateFieldsWhenNoBatch(){
  var jdbc=org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);var scope=org.mockito.Mockito.mock(com.ruoyi.metal.security.FactoryScope.class);
  var service=new ProductionExecutionService(jdbc,scope);var doc=new HashMap<String,Object>(Map.of("type","PRODUCTION_OUT","orderId","7"));
  var error=assertThrows(ServiceException.class,()->service.ensureExecutionForOutbound(1L,doc,List.of()));
  assertTrue(error.getMessage().contains("生产批次"));
  assertNull(doc.get("executionId"));
 }
 @Test void oldImplicitOutboundCreationCannotCreateAnOrphanProcessingRecord(){
  var jdbc=org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);var scope=org.mockito.Mockito.mock(com.ruoyi.metal.security.FactoryScope.class);
  var service=new ProductionExecutionService(jdbc,scope);
  var doc=new HashMap<String,Object>();doc.put("type","PRODUCTION_OUT");doc.put("orderId","7");doc.put("orderItemId","3");doc.put("orderStepId","4");doc.put("processorId","5");
  var lines=List.of(Map.<String,Object>of("quantity","0"),Map.<String,Object>of("quantity","2"),Map.<String,Object>of("netWeight","1.00"));
  // New production must create a batch identity and explicit stage record first.
  assertThrows(Exception.class,()->service.ensureExecutionForOutbound(1L,doc,lines));
  assertNull(doc.get("plannedQuantity"));org.mockito.Mockito.verifyNoInteractions(jdbc);
 }
}
