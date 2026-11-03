package com.ruoyi.metal.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.metal.catalog.WarehouseFlowRequests.Change;
import com.ruoyi.metal.security.FactoryScope;

class WarehouseCancellationTest {
 static class Fixture {
  final JdbcTemplate jdbc=mock(JdbcTemplate.class);
  final FactoryScope scope=mock(FactoryScope.class);
  final Map<Long,Map<String,Object>> roots=new HashMap<>();
  final WarehouseWorkflowService service=new WarehouseWorkflowService(jdbc,scope,new WarehouseService(jdbc,scope),null,null);
  boolean voided;long posted;
  Fixture(){
   when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(c->{String q=c.getArgument(0);Object[] args=(Object[])c.getRawArguments()[1];if(q.startsWith("SELECT * FROM metal_warehouse_flow WHERE flow_id="))return List.of(roots.get(((Number)args[0]).longValue()));if(q.startsWith("SELECT * FROM metal_warehouse_flow_group"))return List.of(Map.of("group_id",10L,"order_id",20L,"processor_id",7L,"keeper_id",7L,"recipient_id",7L,"status","WAITING","order_json","{}"));if(q.startsWith("SELECT order_no,warehouse_name"))return List.of(Map.of("order_no","TEST"));return List.of();});
   when(jdbc.queryForObject(anyString(),eq(Long.class),any(Object[].class))).thenAnswer(c->{String q=c.getArgument(0);Object[] args=(Object[])c.getRawArguments()[2];if(q.contains("metal_warehouse_order"))return posted;if(q.contains("created_batch"))return roots.values().stream().filter(r->Boolean.TRUE.equals(r.get("created_batch"))).count();long except=((Number)args[1]).longValue();return roots.entrySet().stream().filter(e->e.getKey()!=except&&Set.of("WAITING","COLLECTING","RETURNED").contains(e.getValue().get("status"))).count();});
   when(jdbc.update(anyString(),any(Object[].class))).thenAnswer(c->{String q=c.getArgument(0);Object[] args=(Object[])c.getRawArguments()[1];if(q.startsWith("UPDATE metal_warehouse_flow SET status='CANCELLED'"))roots.get(((Number)args[args.length-1]).longValue()).put("status","CANCELLED");if(q.startsWith("UPDATE metal_process_batch SET status='VOID'"))voided=true;return 1;});
  }
  void root(long id,String kind,boolean created,Long closeout){var r=new HashMap<String,Object>();r.put("flow_id",id);r.put("factory_id",2L);r.put("request_actor",7L);r.put("kind",kind);r.put("status","WAITING");r.put("version",0);r.put("created_batch",created);r.put("batch_id",9L);if(closeout!=null)r.put("closeout_id",closeout);roots.put(id,r);}
  void cancel(long id){service.cancel(id,new Change(UUID.randomUUID().toString(),0,"取消隔离申请",null));}
 }
 @Test void handoverOnlyCreatorCanCancelAllCustomerCloseoutKinds(){try(var security=mockStatic(SecurityUtils.class)){
  security.when(SecurityUtils::getUserId).thenReturn(7L);security.when(()->SecurityUtils.hasPermi(anyString())).thenAnswer(c->"metal:warehouse-order:handover".equals(c.getArgument(0)));
  for(String kind:List.of("CUSTOMER_RETURN","STORAGE","OFFSET","HANDOVER")){var f=new Fixture();f.root(1,kind,false,kind.equals("HANDOVER")?null:55L);assertDoesNotThrow(()->f.cancel(1),kind);}
 }}
 @Test void ordinaryCreatorStillNeedsAddAndOtherActorsCannotCancel(){try(var security=mockStatic(SecurityUtils.class)){
  security.when(SecurityUtils::getUserId).thenReturn(7L);security.when(()->SecurityUtils.hasPermi(anyString())).thenAnswer(c->"metal:warehouse-order:handover".equals(c.getArgument(0)));
  var ordinary=new Fixture();ordinary.root(1,"RAW_INBOUND",false,null);assertThrows(ServiceException.class,()->ordinary.cancel(1));
  var closeout=new Fixture();closeout.root(1,"STORAGE",false,55L);security.when(SecurityUtils::getUserId).thenReturn(8L);assertThrows(ServiceException.class,()->closeout.cancel(1));
 }}
 @Test void bothCancellationOrdersClearAnEmptyAutomaticallyCreatedBatch(){try(var security=mockStatic(SecurityUtils.class)){
  security.when(SecurityUtils::getUserId).thenReturn(7L);security.when(()->SecurityUtils.hasPermi(anyString())).thenReturn(true);
  for(boolean creatorFirst:List.of(true,false)){var f=new Fixture();f.root(1,"OUTBOUND",true,null);f.root(2,"OUTBOUND",false,null);f.cancel(creatorFirst?1:2);assertFalse(f.voided);f.cancel(creatorFirst?2:1);assertTrue(f.voided,"creatorFirst="+creatorFirst);}
 }}
 @Test void manualBatchAndPostedMaterialAreNeverVoidedByCancellation(){try(var security=mockStatic(SecurityUtils.class)){
  security.when(SecurityUtils::getUserId).thenReturn(7L);security.when(()->SecurityUtils.hasPermi(anyString())).thenReturn(true);
  var manual=new Fixture();manual.root(1,"OUTBOUND",false,null);manual.cancel(1);assertFalse(manual.voided);
  var posted=new Fixture();posted.root(1,"OUTBOUND",true,null);posted.posted=1;posted.cancel(1);assertFalse(posted.voided);
 }}
}
