package com.ruoyi.framework.audit;

import java.util.*;
import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WarehouseSubmissionReplayAuditTest {
 private static final String REQUEST="00000000-0000-0000-0000-000000000001";
 private static final Set<String> ORDERS=Set.of("100","101");
 private static class Fixture {
  final JdbcTemplate jdbc=mock(JdbcTemplate.class);
  final AuditRecorder recorder=new AuditRecorder(jdbc,mock(PlatformTransactionManager.class));
  final AuditContext context=new AuditContext();
  final Map<String,Object> body=Map.of("requestId",REQUEST,"factoryId",7L);
  final String parent;boolean exists=true;int parentLookups;
  Fixture(String parent,String direction){
   this.parent=parent;context.path="/metal/warehouse-order/"+direction+"-submissions";context.verb="POST";context.actorId=9L;context.actorFactory=7L;
   when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(call->{
    String sql=call.getArgument(0);Object[] args=(Object[])call.getRawArguments()[1];
    boolean parentQuery=sql.contains("JOIN metal_warehouse_flow")||sql.contains("JOIN metal_inbound_submission")||sql.contains("JOIN metal_outbound_submission");
    if(parentQuery){parentLookups++;boolean actorBound=sql.contains("request_actor")&&Arrays.asList(args).contains(9L);boolean factoryBound=sql.contains("factory_id")&&Arrays.asList(args).contains(7L);
     return exists&&actorBound&&factoryBound&&sql.contains("JOIN "+parent)?List.of(Map.of("id",100L),Map.of("id",101L)):List.of();}
    if(sql.startsWith("SELECT * FROM metal_warehouse_order WHERE order_id=?"))return List.of(new LinkedHashMap<>(Map.of("order_id",args[0],"factory_id",7L,"order_no","AUDIT-"+args[0],"status","WAITING_IN")));
    if(sql.startsWith("SELECT * FROM metal_warehouse_order_line WHERE order_id=?"))return List.of(Map.of("line_id",1000L));
    if(sql.contains("WHERE warehouse_flow_id=?"))return List.of(Map.of("id",100L),Map.of("id",101L));
    // Parent UUID never equals a derived warehouse-order request UUID.
    return List.of();
   });
  }
  Map<String,Object> result(boolean reused){return Map.of("reused",reused,"orders",List.of(Map.of("id","100"),Map.of("id","101")));}
  Map<String,Object> before(){context.before=recorder.snapshot(context,body,null);return context.before;}
  Map<String,Object> after(boolean reused){return recorder.snapshot(context,body,result(reused));}
 }
 private void sequentialReplay(String parent,String direction){
  var f=new Fixture(parent,direction);assertEquals(ORDERS,f.before().keySet());assertFalse(f.context.replayed,"a matching UUID is not a successful replay before business validation");
  var after=f.after(true);assertEquals(ORDERS,after.keySet());assertEquals(f.context.before,after);assertTrue(f.context.replayed);
 }
 @Test void modernInboundParentResolvesBothOriginalOrders(){sequentialReplay("metal_warehouse_flow","inbound");}
 @Test void modernOutboundParentResolvesBothOriginalOrders(){sequentialReplay("metal_warehouse_flow","outbound");}
 @Test void legacyInboundParentResolvesBothOriginalOrders(){sequentialReplay("metal_inbound_submission","inbound");}
 @Test void legacyOutboundParentResolvesBothOriginalOrders(){sequentialReplay("metal_outbound_submission","outbound");}
 @Test void firstSuccessfulSubmissionIsNotAReplay(){var f=new Fixture("metal_warehouse_flow","inbound");f.exists=false;assertTrue(f.before().isEmpty());f.exists=true;assertEquals(ORDERS,f.after(false).keySet());assertFalse(f.context.replayed);assertTrue(f.context.before.isEmpty());}
 @Test void concurrentCommitAfterEmptyBeforeUsesOriginalReplayState(){
  var f=new Fixture("metal_warehouse_flow","inbound");f.exists=false;assertTrue(f.before().isEmpty());f.exists=true;
  var after=f.after(true);assertTrue(f.context.replayed);assertEquals(ORDERS,f.context.before.keySet());assertEquals(f.context.before,after);
  var changes=(Map<?,?>)f.recorder.event(f.context,"SUCCESS","",after).get("changes");assertEquals("REPLAY_RETURN_STATE",changes.get("beforeSnapshotBasis"));
 }
 private void failedReplayKeepsSnapshotBasis(int status){
  var f=new Fixture("metal_warehouse_flow","inbound");f.exists=false;assertTrue(f.before().isEmpty());f.exists=true;
  var after=f.after(true);assertTrue(f.context.replayed);assertEquals(ORDERS,f.context.before.keySet());
  TransactionSynchronizationManager.initSynchronization();
  try{
   f.recorder.success(f.context,after);
   for(var callback:TransactionSynchronizationManager.getSynchronizations())callback.afterCompletion(status);
   var payload=ArgumentCaptor.forClass(String.class);
   verify(f.jdbc).update(eq("INSERT IGNORE INTO audit_outbox(event_id,event_json) VALUES(?,?)"),eq(f.context.event),payload.capture());
   var event=JSON.parseObject(payload.getValue());var changes=event.getJSONObject("changes");
   assertEquals("SYSTEM_ERROR",event.getString("code"));assertEquals(true,event.getBoolean("replayed"));
   assertEquals(ORDERS,changes.getJSONObject("before").keySet());assertTrue(changes.getJSONObject("after").isEmpty());
   assertEquals("REPLAY_RETURN_STATE",changes.getString("beforeSnapshotBasis"));
   assertTrue(f.context.logged,"the failure audit must be persisted");
  }finally{TransactionSynchronizationManager.clearSynchronization();}
 }
 @Test void concurrentReplayRollbackKeepsReturnedBeforeSnapshotBasis(){failedReplayKeepsSnapshotBasis(TransactionSynchronization.STATUS_ROLLED_BACK);}
 @Test void concurrentReplayUnknownCommitKeepsReturnedBeforeSnapshotBasis(){failedReplayKeepsSnapshotBasis(TransactionSynchronization.STATUS_UNKNOWN);}
 @Test void refusedChangedContentIsNotPrematurelyMarkedReplayed(){var f=new Fixture("metal_warehouse_flow","inbound");assertEquals(ORDERS,f.before().keySet());assertFalse(f.context.replayed);var event=f.recorder.event(f.context,"BUSINESS_ERROR","content mismatch",null);assertEquals(false,event.get("replayed"));assertTrue(((Map<?,?>)((Map<?,?>)event.get("changes")).get("after")).isEmpty());}
 @Test void anotherActorCannotReadOriginalParentSnapshot(){var f=new Fixture("metal_warehouse_flow","inbound");f.context.actorId=10L;assertTrue(f.before().isEmpty());assertFalse(f.context.replayed);}
 @Test void anotherFactoryCannotReadOriginalParentSnapshot(){var f=new Fixture("metal_inbound_submission","inbound");f.context.actorFactory=8L;assertTrue(f.before().isEmpty());assertEquals(0,f.parentLookups);assertFalse(f.context.replayed);}
}
