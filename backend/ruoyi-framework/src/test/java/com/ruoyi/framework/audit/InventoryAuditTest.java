package com.ruoyi.framework.audit;
import java.util.*;import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.transaction.PlatformTransactionManager;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;
class InventoryAuditTest {
 private AuditContext context(String path){var c=new AuditContext();c.path=path;c.verb="POST";c.actorId=1L;return c;}
 @Test void adjustmentDoesNotInterpretDocumentIdAsInventoryId(){
  var db=mock(JdbcTemplate.class);when(db.queryForList(anyString(),any(Object[].class))).thenAnswer(a->{String q=a.getArgument(0);if(q.contains("FROM metal_inventory"))return List.of(new LinkedHashMap<>(Map.of("inventory_id",11L,"factory_id",7L)));if(q.contains("FROM metal_warehouse_order"))return List.of(new LinkedHashMap<>(Map.of("order_id",99L,"factory_id",7L,"order_no","ADJ_FIXTURE")));return List.of();});
  var c=context("/metal/inventory/11/adjust");var result=new AuditRecorder(db,mock(PlatformTransactionManager.class)).snapshot(c,11L,"99");
  assertEquals(Set.of("11"),result.keySet());assertEquals(Set.of(7L),c.factories);assertEquals("ADJ_FIXTURE",c.businessCode);assertTrue(((Map<?,?>)result.get("11")).containsKey("adjustmentOrder"));
  verify(db,never()).queryForList("SELECT * FROM metal_inventory WHERE inventory_id=?",99L);
  verify(db).queryForList(contains("order_id=? AND factory_id=?"),eq(99L),eq(7L));
 }
 @Test void openingUsesActualWarehouseDocumentForFactoryAndSnapshot(){
  var db=mock(JdbcTemplate.class);when(db.queryForList(anyString(),any(Object[].class))).thenAnswer(a->{String q=a.getArgument(0);if(q.contains("WHERE request_id"))return List.of();if(q.contains("FROM metal_warehouse_order WHERE"))return List.of(new LinkedHashMap<>(Map.of("order_id",99L,"factory_id",7L,"order_no","ADJ_OPENING")));if(q.contains("FROM metal_warehouse_order_line"))return List.of(Map.of("inventory_id",11L));return List.of();});
  var c=context("/metal/inventory/opening");var recorder=new AuditRecorder(db,mock(PlatformTransactionManager.class));Map<String,Object> input=Map.of("requestId","fixture","factoryId",88L);
  assertTrue(recorder.snapshot(c,input,null).isEmpty());var after=recorder.snapshot(c,input,"99");assertEquals(Set.of("99"),after.keySet());assertEquals(Set.of(7L),c.factories);assertEquals("metal_warehouse_order",c.objectType);assertEquals("ADJ_OPENING",c.businessCode);
  assertTrue(mockingDetails(db).getInvocations().stream().noneMatch(i->i.getArgument(0).toString().contains("FROM metal_inventory")));
 }
}
