package com.ruoyi.metal.service;

import java.util.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Uses the guarded disposable 3323 schema; each scenario rolls back its synthetic business data. */
class AutomaticBatchFlowMysqlTest extends ProductionBatchP4MysqlTest {
 Map<String,Object> automaticIssue(long stock,long slice,String weight,int quantity,int step,Long original){
  var l=line(weight,quantity);l.put("stockId",String.valueOf(stock));l.put("stockSliceId",String.valueOf(slice));l.put("reservationLineId",UUID.randomUUID().toString());l.put("sliceVersion",jdbc.queryForObject("SELECT version FROM metal_storage_batch_slice WHERE id=?",Integer.class,slice));l.put("stockVersion",jdbc.queryForObject("SELECT version FROM metal_storage_stock WHERE id=?",Integer.class,stock));
  long owner=step==1?0:CUSTOMER;l.put("allocations",List.of(Map.of("ownerId",String.valueOf(owner),"weight",weight,"quantity",quantity)));
  return automaticIssueLines(List.of(l),step,original);
 }
 Map<String,Object> automaticIssueLines(List<Map<String,Object>> lines,int step,Long original){
  var reserve=command();reserve.put("sessionId",UUID.randomUUID().toString());reserve.put("reservationVersion",0);reserve.put("type","PRODUCTION_OUT");reserve.put("orderId",String.valueOf(ORDER));reserve.put("orderItemId",String.valueOf(ITEM));reserve.put("orderStepId",String.valueOf(98010+step));reserve.put("processorId","1");reserve.put("executionMode",original==null?"NEW":"EXISTING");if(original!=null)reserve.put("sourceOutboundId",String.valueOf(original));reserve.put("lines",lines);
  var held=reservations().replace(reserve);var replay=reservations().replace(reserve);assertEquals(held.get("executionId"),replay.get("executionId"));
  var body=document("PRODUCTION_OUT",StorageSupport.maps(held.get("lines")));for(String key:List.of("orderId","orderItemId","orderStepId","processorId","executionMode","sourceOutboundId"))if(reserve.containsKey(key))body.put(key,reserve.get(key));body.put("executionId",held.get("executionId"));body.put("batchId",held.get("batchId"));body.put("sessionId",held.get("sessionId"));body.put("reservationVersion",held.get("version"));return post(body);
 }
 Map<String,Object> receipt(long original,String weight,int quantity,boolean finish){
  var l=line(weight,quantity);l.put("productId",String.valueOf(PRODUCT));l.put("purity","99");var source=jdbc.queryForMap("SELECT s.output_category FROM metal_storage_document d JOIN metal_production_execution e ON e.id=d.execution_id JOIN metal_production_order_step s ON s.id=e.order_step_id WHERE d.id=?",original);
  String type=switch(source.get("output_category").toString()){case "RAW"->"PROCESS_RAW_IN";case "PRODUCT"->"PRODUCT_IN";default->"SEMI_IN";};
  if(type.equals("PROCESS_RAW_IN"))l.put("materialId",String.valueOf(MATERIAL));
  var body=document(type,List.of(l));body.put("sourceOutboundId",String.valueOf(original));body.put("finishProcessing",finish);if(finish){body.put("physicalChecked",true);body.put("confirmedLossWeight","0.00");}return body;
 }
 long acidRawStock(boolean piece){
  seedCurrentBusiness();String mode=piece?"PIECE":"WEIGHT";
  jdbc.update("UPDATE metal_material SET measurement_mode=?,material_form='UNTREATED' WHERE material_id=?",mode,MATERIAL);
  jdbc.update("UPDATE metal_production_order_step SET measurement_mode=? WHERE item_id=? AND sequence_no<3",mode,ITEM);
  jdbc.update("UPDATE metal_production_order_step SET semantic_code='ACID_WASH',process_name='酸洗',output_category='RAW',output_form='METAL',output_material_id=? WHERE id=98011",MATERIAL);
  jdbc.update("UPDATE metal_production_order_step SET semantic_code='SMELTING',process_name='融定' WHERE id=98012");
  jdbc.update("UPDATE metal_production_order_step SET process_name='轧板' WHERE id=98013");
  var raw=line("1000.00",piece?100:0);raw.put("materialId",String.valueOf(MATERIAL));raw.put("ownerId","0");raw.put("form","UNTREATED");raw.put("purity","99");
  return id(StorageSupport.maps(post(document("RAW_IN",List.of(raw))).get("lines")).get(0).get("stockId"));
 }
 void rejectWithoutPartialChanges(Runnable action){
  var before=counts();var nested=new org.springframework.transaction.support.TransactionTemplate(tx.getTransactionManager());nested.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_NESTED);
  assertThrows(com.ruoyi.common.exception.ServiceException.class,()->nested.executeWithoutResult(status->action.run()));assertEquals(before,counts());
 }
 @Test @org.junit.jupiter.api.Order(1) void automaticRawAcidPartial400MovesToSmeltingWhile600RemainsInProcess(){
  login();tx.executeWithoutResult(status->{
   long raw=acidRawStock(false);var acid=automaticIssue(raw,slice(raw,0),"1000.00",0,1,null);long acidExecution=id(acid.get("executionId"));
   var pending=submit(receipt(id(acid.get("id")),"400.00",0,false));
   assertEquals("PENDING_REVIEW",pending.get("status"));
   assertTrue(StorageSupport.maps(orders().options(F,null,"PRODUCTION_OUT",null,null,98012L).get("stocks")).isEmpty());
   rejectWithoutPartialChanges(()->automaticIssue(raw,slice(raw,0),"400.00",0,2,null));
   var received=approve(pending);var output=StorageSupport.maps(received.get("lines")).get(0);long stock=id(output.get("stockId")),sourceSlice=id(output.get("stockSliceId"));
   assertEquals("RAW",jdbc.queryForObject("SELECT category FROM metal_storage_stock WHERE id=?",String.class,stock));
   assertEquals("METAL",jdbc.queryForObject("SELECT JSON_UNQUOTE(JSON_EXTRACT(identity_snapshot,'$.form')) FROM metal_storage_stock WHERE id=?",String.class,stock));
   assertTrue(StorageSupport.maps(orders().options(F,null,"PRODUCTION_OUT",null,null,98012L).get("stocks")).stream().anyMatch(row->id(row.get("id"))==stock));
   rejectWithoutPartialChanges(()->automaticIssue(stock,sourceSlice,"400.00",0,3,null));
   var smelt=automaticIssue(stock,sourceSlice,"400.00",0,2,null);assertEquals("POSTED",smelt.get("status"));
   assertNotEquals(acid.get("batchId"),smelt.get("batchId"));assertEquals("OPEN",jdbc.queryForObject("SELECT status FROM metal_production_execution WHERE id=?",String.class,acidExecution));
   assertEquals("600.00",materials().remaining(acidExecution).get("weight"));assertEquals("400.00",materials().remaining(id(smelt.get("executionId"))).get("weight"));
   assertEquals(BigDecimal.ZERO.setScale(2),jdbc.queryForObject("SELECT net_weight FROM metal_storage_stock WHERE id=?",BigDecimal.class,stock));
   assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM metal_storage_debt WHERE work_order_id=?",Integer.class,ORDER));assertAllBalanced();status.setRollbackOnly();
  });
 }
 @Test @org.junit.jupiter.api.Order(1) void automaticPieceAcid40And60RunAlongsideSmelting40(){
  login();tx.executeWithoutResult(status->{
   long raw=acidRawStock(true);var first=automaticIssue(raw,slice(raw,0),"400.00",40,1,null);var received=post(receipt(id(first.get("id")),"400.00",40,false));
   var second=automaticIssue(raw,slice(raw,0),"600.00",60,1,null);var output=StorageSupport.maps(received.get("lines")).get(0);long stock=id(output.get("stockId")),sourceSlice=id(output.get("stockSliceId"));
   var smelt=automaticIssue(stock,sourceSlice,"400.00",40,2,null);assertEquals("POSTED",smelt.get("status"));
   for(var acid:List.of(first,second))assertEquals("OPEN",jdbc.queryForObject("SELECT status FROM metal_production_execution WHERE id=?",String.class,id(acid.get("executionId"))));
   assertEquals(40,jdbc.queryForObject("SELECT input_quantity FROM metal_production_execution WHERE id=?",Integer.class,id(smelt.get("executionId"))));
   assertEquals(60,jdbc.queryForObject("SELECT input_quantity FROM metal_production_execution WHERE id=?",Integer.class,id(second.get("executionId"))));
   assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM metal_production_batch WHERE order_id=?",Integer.class,ORDER));
   assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM metal_production_batch WHERE order_id=? AND planned_quantity IS NOT NULL",Integer.class,ORDER));
   assertEquals(new BigDecimal("1000.00"),jdbc.queryForObject("SELECT SUM(remaining_weight) FROM metal_storage_debt WHERE work_order_id=?",BigDecimal.class,ORDER));assertAllBalanced();status.setRollbackOnly();
  });
 }
 @Test @org.junit.jupiter.api.Order(1) void automaticRawAcidSmeltingAndRollingComplete100WithoutRepeatingDebt(){
  login();tx.executeWithoutResult(status->{
   long raw=acidRawStock(false);var acid=automaticIssue(raw,slice(raw,0),"1000.00",0,1,null);var received=post(receipt(id(acid.get("id")),"1000.00",0,true));
   for(int step=2;step<=3;step++){
    var previous=StorageSupport.maps(received.get("lines")).get(0);var issued=automaticIssue(id(previous.get("stockId")),id(previous.get("stockSliceId")),"1000.00",0,step,null);
    received=post(receipt(id(issued.get("id")),"1000.00",step==3?100:0,true));
    assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM metal_storage_debt WHERE work_order_id=?",Integer.class,ORDER));
    assertEquals(new BigDecimal("1000.00"),jdbc.queryForObject("SELECT SUM(remaining_weight) FROM metal_storage_debt WHERE work_order_id=?",BigDecimal.class,ORDER));assertAllBalanced();
   }
   assertEquals(100,jdbc.queryForObject("SELECT e.output_quantity FROM metal_production_execution e JOIN metal_production_order_step s ON s.id=e.order_step_id WHERE e.order_id=? AND s.output_category='PRODUCT'",Integer.class,ORDER));
   assertEquals(0,jdbc.queryForObject("SELECT SUM(e.output_quantity) FROM metal_production_execution e JOIN metal_production_order_step s ON s.id=e.order_step_id WHERE e.order_id=? AND s.sequence_no<3",Integer.class,ORDER));
   var complete=command();complete.put("version",jdbc.queryForObject("SELECT version FROM metal_production_order WHERE id=?",Integer.class,ORDER));assertEquals("COMPLETED",executions().complete(ORDER,complete).get("status"));assertAllBalanced();status.setRollbackOnly();
  });
 }
 @Test @org.junit.jupiter.api.Order(1) void automaticNoPlanSupplementPartialReceiptsAndSourceValidation(){
  login();tx.executeWithoutResult(status->{seedCurrentBusiness();long raw=rawStock();var first=automaticIssue(raw,slice(raw,0),"300.00",30,1,null);long eid=id(first.get("executionId")),batch=id(first.get("batchId"));assertNull(jdbc.queryForObject("SELECT planned_quantity FROM metal_production_batch WHERE id=?",Long.class,batch));
   var second=automaticIssue(raw,slice(raw,0),"200.00",20,1,null);assertNotEquals(first.get("batchId"),second.get("batchId"));
   var supplement=automaticIssue(raw,slice(raw,0),"100.00",10,1,id(first.get("id")));assertEquals(first.get("executionId"),supplement.get("executionId"));assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM metal_production_batch WHERE factory_id=?",Integer.class,F));
   post(receipt(id(first.get("id")),"100.00",10,false));assertEquals("OPEN",jdbc.queryForObject("SELECT status FROM metal_production_execution WHERE id=?",String.class,eid));post(receipt(id(first.get("id")),"300.00",30,true));assertEquals("CLOSED",jdbc.queryForObject("SELECT status FROM metal_production_execution WHERE id=?",String.class,eid));
   var bad=receipt(id(second.get("id")),"100.00",10,false);bad.put("orderId","999999");assertThrows(com.ruoyi.common.exception.ServiceException.class,()->orders().create(bad));assertAllBalanced();status.setRollbackOnly();});
 }
 @Test @org.junit.jupiter.api.Order(1) void automaticThreeStagesPreserveSourcesAllowOverPlanAndFinishOrder(){
  login();tx.executeWithoutResult(status->{seedCurrentBusiness();long raw=rawStock();var issued=automaticIssue(raw,slice(raw,0),"1000.00",100,1,null);long original=id(issued.get("id"));var received=post(receipt(original,"1000.00",100,true));
   for(int step=2;step<=3;step++){var outLine=StorageSupport.maps(received.get("lines")).get(0);long stock=id(outLine.get("stockId")),sourceSlice=id(outLine.get("stockSliceId"));issued=automaticIssue(stock,sourceSlice,"1000.00",100,step,null);original=id(issued.get("id"));var body=receipt(original,"1000.00",step==3?110:100,true);received=post(body);}
   assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM metal_production_batch WHERE factory_id=?",Integer.class,F));assertEquals(110,jdbc.queryForObject("SELECT SUM(e.output_quantity) FROM metal_production_execution e JOIN metal_production_order_step s ON s.id=e.order_step_id WHERE e.order_id=? AND s.output_category='PRODUCT'",Integer.class,ORDER));assertAllBalanced();var complete=command();complete.put("version",jdbc.queryForObject("SELECT version FROM metal_production_order WHERE id=?",Integer.class,ORDER));assertEquals("COMPLETED",executions().complete(ORDER,complete).get("status"));status.setRollbackOnly();});
 }
 @Test @org.junit.jupiter.api.Order(1) void automaticFinishRejectsUnconfirmedResidualAndCancellationDoesNotConsumePlan(){
  login();tx.executeWithoutResult(status->{seedCurrentBusiness();long raw=rawStock();var issue=automaticIssue(raw,slice(raw,0),"300.00",30,1,null);var pending=submit(receipt(id(issue.get("id")),"200.00",20,true));var nested=new org.springframework.transaction.support.TransactionTemplate(tx.getTransactionManager());nested.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_NESTED);long events=jdbc.queryForObject("SELECT COUNT(*) FROM metal_storage_event",Long.class);assertThrows(com.ruoyi.common.exception.ServiceException.class,()->nested.execute(inner->approve(pending)));assertEquals(events,jdbc.queryForObject("SELECT COUNT(*) FROM metal_storage_event",Long.class));assertEquals("PENDING_REVIEW",jdbc.queryForObject("SELECT status FROM metal_storage_document WHERE id=?",String.class,id(pending.get("id"))));status.setRollbackOnly();});
 }
 @Test @org.junit.jupiter.api.Order(1) void automaticMultipleSourcesPartialOutputKeepsMeasuredCountsAndRootWeights(){
  login();tx.executeWithoutResult(status->{seedCurrentBusiness();long raw=rawStock();var inputs=new ArrayList<Map<String,Object>>();
   for(int n=0;n<2;n++){var issue=automaticIssue(raw,slice(raw,0),"200.00",20,1,null);var received=post(receipt(id(issue.get("id")),"200.00",20,true));var output=StorageSupport.maps(received.get("lines")).get(0);long stock=id(output.get("stockId")),sl=id(output.get("stockSliceId"));var l=line("100.00",10);l.put("stockId",String.valueOf(stock));l.put("stockSliceId",String.valueOf(sl));l.put("sliceVersion",jdbc.queryForObject("SELECT version FROM metal_storage_batch_slice WHERE id=?",Integer.class,sl));l.put("stockVersion",jdbc.queryForObject("SELECT version FROM metal_storage_stock WHERE id=?",Integer.class,stock));l.put("reservationLineId",UUID.randomUUID().toString());l.put("allocations",List.of(Map.of("ownerId",String.valueOf(CUSTOMER),"weight","100.00","quantity",10)));inputs.add(l);}
   var issued=automaticIssueLines(inputs,2,null);long e=id(issued.get("executionId"));assertEquals(2,jdbc.queryForObject("SELECT COUNT(DISTINCT source_allocation_id) FROM metal_production_material_source WHERE execution_id=?",Integer.class,e));
   post(receipt(id(issued.get("id")),"70.00",7,false));post(receipt(id(issued.get("id")),"130.00",13,true));assertEquals(20,jdbc.queryForObject("SELECT output_quantity FROM metal_production_execution WHERE id=?",Integer.class,e));assertAllBalanced();status.setRollbackOnly();});
 }
 @Test @org.junit.jupiter.api.Order(1) void automaticWeightIssuesDoNotInventFinishedPieces(){
  login();tx.executeWithoutResult(status->{seedCurrentBusiness();jdbc.update("UPDATE metal_material SET measurement_mode='WEIGHT' WHERE material_id=?",MATERIAL);jdbc.update("UPDATE metal_production_order_step SET measurement_mode='WEIGHT' WHERE item_id=?",ITEM);var l=line("1000.00",0);l.put("materialId",String.valueOf(MATERIAL));l.put("ownerId","0");l.put("form","METAL");l.put("purity","99");long raw=id(StorageSupport.maps(post(document("RAW_IN",List.of(l))).get("lines")).get(0).get("stockId"));
   var a=automaticIssue(raw,slice(raw,0),"200.00",0,1,null);var b=automaticIssue(raw,slice(raw,0),"300.00",0,1,null);post(receipt(id(a.get("id")),"200.00",0,true));post(receipt(id(b.get("id")),"300.00",0,true));assertEquals(0,jdbc.queryForObject("SELECT SUM(input_quantity+output_quantity) FROM metal_production_execution WHERE order_id=?",Integer.class,ORDER));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM metal_production_batch WHERE order_id=? AND planned_quantity IS NOT NULL",Integer.class,ORDER));assertAllBalanced();status.setRollbackOnly();});
 }


 @Test @org.junit.jupiter.api.Order(1) void automaticReversalSubtractsOutputAndRawReturnSettlesActualRemainder(){
  login();tx.executeWithoutResult(status->{seedCurrentBusiness();long raw=rawStock();var issued=automaticIssue(raw,slice(raw,0),"300.00",30,1,null);long original=id(issued.get("id")),e=id(issued.get("executionId"));var received=post(receipt(original,"100.00",10,false));
   var reverse=command();reverse.put("version",received.get("version"));reverse.put("reviewRound",received.get("reviewRound"));reverse.put("reviewerId","1");reverse.put("reason","isolated reverse measured output");approve(orders().action(id(received.get("id")),"reverse",reverse));assertEquals(0,jdbc.queryForObject("SELECT output_quantity FROM metal_production_execution WHERE id=?",Integer.class,e));
   var returned=line("100.00",10);returned.put("stockId",String.valueOf(raw));returned.put("stockSliceId",String.valueOf(slice(raw,0)));returned.put("issueLineId",StorageSupport.maps(issued.get("lines")).get(0).get("id"));var ret=document("RAW_RETURN",List.of(returned));ret.put("sourceOutboundId",String.valueOf(original));post(ret);post(receipt(original,"200.00",20,true));assertEquals("CLOSED",jdbc.queryForObject("SELECT status FROM metal_production_execution WHERE id=?",String.class,e));assertEquals(new BigDecimal("100.00"),jdbc.queryForObject("SELECT returned_weight FROM metal_production_execution WHERE id=?",BigDecimal.class,e));assertAllBalanced();status.setRollbackOnly();});
 }
 @Test @org.junit.jupiter.api.Order(1000) void automaticConcurrentReplayCreatesOneIdentityAndReleaseCancelsEmptyWork()throws Exception {
  login();long raw=tx.execute(status->{seedCurrentBusiness();return rawStock();});
  var body=dispatchReservation(raw,slice(raw,0),"100.00",10);body.put("type","PRODUCTION_OUT");body.put("executionMode","NEW");body.put("orderId",String.valueOf(ORDER));body.put("orderItemId",String.valueOf(ITEM));body.put("orderStepId","98011");body.put("processorId","1");StorageSupport.maps(body.get("lines")).get(0).put("allocations",List.of(Map.of("ownerId","0","weight","100.00","quantity",10)));
  var pool=java.util.concurrent.Executors.newFixedThreadPool(2);try{
   var a=pool.submit(()->{login();return tx.execute(status->reservations().replace(body));});var b=pool.submit(()->{login();return tx.execute(status->reservations().replace(body));});var first=a.get(20,java.util.concurrent.TimeUnit.SECONDS);assertEquals(first.get("executionId"),b.get(20,java.util.concurrent.TimeUnit.SECONDS).get("executionId"));
   assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM metal_production_batch WHERE factory_id=?",Integer.class,F));tx.executeWithoutResult(status->{var release=command();release.put("reservationVersion",first.get("version"));reservations().change(body.get("sessionId").toString(),"release",release);assertEquals("CANCELLED",jdbc.queryForObject("SELECT status FROM metal_production_execution WHERE id=?",String.class,id(first.get("executionId"))));assertEquals("CANCELLED",jdbc.queryForObject("SELECT lifecycle FROM metal_production_batch WHERE id=?",String.class,id(first.get("batchId"))));});
  }finally{pool.shutdownNow();}
 }

}
