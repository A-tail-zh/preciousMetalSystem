package com.ruoyi.framework.audit;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;import java.util.*;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.transaction.PlatformTransactionManager;
class AuditRecorderTest{
 private AuditContext context(){AuditContext c=new AuditContext();c.path="/metal/quotation/save";c.verb="POST";c.actorId=1L;return c;}
 private Map<String,Object> quotation(){return new LinkedHashMap<>(Map.of("quote_id",42L,"factory_id",7L,"quote_no","QT_TEST_42","item_snapshot","[{\"category\":\"test\",\"quantity\":2,\"totalPrice\":\"100.00\"}]","total_price","100.00"));}
 @Test void newQuoteUsesSavedRecordAndMapResultId(){JdbcTemplate j=mock(JdbcTemplate.class);when(j.queryForList(anyString(),any(Object[].class))).thenAnswer(call->{String sql=call.getArgument(0);return sql.contains("WHERE request_id")?List.of():List.of(quotation());});AuditRecorder r=new AuditRecorder(j,mock(PlatformTransactionManager.class));AuditContext c=context();Map<String,Object> body=Map.of("requestId","test-request","factoryId",999L);assertTrue(r.snapshot(c,body,null).isEmpty());var after=r.snapshot(c,body,Map.of("id","42","factoryId","999"));assertEquals(Set.of(7L),c.factories);assertEquals("QT_TEST_42",c.businessCode);assertEquals("metal_saved_quotation",c.objectType);assertFalse(c.replayed);assertEquals("100.00",((Map<?,?>)after.get("42")).get("total_price"));verify(j).queryForList("SELECT * FROM metal_saved_quotation WHERE quote_id=?",42L);}
 @Test void existingRequestIsMarkedReplayedAndReadFromDatabase(){JdbcTemplate j=mock(JdbcTemplate.class);when(j.queryForList(anyString(),any(Object[].class))).thenAnswer(call->call.<String>getArgument(0).contains("WHERE request_id")?List.of(Map.of("id",42L)):List.of(quotation()));AuditRecorder r=new AuditRecorder(j,mock(PlatformTransactionManager.class));AuditContext c=context();assertFalse(r.snapshot(c,Map.of("requestId","test-request"),null).isEmpty());assertTrue(c.replayed);assertEquals(Set.of(7L),c.factories);assertEquals("QT_TEST_42",c.businessCode);}
 @Test void metalRuleRecordUsesItsMetalIdForBothSnapshots(){
  var jdbc=mock(JdbcTemplate.class);when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(new LinkedHashMap<>(Map.of("metal_type_id",2L,"density",new java.math.BigDecimal("21.46"),"version",1))));
  var c=new AuditContext();c.path="/metal/quote-rule";c.verb="PUT";c.actorId=1L;
  var one=java.math.BigDecimal.ONE;var body=new com.ruoyi.metal.catalog.MetalQuoteRule(2L,new java.math.BigDecimal("21.455"),one,one,one,one,0);
  var recorder=new AuditRecorder(jdbc,mock(PlatformTransactionManager.class));
  assertEquals(Set.of("2"),recorder.snapshot(c,body,null).keySet());assertEquals(Set.of("2"),recorder.snapshot(c,body,null).keySet());assertEquals("2",c.businessCode);assertEquals("metal_type_quote_rule",c.objectType);assertTrue(c.factories.isEmpty());
  verify(jdbc,times(2)).queryForList("SELECT * FROM metal_type_quote_rule WHERE metal_type_id=?",2L);
 }
 @Test void sharedPiUsesFixedGlobalIdForAuditSnapshots(){
  var jdbc=mock(JdbcTemplate.class);when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(new LinkedHashMap<>(Map.of("rule_id",1L,"pi_value",new java.math.BigDecimal("3.14"),"curved_bottom_factor",new java.math.BigDecimal("1.03")))));
  var c=new AuditContext();c.path="/metal/volume-rule";c.verb="PUT";c.actorId=1L;
  var body=new com.ruoyi.metal.catalog.VolumeRule(new java.math.BigDecimal("3.20"),0,new java.math.BigDecimal("1.035"));
  var snapshot=new AuditRecorder(jdbc,mock(PlatformTransactionManager.class)).snapshot(c,body,null);assertEquals(Set.of("1"),snapshot.keySet());assertEquals("1.03",((Map<?,?>)snapshot.get("1")).get("curved_bottom_factor").toString());assertEquals("metal_volume_rule",c.objectType);verify(jdbc).queryForList("SELECT * FROM metal_volume_rule WHERE rule_id=?",1L);
 }
 @Test void deletedMetalRuleKeepsBeforeAndAfterAuditEvidence(){
  var jdbc=mock(JdbcTemplate.class);when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(new LinkedHashMap<>(Map.of("metal_type_id",2L,"del_flag","0","version",4))),List.of(new LinkedHashMap<>(Map.of("metal_type_id",2L,"del_flag","2","version",5))));
  var c=new AuditContext();c.path="/metal/quote-rule/2";c.verb="DELETE";c.actorId=1L;var recorder=new AuditRecorder(jdbc,mock(PlatformTransactionManager.class));
  assertEquals("0",((Map<?,?>)recorder.snapshot(c,Map.of("version",4),null).get("2")).get("del_flag"));assertEquals("2",((Map<?,?>)recorder.snapshot(c,Map.of("version",4),null).get("2")).get("del_flag"));assertEquals("metal_type_quote_rule",c.objectType);
 }
 @Test void multiWarehouseResultAuditsEveryActualOrder(){
  var jdbc=mock(JdbcTemplate.class);
  when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(call->{
   if(!call.<String>getArgument(0).startsWith("SELECT * FROM metal_warehouse_order WHERE order_id=?"))return List.of();
   Object[] args=(Object[])call.getRawArguments()[1];long id=((Number)args[0]).longValue();
   return List.of(new LinkedHashMap<>(Map.of("order_id",id,"order_no","AUDIT-"+id,"factory_id",7L,"status","POSTED")));
  });
  var c=new AuditContext();c.path="/metal/warehouse-order/inbound-submissions";c.verb="POST";c.actorId=1L;
  var result=Map.of("submissionId","10","orders",List.of(Map.of("id","100"),Map.of("id","101")));
  var after=new AuditRecorder(jdbc,mock(PlatformTransactionManager.class)).snapshot(c,Map.of("requestId","new-submission"),result);
  assertEquals(Set.of("100","101"),after.keySet());assertEquals(Set.of(7L),c.factories);
 }
 @Test void productMetalIdDoesNotSelectAnotherProduct(){assertCatalogTargets("product","product_id",Map.of("id",100L,"metalTypeId",1L),null,Set.of("100"));}
 @Test void materialMetalIdDoesNotSelectAnotherMaterial(){assertCatalogTargets("material","material_id",Map.of("id",100L,"metalTypeId",1L),null,Set.of("100"));}
 @Test void creatingProductDoesNotSnapshotTheMetalIdAsAnExistingProduct(){
  assertCatalogTargets("product","product_id",Map.of("metalTypeId",1L),null,Set.of());
  assertCatalogTargets("product","product_id",Map.of("metalTypeId",1L),"100",Set.of("100"));
 }
 private void assertCatalogTargets(String kind,String key,Map<String,Object> body,Object result,Set<String> expected){
  var jdbc=mock(JdbcTemplate.class);
  when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(call->{
   String sql=call.getArgument(0);if(!sql.startsWith("SELECT * FROM metal_"+kind+" WHERE "))return List.of();
   Object[] args=(Object[])call.getRawArguments()[1];long id=((Number)args[0]).longValue();
   return List.of(new LinkedHashMap<>(Map.of(key,id,"factory_id",7L,"name","catalog-"+id)));
  });
  var c=new AuditContext();c.path="/metal/"+kind;c.verb=result==null?"PUT":"POST";c.actorId=1L;
  assertEquals(expected,new AuditRecorder(jdbc,mock(PlatformTransactionManager.class)).snapshot(c,body,result).keySet());
 }
}
