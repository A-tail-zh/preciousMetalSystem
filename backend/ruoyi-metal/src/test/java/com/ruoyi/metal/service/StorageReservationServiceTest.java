package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.util.*;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.metal.security.FactoryScope;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StorageReservationServiceTest {
 @Test void physicalDecreaseCannotSpendOtherSessionsHold(){
  var jdbc=mock(JdbcTemplate.class);var reservations=new StorageReservationService(jdbc,mock(FactoryScope.class));
  when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(Map.of("weight",new BigDecimal("80.00"),"quantity",8)));
  assertThrows(ServiceException.class,()->reservations.protect(1,2,null,new BigDecimal("100.00"),10,new BigDecimal("-21.00"),0));
  assertThrows(ServiceException.class,()->reservations.protect(1,2,null,new BigDecimal("100.00"),10,BigDecimal.ZERO,-3));
  assertDoesNotThrow(()->reservations.protect(1,2,null,new BigDecimal("100.00"),10,new BigDecimal("-20.00"),-2));
 }
 @Test void positivePostingDoesNotReadHolds(){
  var jdbc=mock(JdbcTemplate.class);var reservations=new StorageReservationService(jdbc,mock(FactoryScope.class));
  reservations.protect(1,2,null,BigDecimal.ZERO,0,new BigDecimal("10.00"),1);verifyNoInteractions(jdbc);
 }
 @Test void crossFactoryRejectedBeforeReadingSession(){
  var jdbc=mock(JdbcTemplate.class);var scope=mock(FactoryScope.class);when(scope.resolveFactory(2L)).thenThrow(new ServiceException("cross factory"));
  assertThrows(ServiceException.class,()->new StorageReservationService(jdbc,scope).status(UUID.randomUUID().toString(),2L));verifyNoInteractions(jdbc);
 }

 private static String key(){return UUID.randomUUID().toString();}
 private static Map<String,Object> line(String weight,int quantity,long owner){var l=new LinkedHashMap<String,Object>();l.put("reservationLineId",key());l.put("stockId","2");l.put("netWeight",weight);l.put("quantity",quantity);l.put("allocations",new ArrayList<>(List.of(new LinkedHashMap<>(Map.of("ownerId",String.valueOf(owner),"weight",weight,"quantity",quantity)))));return l;}
 private static List<Map<String,Object>> normalize(StorageReservationService service,String sid,Map<String,Object> doc,List<Map<String,Object>> lines)throws Exception {
  var method=StorageReservationService.class.getDeclaredMethod("normalize",long.class,String.class,Map.class,List.class);method.setAccessible(true);
  try{return (List<Map<String,Object>>)method.invoke(service,1L,sid,doc,lines);}catch(java.lang.reflect.InvocationTargetException e){if(e.getCause() instanceof RuntimeException r)throw r;throw e;}
 }
 private static JdbcTemplate balances(boolean piece,String customerWeight,String factoryWeight){
  var jdbc=mock(JdbcTemplate.class);when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(i->{String sql=i.getArgument(0);Object[] a=(Object[])i.getRawArguments()[1];
   if((sql.contains("SUM(r.weight)")||sql.contains("SELECT r.weight,r.quantity")))return List.of(Map.of("weight",BigDecimal.ZERO,"quantity",0));
   if(sql.contains("batch_no,w.name"))return List.of(Map.of("batch_no","BATCH-2","warehouse_name","原料仓"));
   if(sql.contains("metal_customer"))return List.of(Map.of("customer_id",8,"customer_name","客户甲"));
   if(sql.startsWith("SELECT * FROM metal_storage_batch_slice"))return List.of(Map.of("id",20L,"stock_id",2L,"slice_kind","UNASSIGNED","version",0,"weight",new BigDecimal("100.00"),"quantity",piece?10:0));
   if(sql.startsWith("SELECT weight,quantity FROM metal_storage_batch_slice"))return List.of(Map.of("weight",new BigDecimal("100.00"),"quantity",piece?10:0));
   if(sql.contains("SELECT o.weight,o.quantity FROM metal_storage_batch_slice_owner")){String w=((Number)a[3]).longValue()==0?factoryWeight:customerWeight;return List.of(Map.of("weight",new BigDecimal(w),"quantity",piece?10:0));}
   if(sql.contains("SELECT weight,quantity FROM metal_storage_ownership")){String w=((Number)a[2]).longValue()==0?factoryWeight:customerWeight;return List.of(Map.of("weight",new BigDecimal(w),"quantity",piece?10:0));}
   if(sql.contains("SELECT net_weight weight"))return List.of(Map.of("weight",new BigDecimal("100.00"),"quantity",piece?10:0));
   if(sql.contains("SELECT s.*,w.status"))return List.of(Map.of("id",2,"warehouse_id",3,"warehouse_status","0","warehouse_deleted","0","category","RAW","measurement_mode",piece?"PIECE":"WEIGHT"));
   return List.of();});return jdbc;
 }
 @Test void allRowsAreAccumulatedBeforeReservingPhysicalOrOwnership()throws Exception {
  var jdbc=balances(false,"100.00","0.00");var service=new StorageReservationService(jdbc,mock(FactoryScope.class));
  var ex=assertThrows(ServiceException.class,()->normalize(service,key(),Map.of("type","PRODUCTION_OUT","customerId","8"),List.of(line("60.00",0,8),line("50.00",0,8))));
  assertTrue(ex.getMessage().contains("原料仓"));assertTrue(ex.getMessage().contains("BATCH-2"));assertTrue(ex.getMessage().contains("110.00"));assertTrue(ex.getMessage().contains("10.00"));verify(jdbc,never()).update(anyString(),any(Object[].class));
 }
 @Test void defaultSourcesUseOwnCustomerThenFactoryAndKeepUnfilledShortageAccurate()throws Exception {
  var jdbc=balances(false,"60.00","40.00");var service=new StorageReservationService(jdbc,mock(FactoryScope.class));var first=line("70.00",0,8);first.remove("allocations");
  var result=normalize(service,key(),Map.of("type","PRODUCTION_OUT","customerId","8"),List.of(first));var parts=StorageSupport.maps(result.get(0).get("allocations"));assertEquals(List.of("8","0"),parts.stream().map(a->a.get("ownerId")).toList());assertEquals("60.00",parts.get(0).get("weight"));assertEquals("10.00",parts.get(1).get("weight"));
  var zero=balances(false,"60.00","0.00");var second=line("70.00",0,8);second.remove("allocations");var ex=assertThrows(ServiceException.class,()->normalize(new StorageReservationService(zero,mock(FactoryScope.class)),key(),Map.of("type","PRODUCTION_OUT","customerId","8"),List.of(second)));assertTrue(ex.getMessage().contains("可用60.00g"));assertTrue(ex.getMessage().contains("不足10.00g"));
 }
 @Test void pieceReservationsIndependentlyCheckWeightAndQuantity()throws Exception {
  var service=new StorageReservationService(balances(true,"100.00","0.00"),mock(FactoryScope.class));
  assertThrows(ServiceException.class,()->normalize(service,key(),Map.of("type","PRODUCTION_OUT","customerId","8"),List.of(line("60.00",6,8),line("30.00",5,8))));
  assertThrows(ServiceException.class,()->normalize(service,key(),Map.of("type","PRODUCTION_OUT","customerId","8"),List.of(line("60.00",4,8),line("50.00",4,8))));
 }
 @Test void defaultPieceSourcesSkipOwnerWithoutEnoughWeightForWholePiece()throws Exception {
  var service=new StorageReservationService(balances(true,"1.00","99.00"),mock(FactoryScope.class));var row=line("10.00",1,8);row.remove("allocations");
  var result=normalize(service,key(),Map.of("type","PRODUCTION_OUT","customerId","8"),List.of(row));var parts=StorageSupport.maps(result.get(0).get("allocations"));assertEquals(1,parts.size());assertEquals("0",parts.get(0).get("ownerId"));
 }
 @Test void releaseNeverFreesBoundSessionEvenWithStaleVersion(){
  var jdbc=mock(JdbcTemplate.class);var scope=mock(FactoryScope.class);when(scope.resolveFactory(1L)).thenReturn(1L);when(scope.isAdmin()).thenReturn(true);String sid=key();
  when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(i->i.getArgument(0,String.class).contains("current_status")?List.of(Map.of("session_id",sid,"status","BOUND","current_status","BOUND","version",4,"data","[]","expiresAt","2000-01-01 00:00:00")):List.of());
  try(var security=mockStatic(com.ruoyi.common.utils.SecurityUtils.class)){security.when(com.ruoyi.common.utils.SecurityUtils::getUserId).thenReturn(9L);var out=new StorageReservationService(jdbc,scope).change(sid,"release",Map.of("factoryId",1,"requestId",key(),"reservationVersion",0));assertEquals("BOUND",out.get("status"));verify(jdbc,never()).update(startsWith("UPDATE metal_storage_reservation_session"),any(Object[].class));}
 }
 @Test void expiryAndReplayReturnCurrentAuthoritativeState(){
  var jdbc=mock(JdbcTemplate.class);var scope=mock(FactoryScope.class);when(scope.resolveFactory(1L)).thenReturn(1L);when(scope.isAdmin()).thenReturn(true);String sid=key();var body=Map.<String,Object>of("factoryId",1,"requestId",key(),"sessionId",sid,"reservationVersion",0);
  when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(i->{String sql=i.getArgument(0);if(sql.contains("request_hash"))return List.of(Map.of("action","replace","session_id",sid,"request_hash",StorageSupport.hash(body),"result","{\"sessionId\":\""+sid+"\",\"version\":1,\"status\":\"TEMP\",\"lines\":[]}"));if(sql.contains("current_status"))return List.of(Map.of("session_id",sid,"status","TEMP","current_status","EXPIRED","version",3,"data","[]","expiresAt","2000-01-01 00:00:00"));return List.of();});
  try(var security=mockStatic(com.ruoyi.common.utils.SecurityUtils.class)){security.when(com.ruoyi.common.utils.SecurityUtils::getUserId).thenReturn(9L);var service=new StorageReservationService(jdbc,scope);var out=service.replace(body);assertEquals("EXPIRED",out.get("status"));assertEquals(3,out.get("version"));assertThrows(ServiceException.class,()->service.change(sid,"renew",Map.of("factoryId",1,"requestId",key(),"reservationVersion",3)));}
 }
 @Test void foreignActorCannotReadKnownSession(){
  var jdbc=mock(JdbcTemplate.class);var scope=mock(FactoryScope.class);when(scope.resolveFactory(1L)).thenReturn(1L);when(scope.isAdmin()).thenReturn(true);
  try(var security=mockStatic(com.ruoyi.common.utils.SecurityUtils.class)){security.when(com.ruoyi.common.utils.SecurityUtils::getUserId).thenReturn(99L);assertThrows(ServiceException.class,()->new StorageReservationService(jdbc,scope).status(key(),1L));verify(jdbc).queryForList(contains("factory_id=? AND actor_id=? AND session_id=?"),eq(1L),eq(99L),anyString());}
 }
 @Test void legacyPendingReviewCannotConsumeUnreservedStock(){var jdbc=mock(JdbcTemplate.class);var ex=assertThrows(ServiceException.class,()->new StorageReservationService(jdbc,mock(FactoryScope.class)).consume(1,2,Map.of("type","RAW_OUT"),List.of()));assertTrue(ex.getMessage().contains("退回发起人"));verify(jdbc,never()).update(anyString(),any(Object[].class));}

 @Test void consumeRejectsActualBeyondReservedSourceAndReleasesUnusedDifference(){
  var jdbc=mock(JdbcTemplate.class);String sid=key(),local=key();when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(i->i.getArgument(0,String.class).contains("status='BOUND'")?List.of(Map.of("session_id",sid)):List.of(Map.of("reservation_line_id",local,"stock_id",2,"owner_id",0,"weight",new BigDecimal("50.00"),"quantity",5)));
  var service=new StorageReservationService(jdbc,mock(FactoryScope.class));var row=line("51.00",5,0);row.put("measurementMode","PIECE");row.put("reservationLineId",local);var doc=Map.<String,Object>of("type","PRODUCTION_OUT");
  var ex=assertThrows(ServiceException.class,()->service.consume(1,3,doc,List.of(row)));assertTrue(ex.getMessage().contains("超过本单已预占"));verify(jdbc,never()).update(anyString(),any(Object[].class));
  row.put("allocations",List.of(Map.of("ownerId","0","weight","40.00","quantity",4)));row.put("netWeight","40.00");row.put("quantity",4);service.consume(1,3,doc,List.of(row));verify(jdbc).update(contains("status='CONSUMED'"),eq(sid));
 }
 @Test void bothLedgerDecreasesReachReservationGuardBeforeAnyBalanceMutation(){
  var jdbc=mock(JdbcTemplate.class);var scope=mock(FactoryScope.class);var guard=mock(StorageReservationService.class);
  var ledger=new StorageLedgerService(jdbc,scope,guard){@Override Map<String,Object> stock(long f,long id){return Map.of("net_weight",new BigDecimal("100.00"),"quantity",10);}@Override Map<String,Object> owner(long f,long id,long owner){return Map.of("weight",new BigDecimal("100.00"),"quantity",10);}};
  doThrow(new ServiceException("reserved")).when(guard).protect(eq(1L),eq(2L),nullable(Long.class),any(BigDecimal.class),eq(10L),any(BigDecimal.class),eq(-1L));
  assertThrows(ServiceException.class,()->ledger.physical(5,1,2,new BigDecimal("-10.00"),-1,"ADJUSTMENT"));assertThrows(ServiceException.class,()->ledger.ownership(5,1,2,0,new BigDecimal("-10.00"),-1,"REPAYMENT"));verify(jdbc,never()).update(anyString(),any(Object[].class));
 }

 @Test void weightOnlySourceCannotSmugglePieceHold(){var row=line("10.00",0,8);row.put("allocations",List.of(Map.of("ownerId","8","weight","10.00","quantity",1)));assertThrows(ServiceException.class,()->normalize(new StorageReservationService(balances(false,"100.00","0.00"),mock(FactoryScope.class)),key(),Map.of("type","PRODUCTION_OUT","customerId","8"),List.of(row)));}

 @Test void expiredTemporarySessionCanBeExplicitlyReleased(){
  var jdbc=mock(JdbcTemplate.class);var scope=mock(FactoryScope.class);when(scope.resolveFactory(1L)).thenReturn(1L);when(scope.isAdmin()).thenReturn(true);String sid=key();var reads=new java.util.concurrent.atomic.AtomicInteger();
  when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(i->{if(!i.getArgument(0,String.class).contains("current_status"))return List.of();boolean released=reads.getAndIncrement()>0;return List.of(Map.of("session_id",sid,"status",released?"RELEASED":"TEMP","current_status",released?"RELEASED":"EXPIRED","version",released?2:1,"data","[]","expiresAt","2000-01-01 00:00:00"));});
  try(var security=mockStatic(com.ruoyi.common.utils.SecurityUtils.class)){security.when(com.ruoyi.common.utils.SecurityUtils::getUserId).thenReturn(9L);var out=new StorageReservationService(jdbc,scope).change(sid,"release",Map.of("factoryId",1,"requestId",key(),"reservationVersion",1));assertEquals("RELEASED",out.get("status"));verify(jdbc).update(contains("WHERE session_id=? AND status='TEMP'"),eq(sid));}
 }

 @Test void writeAvailabilityAndLedgerProtectionUseCurrentReadsAfterFactoryLock()throws Exception {
  var jdbc=balances(false,"100.00","0.00");var service=new StorageReservationService(jdbc,mock(FactoryScope.class));String sid=key();normalize(service,sid,Map.of("type","PRODUCTION_OUT","customerId","8"),List.of(line("10.00",0,8)));service.protect(1,2,null,new BigDecimal("100.00"),0,new BigDecimal("-10.00"),0);
  verify(jdbc,atLeastOnce()).queryForList(eq("SELECT net_weight weight,quantity FROM metal_storage_stock WHERE factory_id=? AND id=? FOR UPDATE"),eq(1L),eq(2L));verify(jdbc,atLeastOnce()).queryForList(eq("SELECT weight,quantity FROM metal_storage_ownership WHERE factory_id=? AND stock_id=? AND owner_id=? FOR UPDATE"),eq(1L),eq(2L),eq(8L));verify(jdbc,atLeastOnce()).queryForList(argThat(q->q.startsWith("SELECT r.weight,r.quantity")&&q.endsWith(" FOR UPDATE")),any(Object[].class));verify(jdbc,never()).queryForList(startsWith("SELECT COALESCE(SUM(r.weight)"),any(Object[].class));
 }

 @Test void rawDispatchCanNormalizeItsSyntheticCustomerAllocation()throws Exception {var service=new StorageReservationService(balances(false,"100.00","0.00"),mock(FactoryScope.class));var result=normalize(service,key(),Map.of("type","RAW_OUT","customerId","8"),List.of(line("10.00",0,8)));assertEquals("10.00",result.get(0).get("netWeight"));assertEquals(0L,result.get(0).get("quantity"));}

 @Test void consumeRejectsEmptyPartialDuplicateOrPhysicallyMismatchedSources(){
  var jdbc=mock(JdbcTemplate.class);String sid=key(),local=key();when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(i->i.getArgument(0,String.class).contains("status='BOUND'")?List.of(Map.of("session_id",sid)):List.of(Map.of("reservation_line_id",local,"stock_id",2,"owner_id",0,"weight",new BigDecimal("30.00"),"quantity",0),Map.of("reservation_line_id",local,"stock_id",2,"owner_id",8,"weight",new BigDecimal("20.00"),"quantity",0)));
  var service=new StorageReservationService(jdbc,mock(FactoryScope.class));var row=line("50.00",0,0);row.put("measurementMode","WEIGHT");row.put("reservationLineId",local);var doc=Map.<String,Object>of("type","PRODUCTION_OUT");
  for(var parts:List.of(List.<Map<String,Object>>of(),List.<Map<String,Object>>of(Map.of("ownerId","0","weight","30.00","quantity",0)),List.<Map<String,Object>>of(Map.of("ownerId","0","weight","25.00","quantity",0),Map.of("ownerId","0","weight","25.00","quantity",0)),List.<Map<String,Object>>of(Map.of("ownerId","0","weight","10.00","quantity",0),Map.of("ownerId","8","weight","10.00","quantity",0)))){row.put("allocations",parts);assertThrows(ServiceException.class,()->service.consume(1,3,doc,List.of(row)));}verify(jdbc,never()).update(contains("CONSUMED"),any(Object[].class));
 }
 @Test void productionConfirmationRequiresExecutionOrAutoBatchFieldsAndValidatesBeforePersisting(){
  var jdbc=balances(false,"100.00","0.00");
  when(jdbc.queryForList(startsWith("SELECT customer_id FROM metal_production_order"),any(Object[].class))).thenReturn(List.of(Map.of("customer_id",8)));
  when(jdbc.queryForList(startsWith("SELECT order_id,batch_id,status FROM metal_production_execution"),any(Object[].class))).thenReturn(List.of(Map.of("order_id",10,"status","OPEN")));
  var scope=mock(FactoryScope.class);when(scope.resolveFactory(1L)).thenReturn(1L);when(scope.isAdmin()).thenReturn(true);var production=mock(ProductionExecutionService.class);doThrow(new ServiceException("执行批次已经结算或阶段不匹配")).when(production).validateDocument(eq(1L),anyMap(),anyList());var service=new StorageReservationService(jdbc,scope,production);
  var body=new LinkedHashMap<String,Object>(Map.of("factoryId",1,"requestId",key(),"sessionId",key(),"reservationVersion",0,"type","PRODUCTION_OUT","orderId",10,"lines",List.of(line("10.00",0,8))));
  try(var security=mockStatic(com.ruoyi.common.utils.SecurityUtils.class)){
   security.when(com.ruoyi.common.utils.SecurityUtils::getUserId).thenReturn(9L);
   assertTrue(assertThrows(ServiceException.class,()->service.replace(body)).getMessage().contains("自动建批"));
   verify(production).resolveSourceDocument(eq(1L),anyMap());
   verify(production,never()).ensureExecutionForOutbound(eq(1L),anyMap(),anyList());
   verify(production,never()).validateDocument(eq(1L),anyMap(),anyList());
   body.put("executionId",11);
   assertEquals("执行批次已经结算或阶段不匹配",assertThrows(ServiceException.class,()->service.replace(body)).getMessage());
   verify(production).validateDocument(eq(1L),anyMap(),anyList());
   verify(jdbc,never()).update(anyString(),any(Object[].class));
  }
 }
}
