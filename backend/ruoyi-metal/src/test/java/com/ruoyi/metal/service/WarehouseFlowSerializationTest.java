package com.ruoyi.metal.service;

import static org.junit.jupiter.api.Assertions.*;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.metal.catalog.WarehouseRequests.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class WarehouseFlowSerializationTest {
 @Test void pendingDocumentsAreValidJsonAndRoundTrip(){
  var line=new Line(null,null,"RAW",11L,null,new BigDecimal("90.00"),0L,null,null,null,null,null,null,null,null,null,null,null,12L,0,"CUSTOMER");
  var order=new Order("test",1L,2L,"RAW_IN",LocalDate.of(2026,11,3),null,List.of(3L),3L,null,null,null,null,null,null,false,null,null,List.of(line),null,null);
  String encoded=WarehouseWorkflowService.json(order);
  assertTrue(JSON.isValid(encoded),encoded);
  assertEquals(order,JSON.parseObject(encoded,Order.class));
 }
}
