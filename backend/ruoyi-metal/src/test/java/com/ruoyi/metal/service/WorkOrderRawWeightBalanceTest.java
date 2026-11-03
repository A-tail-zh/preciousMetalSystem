package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.util.*;
import com.ruoyi.metal.security.FactoryScope;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class WorkOrderRawWeightBalanceTest {
 private static Map<String,Object> row(Object... values){var row=new LinkedHashMap<String,Object>();for(int i=0;i<values.length;i+=2)row.put(values[i].toString(),values[i+1]);return row;}
 private Map<String,Object> fact(long line,long batch,String type,String weight,long originWork){
  return row("line_id",line,"order_id",line,"order_no","D"+line,"type",type,"order_status","POSTED","target_work",1L,"target_batch",batch,"target_item",2L,"origin_work",originWork,"origin_item",2L,"category","RAW","net_weight",new BigDecimal(weight),"quantity",0L);
 }
 private Map<String,Object> report(long batch,String issued,String returned,String loss){
  return row("batch_id",batch,"item_id",2L,"batch_no","B"+batch,"status","SETTLED","report_id",batch,"voided",false,"issued_weight",new BigDecimal(issued),"returned_weight",new BigDecimal(returned),"loss_weight",new BigDecimal(loss),"completed_quantity",0,"report_quantity",0);
 }
 private Map<String,Object> stock(long id,String weight){return row("inventory_id",id,"item_id",2L,"category","RAW","measurement_mode","WEIGHT","quantity",0L,"net_weight",new BigDecimal(weight),"ownership_status","NORMAL");}
 @SuppressWarnings("unchecked")
 private Map<String,String> calculate(List<Map<String,Object>> facts,List<Map<String,Object>> stocks,List<Map<String,Object>> reports){
  var jdbc=mock(JdbcTemplate.class);
  when(jdbc.queryForMap(anyString(),any(Object[].class))).thenReturn(row("work_order_id",1L,"factory_id",1L,"order_no","WO1"));
  when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(call->{String sql=call.getArgument(0);
   if(sql.startsWith("WITH relevant_lines"))return facts;
   if(sql.startsWith("SELECT i.*"))return stocks;
   if(sql.startsWith("SELECT b.*"))return reports;
   if(sql.contains("metal_work_order_item"))return List.of(row("item_id",2L,"quantity",0,"product_snapshot","{}","material_snapshot","{\"name\":\"原料\"}"));
   return List.of();
  });
  var summary=new WorkOrderWeightBalanceService(jdbc,mock(FactoryScope.class)).summary(1L,null);
  assertEquals("BALANCED",summary.get("status"));assertEquals(List.of(),summary.get("anomalies"));
  return (Map<String,String>)summary.get("totals");
 }
 @Test void successiveRawStagesCountExternalInputOnceAndKeepRemainingStock(){
  var totals=calculate(List.of(fact(1,1,"PRODUCTION_OUT","100",0),fact(2,1,"RAW_PROCESS_IN","95",1),fact(3,2,"PRODUCTION_OUT","60",1),fact(4,2,"RAW_PROCESS_IN","57",1)),List.of(stock(11,"35"),stock(12,"57")),List.of(report(1,"100","95","5"),report(2,"60","57","3")));
  assertEquals("100.000000",totals.get("INPUT"));assertEquals("92.000000",totals.get("OWN_RAW_STOCK"));assertEquals("60.000000",totals.get("INTERNAL_RAW"));assertEquals("0.000000",totals.get("FINISHED_OUTPUT"));
 }
 @Test void unprocessedReturnOfInternalRawOnlyReducesInternalTransfer(){
  var totals=calculate(List.of(fact(1,1,"PRODUCTION_OUT","100",0),fact(2,1,"RAW_PROCESS_IN","95",1),fact(3,2,"PRODUCTION_OUT","60",1),fact(4,2,"RAW_RETURN","10",1),fact(5,2,"RAW_PROCESS_IN","47",1)),List.of(stock(11,"45"),stock(12,"47")),List.of(report(1,"100","95","5"),report(2,"60","57","3")));
  assertEquals("0.000000",totals.get("RAW_RETURN"));assertEquals("50.000000",totals.get("INTERNAL_RAW"));
 }
 @Test void rawLeavingTheOrderAndReturningUsesItsOwnBoundaryCategory(){
  var outbound=fact(3,3,"PRODUCTION_OUT","50",1);outbound.put("target_work",9L);outbound.put("target_item",8L);
  var returned=fact(4,3,"RAW_RETURN","10",1);returned.put("target_work",9L);returned.put("target_item",8L);
  var totals=calculate(List.of(fact(1,1,"PRODUCTION_OUT","100",0),fact(2,1,"RAW_PROCESS_IN","95",1),outbound,returned),List.of(stock(11,"55")),List.of(report(1,"100","95","5")));
  assertEquals("40.000000",totals.get("CROSS_RAW_OUT"));assertEquals("0.000000",totals.get("CROSS_SEMI_OUT"));
 }
 @Test void rawInventoryAdjustmentIsAdditionalBoundaryInput(){
  var adjust=fact(3,0,"ADJUST","2",1);adjust.put("target_work",0L);
  var totals=calculate(List.of(fact(1,1,"PRODUCTION_OUT","100",0),fact(2,1,"RAW_PROCESS_IN","95",1),adjust),List.of(stock(11,"97")),List.of(report(1,"100","95","5")));
  assertEquals("102.000000",totals.get("INPUT"));assertEquals("2.000000",totals.get("RAW_ADJUSTMENT"));
 }
 @Test void rawStagesPreserveIndependentProcessorAndKeeperWeights(){
  var first=fact(2,1,"RAW_PROCESS_IN","95",1);first.put("processor_net_weight",new BigDecimal("96"));
  var second=fact(4,2,"RAW_PROCESS_IN","57",1);second.put("processor_net_weight",new BigDecimal("58"));
  var firstReport=report(1,"100","96","4");firstReport.put("handover_difference",BigDecimal.ONE);
  var secondReport=report(2,"60","58","2");secondReport.put("handover_difference",BigDecimal.ONE);
  var totals=calculate(List.of(fact(1,1,"PRODUCTION_OUT","100",0),first,fact(3,2,"PRODUCTION_OUT","60",1),second),List.of(stock(11,"35"),stock(12,"57")),List.of(firstReport,secondReport));
  assertEquals("6.000000",totals.get("SETTLED_LOSS"));assertEquals("2.000000",totals.get("HANDOVER_DIFFERENCE"));
 }
 @Test void rawOutgoingOtherThanProductionRemainsOnItsOwnBoundary(){
  var outgoing=fact(3,0,"EXCHANGE_OUT","30",1);outgoing.put("target_work",0L);
  var totals=calculate(List.of(fact(1,1,"PRODUCTION_OUT","100",0),fact(2,1,"RAW_PROCESS_IN","95",1),outgoing),List.of(stock(11,"65")),List.of(report(1,"100","95","5")));
  assertEquals("30.000000",totals.get("OTHER_RAW_OUT"));assertEquals("0.000000",totals.get("FINISHED_OUTPUT"));
 }
}
