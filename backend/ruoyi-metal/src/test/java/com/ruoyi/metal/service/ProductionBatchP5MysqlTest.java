package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Reuses the P1-P4 real transaction fixture, including its fresh-schema and port guards. */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProductionBatchP5MysqlTest extends ProductionBatchP4MysqlTest {
 private long reviewer=1;
 @Override Map<String,Object> document(String type,List<Map<String,Object>> lines){var body=super.document(type,lines);body.put("reviewerId",String.valueOf(reviewer));return body;}
 @Override Map<String,Object> approve(Map<String,Object> submitted){
  if(reviewer==1)return super.approve(submitted);
  var previous=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
  org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(reviewerActor());
  try{return super.approve(submitted);}finally{org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(previous);}
 }
 private org.springframework.security.core.Authentication reviewerActor(){
  var user=new com.ruoyi.common.core.domain.entity.SysUser();user.setUserId(reviewer);user.setUserName("p5-worker");user.setFactoryId(F);
  var actor=new com.ruoyi.common.core.domain.model.LoginUser(reviewer,1L,user,Set.of("*:*:*"));
  return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(actor,null,actor.getAuthorities());
 }
 @Override long output(long batch,long execution,String weight,int quantity,int step){
  if(reviewer==1)return super.output(batch,execution,weight,quantity,step);
  var previous=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
  org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(reviewerActor());
  try{return super.output(batch,execution,weight,quantity,step);}finally{org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(previous);}
 }
 @Test @Order(60) void sameProcessingAccepts30Then70WithoutCreatingChildren(){
  login();tx.executeWithoutResult(status->{long batch=id(createBatch(100).get("batchId")),e=start(batch,1),raw=rawStock();issue(batch,e,raw,slice(raw,0),"1000.00",100,0);long identities=jdbc.queryForObject("SELECT COUNT(*) FROM metal_production_batch",Long.class);output(batch,e,"300.00",30,1);assertEquals("700.00",materials().remaining(e).get("weight"));assertEquals(70L,((Number)materials().remaining(e).get("quantity")).longValue());output(batch,e,"700.00",70,1);close(e);assertEquals(identities,jdbc.queryForObject("SELECT COUNT(*) FROM metal_production_batch",Long.class));assertEquals(100,jdbc.queryForObject("SELECT SUM(quantity) FROM metal_production_execution_event WHERE execution_id=? AND event_type='OUTPUT' AND reversed=0",Integer.class,e));assertEquals("0.00",materials().remaining(e).get("weight"));assertAllBalanced();status.setRollbackOnly();});
 }
 @Test @Order(61) void independentPeopleKeepSeparateInputOutputAndResponsibility(){
  login();tx.executeWithoutResult(status->{jdbc.update("INSERT INTO sys_user(user_id,dept_id,user_name,nick_name,password,status,del_flag,factory_id) VALUES(2,103,'p5-worker','P5 second worker','not_a_login_hash','0','0',9001)");jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(2,1)");long parent=id(createBatch(100).get("batchId"));var children=planSplit(parent);long a=id(children.get(0).get("batchId")),b=id(children.get(1).get("batchId"));long ea=start(a,1);var startB=command();startB.put("batchVersion",batchVersion(b));startB.put("intent","START_STAGE");startB.put("orderStepId","98011");startB.put("processorId","2");long eb=id(batches().start(b,startB).get("executionId"));long raw=rawStock();issue(a,ea,raw,slice(raw,0),"300.00",30,0);output(a,ea,"300.00",30,1);close(ea);assertEquals("OPEN",jdbc.queryForObject("SELECT status FROM metal_production_execution WHERE id=?",String.class,eb));assertEquals(0,jdbc.queryForObject("SELECT input_quantity+output_quantity FROM metal_production_execution WHERE id=?",Integer.class,eb));reviewer=2;issue(b,eb,raw,slice(raw,0),"700.00",70,0);output(b,eb,"700.00",70,1);close(eb);assertEquals(1L,jdbc.queryForObject("SELECT processor_id FROM metal_production_execution WHERE id=?",Long.class,ea));assertEquals(2L,jdbc.queryForObject("SELECT processor_id FROM metal_production_execution WHERE id=?",Long.class,eb));assertEquals(30,jdbc.queryForObject("SELECT output_quantity FROM metal_production_execution WHERE id=?",Integer.class,ea));assertEquals(70,jdbc.queryForObject("SELECT output_quantity FROM metal_production_execution WHERE id=?",Integer.class,eb));assertAllBalanced();status.setRollbackOnly();});
 }
 @Test @Order(62) void confirmedWeightAndPieceAllocationsAreIndependentAndNeverInventMeasuredEvidence(){
  login();tx.executeWithoutResult(status->{long parent=id(createBatch(100).get("batchId")),e=start(parent,1),raw=rawStock();issue(parent,e,raw,slice(raw,0),"1000.00",100,0);output(parent,e,"1000.00",100,1);close(e);var body=splitCommand(parent,"STOCK_SPLIT",List.of(stockResult("30",30,stockPart(parent,"200.00",30)),stockResult("70",70,stockPart(parent,"800.00",70))));var original=counts();var measured=stockPart(parent,"200.00",30);measured.put("allocationBasis","MEASURED");var invalid=splitCommand(parent,"STOCK_SPLIT",List.of(stockResult("30",30,measured),stockResult("70",70,stockPart(parent,"800.00",70))));assertThrows(com.ruoyi.common.exception.ServiceException.class,()->transforms().split(invalid));assertEquals(original,counts());var result=transforms().split(body);var children=StorageSupport.maps(result.get("results"));var first=materials().stockContext(F,id(children.get(0).get("batchId"))).get(0);var second=materials().stockContext(F,id(children.get(1).get("batchId"))).get(0);assertEquals(new BigDecimal("200.00"),new BigDecimal(first.get("netWeight").toString()));assertEquals(30L,((Number)first.get("quantity")).longValue());assertEquals(new BigDecimal("800.00"),new BigDecimal(second.get("netWeight").toString()));assertEquals(70L,((Number)second.get("quantity")).longValue());assertAllBalanced();status.setRollbackOnly();});
 }
 @Test @Order(63) void powderWeightProductionKeeps30And70PlansSeparateFromZeroActualPieces(){
  login();tx.executeWithoutResult(status->{jdbc.update("UPDATE metal_production_order_step SET measurement_mode='WEIGHT',output_form='POWDER' WHERE item_id=?",ITEM);jdbc.update("UPDATE metal_material SET measurement_mode='WEIGHT',material_form='POWDER' WHERE material_id=?",MATERIAL);long parent=id(createBatch(100).get("batchId"));var children=planSplit(parent);assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM metal_storage_batch_slice WHERE batch_id IS NOT NULL",Integer.class));var rawLine=line("1000.00",0);rawLine.put("materialId",String.valueOf(MATERIAL));rawLine.put("ownerId","0");rawLine.put("form","POWDER");rawLine.put("purity","99");long raw=id(StorageSupport.maps(post(document("RAW_IN",List.of(rawLine))).get("lines")).get(0).get("stockId"));for(var child:children){long batch=id(child.get("batchId"));int planned=((Number)child.get("plannedQuantity")).intValue();long e=start(batch,1);String weight=planned==30?"300.00":"700.00";issue(batch,e,raw,slice(raw,0),weight,0,0);output(batch,e,weight,0,1);close(e);assertEquals(planned,jdbc.queryForObject("SELECT planned_quantity FROM metal_production_batch WHERE id=?",Integer.class,batch));assertEquals(0,jdbc.queryForObject("SELECT SUM(quantity) FROM metal_storage_batch_slice WHERE batch_id=?",Integer.class,batch));assertEquals(new BigDecimal(weight),jdbc.queryForObject("SELECT SUM(weight) FROM metal_storage_batch_slice WHERE batch_id=?",BigDecimal.class,batch));}assertAllBalanced();status.setRollbackOnly();});
 }
 @Test @Order(64) void unmappedHistoricalProcessingIsReadableButCannotWriteOrInventItsSource(){
  login();tx.executeWithoutResult(status->{
   jdbc.update("INSERT INTO metal_production_execution(id,factory_id,order_id,order_item_id,order_step_id,processor_id,planned_quantity,inherited_snapshot,create_by,input_weight,output_weight,input_quantity,output_quantity) VALUES(99021,9001,9701,9801,98011,1,100,'[]',1,1000,300,100,30)");
   var original=jdbc.queryForMap("SELECT * FROM metal_production_execution WHERE id=99021");var before=counts();
   var flow=executions().flow(ORDER,F);var record=StorageSupport.maps(flow.get("executions")).stream().filter(e->"99021".equals(e.get("id"))).findFirst().orElseThrow();
   assertEquals(false,record.get("batchMapped"));assertEquals(true,record.get("readOnly"));assertEquals("LEGACY_UNMAPPED",record.get("writeBlockedReasonCode"));assertEquals(false,record.get("canCancel"));assertEquals(false,record.get("canEdit"));
   var report=executions().report(ORDER,F);var historical=StorageSupport.maps(report.get("executions")).stream().filter(e->"99021".equals(e.get("id"))).findFirst().orElseThrow();
   assertEquals("1000.00",historical.get("inputWeight"));assertEquals("300.00",historical.get("outputWeight"));assertEquals(false,historical.get("sourceCompositionKnown"));assertNull(historical.get("customerInputWeight"));assertNull(historical.get("factoryInputWeight"));
   var body=command();body.put("version",0);body.put("reason","history must remain readonly");body.put("physicalChecked",true);body.put("processorId","1");body.put("plannedQuantity",100);
   for(Runnable action:List.<Runnable>of(()->executions().edit(99021,body),()->executions().close(99021,body),()->executions().cancel(99021,body),()->createBatch(100))){var failure=assertThrows(com.ruoyi.common.exception.ServiceException.class,action::run);assertEquals(409,failure.getCode());}
   var doc=command();doc.put("type","PRODUCTION_OUT");doc.put("orderId",String.valueOf(ORDER));doc.put("executionId","99021");assertEquals(409,assertThrows(com.ruoyi.common.exception.ServiceException.class,()->executions().validateDocument(F,doc,List.of())).getCode());
   assertEquals(before,counts());assertEquals(original,jdbc.queryForMap("SELECT * FROM metal_production_execution WHERE id=99021"));assertAllBalanced();status.setRollbackOnly();
  });
 }
 @Test @Order(65) void closingStepOrOrderCannotBypassAnUnmappedClosedHistoricalRecord(){
  login();tx.executeWithoutResult(status->{
   jdbc.update("INSERT INTO metal_production_execution(id,factory_id,order_id,order_item_id,order_step_id,processor_id,planned_quantity,inherited_snapshot,create_by,status,input_weight,output_weight,input_quantity,output_quantity,loss_weight) VALUES(99022,9001,9701,9801,98011,1,100,'[]',1,'CLOSED',1000,1000,100,100,0)");
   var before=counts();var body=command();body.put("version",jdbc.queryForObject("SELECT version FROM metal_production_order WHERE id=?",Integer.class,ORDER));
   for(Runnable action:List.<Runnable>of(()->executions().completeStep(ORDER,98011,body),()->executions().complete(ORDER,body))){var failure=assertThrows(com.ruoyi.common.exception.ServiceException.class,action::run);assertEquals(409,failure.getCode());assertTrue(failure.getMessage().contains("历史"),failure.getMessage());}
   assertEquals(before,counts());assertFalse(ProductionExecutionService.closed(jdbc.queryForObject("SELECT execution_closed FROM metal_production_order_step WHERE id=98011",Integer.class)));status.setRollbackOnly();
  });
 }

 private Map<String,Object> autoRequest(long stock,int planned){
  var l=line("300.00",30);l.put("stockId",String.valueOf(stock));l.put("stockSliceId",String.valueOf(slice(stock,0)));l.put("reservationLineId",UUID.randomUUID().toString());l.put("stockVersion",jdbc.queryForObject("SELECT version FROM metal_storage_stock WHERE id=?",Integer.class,stock));l.put("sliceVersion",jdbc.queryForObject("SELECT version FROM metal_storage_batch_slice WHERE id=?",Integer.class,slice(stock,0)));
  var r=command();r.put("type","PRODUCTION_OUT");r.put("executionMode","NEW");r.put("orderId",String.valueOf(ORDER));r.put("orderItemId",String.valueOf(ITEM));r.put("orderStepId","98011");r.put("processorId","1");r.put("plannedQuantity",planned);r.put("sessionId",UUID.randomUUID().toString());r.put("reservationVersion",0);r.put("lines",List.of(l));return r;
 }
 private void releaseAuto(Map<String,Object> result){var b=command();b.put("reservationVersion",result.get("version"));reservations().change(result.get("sessionId").toString(),"release",b);}
 @Test @Order(90) void warehouseIssueCreatesUnifiedBatchOnceAndCancelReleasesItsPlan(){
  login();tx.executeWithoutResult(status->{long stock=rawStock();var request=autoRequest(stock,30);var a=reservations().replace(request);long batch=id(a.get("batchId")),execution=id(a.get("executionId"));
   var replay=reservations().replace(request);assertEquals(a.get("batchId"),replay.get("batchId"));assertEquals(a.get("executionId"),replay.get("executionId"));assertEquals(a.get("version"),replay.get("version"));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM metal_production_batch WHERE order_item_id=?",Integer.class,ITEM));
   assertEquals(30,jdbc.queryForObject("SELECT planned_quantity FROM metal_production_batch WHERE id=?",Integer.class,batch));assertEquals(batch,jdbc.queryForObject("SELECT batch_id FROM metal_production_execution WHERE id=?",Long.class,execution));
   assertEquals(BigDecimal.ZERO.setScale(2),jdbc.queryForObject("SELECT input_weight FROM metal_production_execution WHERE id=?",BigDecimal.class,execution));
   releaseAuto(a);assertEquals("CANCELLED",jdbc.queryForObject("SELECT lifecycle FROM metal_production_batch WHERE id=?",String.class,batch));assertEquals("CANCELLED",jdbc.queryForObject("SELECT status FROM metal_production_execution WHERE id=?",String.class,execution));
   createBatch(100);assertAllBalanced();status.setRollbackOnly();});
 }
 @Test @Order(91) void autoBatchReviewAndSupplementKeepOneIdentityAndPostedWorkIsNotCancelled(){
  login();tx.executeWithoutResult(status->{long stock=rawStock();var a=reservations().replace(autoRequest(stock,100));long batch=id(a.get("batchId")),execution=id(a.get("executionId"));var d=document("PRODUCTION_OUT",StorageSupport.maps(a.get("lines")));d.put("orderId",String.valueOf(ORDER));d.put("batchId",String.valueOf(batch));d.put("executionId",String.valueOf(execution));d.put("sessionId",a.get("sessionId"));d.put("reservationVersion",a.get("version"));post(d);
   assertEquals(new BigDecimal("300.00"),jdbc.queryForObject("SELECT input_weight FROM metal_production_execution WHERE id=?",BigDecimal.class,execution));
   var next=autoRequest(stock,100);next.put("batchId",String.valueOf(batch));next.put("executionId",String.valueOf(execution));var b=reservations().replace(next);assertEquals(a.get("batchId"),b.get("batchId"));releaseAuto(b);
   assertFalse(executions().cancelUnusedAutoExecution(F,execution,null));assertEquals("ACTIVE",jdbc.queryForObject("SELECT lifecycle FROM metal_production_batch WHERE id=?",String.class,batch));assertAllBalanced();status.setRollbackOnly();});
 }
 @Test @Order(92) void invalidAutoIssueCannotLeaveBatchPlanOrReservations(){
  login();tx.executeWithoutResult(status->{long stock=rawStock();var before=counts();for(String invalid:List.of("later-stage","missing-plan","over-plan","closed-order")){var r=autoRequest(stock,invalid.equals("over-plan")?101:100);if(invalid.equals("later-stage"))r.put("orderStepId","98012");if(invalid.equals("missing-plan"))r.remove("plannedQuantity");if(invalid.equals("closed-order"))jdbc.update("UPDATE metal_production_order SET status='COMPLETED' WHERE id=?",ORDER);assertThrows(com.ruoyi.common.exception.ServiceException.class,()->reservations().replace(r));jdbc.update("UPDATE metal_production_order SET status='CREATED' WHERE id=?",ORDER);assertEquals(before,counts());}status.setRollbackOnly();});
 }
 @Test @Order(93) void anotherLiveSupplementPreventsAutomaticEmptyBatchCancellation(){
  login();tx.executeWithoutResult(status->{long stock=rawStock();var a=reservations().replace(autoRequest(stock,100));var next=autoRequest(stock,100);next.put("executionMode","EXISTING");next.put("batchId",a.get("batchId"));next.put("executionId",a.get("executionId"));var b=reservations().replace(next);releaseAuto(a);assertEquals("ACTIVE",jdbc.queryForObject("SELECT lifecycle FROM metal_production_batch WHERE id=?",String.class,id(a.get("batchId"))));releaseAuto(b);assertEquals("CANCELLED",jdbc.queryForObject("SELECT lifecycle FROM metal_production_batch WHERE id=?",String.class,id(a.get("batchId"))));status.setRollbackOnly();});
 }

 @Test @Order(94) void warehousePermissionCanAutoCreateButDoesNotGrantGeneralProductionExecution(){
  login();tx.executeWithoutResult(status->{long stock=rawStock();var request=autoRequest(stock,30);var scoped=scope();org.mockito.Mockito.when(scoped.isAdmin()).thenReturn(false);var user=new com.ruoyi.common.core.domain.entity.SysUser();user.setUserId(1L);user.setUserName("warehouse-only");user.setFactoryId(F);var actor=new com.ruoyi.common.core.domain.model.LoginUser(1L,1L,user,Set.of("metal:warehouse-order:add"));org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(actor,null,actor.getAuthorities()));
   var service=new StorageReservationService(jdbc,scoped,new ProductionExecutionService(jdbc,scoped));var result=service.replace(request);assertNotNull(result.get("batchId"));assertNotNull(result.get("executionId"));assertEquals(403,assertThrows(com.ruoyi.common.exception.ServiceException.class,()->new ProductionBatchService(jdbc,scoped,new ProductionExecutionService(jdbc,scoped)).create(command())).getCode());status.setRollbackOnly();});
 }
 @Test @Order(95) void processorFailureAfterBatchInsertRollsBackTheWholeReservationTransaction(){
  login();long stock=tx.execute(status->rawStock());var before=counts();var request=autoRequest(stock,30);request.put("processorId","99999999");assertThrows(com.ruoyi.common.exception.ServiceException.class,()->tx.execute(status->reservations().replace(request)));assertEquals(before,counts());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM metal_production_batch WHERE order_item_id=?",Integer.class,ITEM));
 }
 @Test @Order(96) void concurrentRetriesCreateOnlyOneAutomaticIdentity(){
  login();long stock=tx.execute(status->rawStock());var request=autoRequest(stock,30);var pool=java.util.concurrent.Executors.newFixedThreadPool(2);try{var jobs=new ArrayList<java.util.concurrent.Future<Map<String,Object>>>();for(int i=0;i<2;i++)jobs.add(pool.submit(()->{login();try{return tx.execute(status->reservations().replace(com.alibaba.fastjson2.JSON.parseObject(com.alibaba.fastjson2.JSON.toJSONString(request))));}finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}}));var first=jobs.get(0).get(20,java.util.concurrent.TimeUnit.SECONDS);var second=jobs.get(1).get(20,java.util.concurrent.TimeUnit.SECONDS);assertEquals(first.get("batchId"),second.get("batchId"));assertEquals(first.get("executionId"),second.get("executionId"));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM metal_production_batch WHERE order_item_id=?",Integer.class,ITEM));tx.executeWithoutResult(status->releaseAuto(first));assertAllBalanced();}catch(Exception e){throw new RuntimeException(e);}finally{pool.shutdownNow();}
 }

 private Map<String,Object> itemCapacity(){return StorageSupport.maps(orders().options(F,null,"PRODUCTION_OUT").get("orderItems")).stream().filter(i->String.valueOf(ITEM).equals(i.get("id"))).findFirst().orElseThrow();}
 @Test @Order(97) void warehouseOptionsUseRemainingBatchPlanAndRejectOverAllocation(){
  login();tx.executeWithoutResult(status->{long first=id(createBatch(30).get("batchId"));assertEquals(30L,((Number)itemCapacity().get("allocatedQuantity")).longValue());assertEquals(70L,((Number)itemCapacity().get("remainingQuantity")).longValue());assertThrows(com.ruoyi.common.exception.ServiceException.class,()->createBatch(71));createBatch(70);assertEquals(0L,((Number)itemCapacity().get("remainingQuantity")).longValue());jdbc.update("UPDATE metal_production_batch SET lifecycle='CANCELLED' WHERE id=?",first);assertEquals(30L,((Number)itemCapacity().get("remainingQuantity")).longValue());status.setRollbackOnly();});
 }
 @Test @Order(97) void capacityCountsSplitChildrenOnceAndSkipsTheirParent(){
  login();tx.executeWithoutResult(status->{long parent=id(createBatch(100).get("batchId"));planSplit(parent);assertEquals(100L,((Number)itemCapacity().get("allocatedQuantity")).longValue());assertEquals(0L,((Number)itemCapacity().get("remainingQuantity")).longValue());status.setRollbackOnly();});
 }
}
