package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import org.springframework.jdbc.core.JdbcTemplate;
import com.alibaba.fastjson2.JSON;

class ProductionSourceBalancesTest {
 @Test void directAndShortageKeepSourcesAcrossOutputBatchesAndAcrossExecutions(){
  for(String type:List.of("DIRECT_CUSTOM","SHORTAGE_CUSTOM"))for(boolean splitOutput:List.of(true,false)){
   var jdbc=mock(JdbcTemplate.class);
   when(jdbc.queryForObject("SELECT order_id FROM metal_production_execution WHERE id=?",Long.class,1L)).thenReturn(7L);
   when(jdbc.queryForObject("SELECT processing_type FROM metal_production_order WHERE id=?",String.class,7L)).thenReturn(type);
   var original=Map.<String,Object>of("customer_weight","1.00","factory_weight","2.00","document_id",1L,"line_id",1L,"origin_order_id",0L);
   when(jdbc.queryForList(startsWith("SELECT ev.*,st.origin_order_id"),eq(1L))).thenReturn(List.of(original));
   when(jdbc.queryForMap(startsWith("SELECT COALESCE(SUM(returned_weight)"),eq(1L),eq(1L))).thenReturn(Map.of("returned_weight","0.00","factory_returned_weight","0.00"));
   var produced=new ArrayList<Map<String,Object>>();
   for(int i=0;i<(splitOutput?3:1);i++){
    long stock=100L+i,storageEvent=200L+i,productionEvent=10L+i;
    String weight=splitOutput?"1.00":"3.00";
    produced.add(Map.of("id",productionEvent,"weight",weight));
    var receipt=Map.<String,Object>of("id",storageEvent,"action","INBOUND","result",JSON.toJSONString(Map.of("stockId",stock,"netWeight",weight)),"production_event",productionEvent,"execution_id",1L);
    when(jdbc.queryForList(startsWith("SELECT e.id,e.action,CAST(e.result AS CHAR) result,p.id"),eq(stock))).thenReturn(List.of(receipt));
    var physical=new ArrayList<Map<String,Object>>();physical.add(Map.of("id",storageEvent,"action","INBOUND","weight_delta",weight));
    if(!splitOutput)for(int k=0;k<3;k++)physical.add(Map.of("id",500L+k,"action","ISSUE","weight_delta","-1.00"));
    when(jdbc.queryForList(startsWith("SELECT e.id,e.action,CAST(e.result AS CHAR) result,s.weight_delta"),eq(stock))).thenReturn(physical);
   }
   when(jdbc.queryForList(startsWith("SELECT id,weight FROM metal_production_execution_event"),eq(1L))).thenReturn(produced);
   var total=pool("0.00","0.00");
   for(int i=0;i<3;i++){
    long execution=2L+i,line=300L+i,stock=splitOutput?102L-i:100L;
    when(jdbc.queryForObject("SELECT order_id FROM metal_production_execution WHERE id=?",Long.class,execution)).thenReturn(7L);
    var input=Map.<String,Object>of("stock_id",stock,"line_id",line,"origin_order_id",7L,"identity_snapshot","{\"sourceExecutionId\":\"1\"}");
    when(jdbc.queryForList(startsWith("SELECT ev.*,st.origin_order_id"),eq(execution))).thenReturn(List.of(input));
    when(jdbc.queryForList(startsWith("SELECT id,CAST(result AS CHAR) result FROM metal_storage_event"),eq(line))).thenReturn(List.of(Map.of("id",500L+i,"result",JSON.toJSONString(Map.of("stockId",stock,"sentWeight","1.00","receivedWeight","1.00")))));
    var sources=new ProductionSourceBalances(jdbc);var part=sources.net(execution);
    assertEquals(part,sources.loss(execution,new BigDecimal("1.00")),type);
    for(int k=0;k<2;k++)total.set(k,total.get(k).add(part.get(k)));
   }
   assertEquals(pool("1.00","2.00"),total,type+" splitOutput="+splitOutput);
  }
 }
 private static List<BigDecimal> pool(String customer,String factory){return new ArrayList<>(List.of(new BigDecimal(customer),new BigDecimal(factory)));}
 @Test void partialOutputsKeepRemaindersUntilTheLastBatch(){
  var balance=pool("1.00","2.00");
  assertEquals(pool("0.33","0.67"),ProductionSourceBalances.take(balance,new BigDecimal("1.00")));
  assertEquals(pool("0.34","0.66"),ProductionSourceBalances.take(balance,new BigDecimal("1.00")));
  assertEquals(pool("0.33","0.67"),ProductionSourceBalances.take(balance,new BigDecimal("1.00")));
  assertEquals(pool("0.00","0.00"),balance);
 }
 @Test void centTieIsNotRepeatedForEachPartialIssueOrLoss(){
  var balance=pool("0.01","0.01");
  assertEquals(pool("0.01","0.00"),ProductionSourceBalances.take(balance,new BigDecimal("0.01")));
  assertEquals(pool("0.00","0.01"),ProductionSourceBalances.take(balance,new BigDecimal("0.01")));
  assertEquals(pool("0.00","0.00"),balance);
  assertEquals(pool("0.00","0.00"),ProductionSourceBalances.take(balance,new BigDecimal("0.00")));
  assertThrows(com.ruoyi.common.exception.ServiceException.class,()->ProductionSourceBalances.take(balance,new BigDecimal("0.01")));
 }
 @Test void partialReceiptLeavesItsHandoverLossOutOfTheNextPool(){
  var stock=pool("1.00","2.00");var sent=new ArrayList<>(ProductionSourceBalances.take(stock,new BigDecimal("1.00")));
  var received=ProductionSourceBalances.take(sent,new BigDecimal("0.99"));
  assertEquals(new BigDecimal("0.99"),received.get(0).add(received.get(1)));
  assertEquals(new BigDecimal("0.01"),sent.get(0).add(sent.get(1)));
  assertEquals(new BigDecimal("2.00"),stock.get(0).add(stock.get(1)));
 }
}
