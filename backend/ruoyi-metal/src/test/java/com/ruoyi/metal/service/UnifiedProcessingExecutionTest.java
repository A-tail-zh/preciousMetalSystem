package com.ruoyi.metal.service;

import java.util.*;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.metal.catalog.WarehouseRequests.Batch;
import com.ruoyi.metal.security.FactoryScope;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.KeyHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class UnifiedProcessingExecutionTest {
 private static final class Inputs {
  final JdbcTemplate jdbc=mock(JdbcTemplate.class);
  final Map<String,Object> stock=new HashMap<>(Map.of("inventory_id",1L,"category","RAW","reference_id",11L,"factory_id",1L,"origin_batch_id",100L,"washing_status","PASSED","raw_material_kind","METAL"));
  final Map<String,Object> source=new HashMap<>(Map.of("item_id",20L,"work_order_id",2L,"factory_id",1L,"sequence_no",1L,"status","SETTLED"));
  final Map<String,Object> target=new HashMap<>(Map.of("item_id",20L,"work_order_id",2L,"factory_id",1L,"sequence_no",2L,"processing_policy_version",3,"process_role","ACID_WASH","metal_type_id",1L));
  List<Map<String,Object>> sourceRoute=List.of(Map.of("process_id",10L));
  List<Map<String,Object>> targetRoute=List.of(Map.of("process_id",10L));
  Inputs(){
   when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(call->{
    String sql=call.getArgument(0);
    if(sql.startsWith("SELECT inventory_id,origin_batch_id FROM metal_inventory")){var row=new HashMap<String,Object>();row.put("inventory_id",stock.get("inventory_id"));row.put("origin_batch_id",stock.get("origin_batch_id"));return List.of(row);}
    if(sql.startsWith("SELECT b.batch_id,b.measurement_mode,CAST(i.product_snapshot"))return List.of(Map.of("batch_id",stock.get("origin_batch_id"),"measurement_mode","WEIGHT","product_snapshot","{\"measurementPolicyVersion\":2}"));
    if(sql.startsWith("SELECT m.material_type"))return List.of(Map.of("material_type","块料","metal_type_id",1L));
    if(sql.contains("FROM metal_work_order_step"))return ((Number)call.getArgument(1)).longValue()==((Number)source.get("item_id")).longValue()?sourceRoute:targetRoute;
    throw new AssertionError(sql);
   });
   when(jdbc.queryForMap(anyString(),any(Object[].class))).thenAnswer(call->call.<String>getArgument(0).contains("FROM metal_process_batch")?source:new HashMap<String,Object>());
  }
  void require(){ProcessingInputService.require(jdbc,stock,target);}
 }
 @Test void newExecutionRejectsAnotherProductItemRawFromTheSameWorkOrder(){
  var f=new Inputs();f.source.put("item_id",21L);assertThrows(ServiceException.class,f::require);
 }
 @Test void settledPartialRawCanAdvanceBeforeItsWeightStageIsClosed(){assertDoesNotThrow(new Inputs()::require);}
 @Test void newExecutionRejectsUnapprovedNonadjacentInternalRaw(){
  var f=new Inputs();f.target.put("sequence_no",3L);assertThrows(ServiceException.class,f::require);
 }
 @Test void newExecutionRejectsUntracedRawAtALaterPreparationStep(){
  var f=new Inputs();f.stock.remove("origin_batch_id");assertThrows(ServiceException.class,f::require);
 }
 @Test void externalRawAtLaterPreparationMustMatchTheSavedRoute(){
  var f=new Inputs();f.source.put("item_id",21L);f.source.put("work_order_id",3L);
  assertDoesNotThrow(f::require);f.sourceRoute=List.of(Map.of("process_id",99L));assertThrows(ServiceException.class,f::require);
 }
 @Test void qualifiedExternalSmeltingAndFirstPreparationRemainAvailable(){
  var f=new Inputs();f.stock.remove("origin_batch_id");f.target.put("process_role","SMELTING");f.target.put("sequence_no",3L);assertDoesNotThrow(f::require);
  f.target.put("process_role","PREPARATION");f.target.put("sequence_no",1L);assertDoesNotThrow(f::require);
 }
 @Test void existingPolicyTwoKeepsItsRawSourceResults(){
  var f=new Inputs();f.target.put("processing_policy_version",2);f.source.put("item_id",21L);assertDoesNotThrow(f::require);
  f.target.put("sequence_no",3L);assertDoesNotThrow(f::require);
 }
 @Test void realLocalWeightWorkCannotBeCoveredByDownstreamQualification(){
  var jdbc=mock(JdbcTemplate.class);var semi=mock(SemiFlowService.class);
  when(jdbc.queryForObject(anyString(),eq(Long.class),any(Object[].class))).thenAnswer(call->{
   String sql=call.getArgument(0);
   return sql.contains("metal_raw_qualification_use")||sql.contains("processing_policy_version>=3")||sql.startsWith("SELECT COUNT(*) FROM metal_process_batch WHERE item_id=")?1L:0L;
  });
  var step=new HashMap<String,Object>(Map.of("item_id",20L,"sequence_no",1L,"measurement_mode","WEIGHT","weight_stage_closed",false));
  assertFalse(WeightStageProgressService.covered(jdbc,step,semi));
  assertFalse(WeightStageProgressService.externalCovered(jdbc,20,1));
  step.put("weight_stage_closed",true);assertTrue(WeightStageProgressService.covered(jdbc,step,semi));
 }
 @Test void qualificationsStillCoverOldPoliciesAndNewRoutesWithoutLocalWeightWork(){
  var jdbc=mock(JdbcTemplate.class);var semi=mock(SemiFlowService.class);
  when(jdbc.queryForObject(anyString(),eq(Long.class),any(Object[].class))).thenAnswer(call->call.<String>getArgument(0).contains("metal_raw_qualification_use")?1L:0L);
  assertTrue(WeightStageProgressService.covered(jdbc,new HashMap<>(Map.of("item_id",20L,"sequence_no",1L,"measurement_mode","WEIGHT","weight_stage_closed",false)),semi));
 }
 @Test void newBatchCopiesItsSavedExecutionVersion()throws Exception{
  for(int policy:List.of(2,3)){
   var jdbc=mock(JdbcTemplate.class);var scope=mock(FactoryScope.class);
   when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(call->{
    String sql=call.getArgument(0);
    if(sql.startsWith("SELECT factory_id"))return List.of(Map.of("factory_id",1L));
    if(sql.startsWith("SELECT * FROM metal_work_order"))return List.of(Map.of("factory_id",1L,"status","IN_PROGRESS","order_no","WO1"));
    if(sql.startsWith("SELECT s.*"))return List.of(Map.of("work_order_id",2L,"sequence_no",1L,"processing_policy_version",policy,"output_stage","RAW","measurement_mode","WEIGHT","item_quantity",0L,"product_snapshot","{\"metalTypeId\":1,\"code\":\"P1\"}"));
    if(sql.startsWith("SELECT metal_type_id"))return List.of(Map.of("metal_type_id",1L));
    return List.of();
   });
   var saved=new HashMap<Integer,Object>();
   var connection=mock(java.sql.Connection.class);var statement=mock(java.sql.PreparedStatement.class);
   when(connection.prepareStatement(anyString(),anyInt())).thenReturn(statement);
   doAnswer(call->{saved.put(call.getArgument(0),call.getArgument(1));return null;}).when(statement).setObject(anyInt(),any());
   when(jdbc.update(any(PreparedStatementCreator.class),any(KeyHolder.class))).thenAnswer(call->{
    call.<PreparedStatementCreator>getArgument(0).createPreparedStatement(connection);
    call.<KeyHolder>getArgument(1).getKeyList().add(new HashMap<>(Map.of("GENERATED_KEY",100L)));return 1;
   });
   when(jdbc.queryForObject(anyString(),eq(Long.class),any(Object[].class))).thenReturn(0L);
   try(var security=mockStatic(SecurityUtils.class)){
    security.when(SecurityUtils::getUserId).thenReturn(7L);
    assertEquals("100",new WarehouseService(jdbc,scope).createBatch(new Batch(UUID.randomUUID().toString(),2L,20L,10L,0)));
   }
   assertEquals(policy,saved.get(12));
  }
 }
}
