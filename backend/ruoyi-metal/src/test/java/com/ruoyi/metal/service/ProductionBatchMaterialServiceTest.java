package com.ruoyi.metal.service;

import com.ruoyi.common.exception.ServiceException;
import java.util.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductionBatchMaterialServiceTest {
 @Test void weightInputDoesNotInventAZeroPieceBalance(){
  assertNull(ProductionBatchMaterialService.inputSourceQuantity(true,"WEIGHT",0L));
  assertEquals(10L,ProductionBatchMaterialService.inputSourceQuantity(true,"PIECE",10L));
  assertEquals(0L,ProductionBatchMaterialService.inputSourceQuantity(false,"WEIGHT",0L));
 }
 @Test void weightInputCanProduceTenPiecesButCannotExceedAvailableWeight() throws Exception {
  var jdbc=mock(JdbcTemplate.class);var source=source();source.put("weight","500.00");source.put("quantity",ProductionBatchMaterialService.inputSourceQuantity(true,"WEIGHT",0L));
  when(jdbc.queryForList(startsWith("SELECT * FROM metal_production_material_source WHERE factory_id=? AND execution_id=?"),eq(1L),eq(2L))).thenReturn(List.of(source));
  when(jdbc.update(startsWith("UPDATE metal_production_material_source SET weight=weight-?"),any(),any(),any(),any(),any())).thenReturn(1);
  var consume=ProductionBatchMaterialService.class.getDeclaredMethod("consumeExecution",long.class,long.class,Long.class,String.class,BigDecimal.class,long.class,BigDecimal.class,BigDecimal.class);consume.setAccessible(true);
  var service=new ProductionBatchMaterialService(jdbc);
  var result=(List<?>)consume.invoke(service,1L,2L,null,"OUTPUT",new BigDecimal("500.00"),10L,null,null);
  assertEquals(10L,((Map<?,?>)result.get(0)).get("partQuantity"));
  var error=assertThrows(java.lang.reflect.InvocationTargetException.class,()->consume.invoke(service,1L,2L,null,"OUTPUT",new BigDecimal("501.00"),10L,null,null));
  assertInstanceOf(ServiceException.class,error.getCause());
 }
 private static Map<String,Object> source(){return new LinkedHashMap<>(Map.of("id",3L,"weight","1000.00","quantity",100,"returnable_weight","1000.00","returnable_quantity",100));}
 private static Map<String,Object> allocation(String w,int q){return new LinkedHashMap<>(Map.of("sourceAllocationId","3","netWeight",w,"quantity",q,"allocationBasis","CONFIRMED_BALANCE"));}
 @Test void repeatedWipSourceAcrossResultsCannotExceedOneResponsibility(){
  var jdbc=mock(JdbcTemplate.class);when(jdbc.queryForList(startsWith("SELECT * FROM metal_production_material_source WHERE factory_id=? AND execution_id=?"),eq(1L),eq(2L))).thenReturn(List.of(source()));
  var material=new ProductionBatchMaterialService(jdbc);
  assertThrows(ServiceException.class,()->material.validateWip(1,2,List.of(allocation("600.00",60),allocation("500.00",50))));
  verify(jdbc,never()).update(anyString(),any(Object[].class));
 }
 @Test void transferDoesNotAssumeAllWipIsUnprocessedReturnableMaterial(){
  var jdbc=mock(JdbcTemplate.class);var s=source();s.put("returnable_weight","20.00");s.put("returnable_quantity",2);
  when(jdbc.queryForList(startsWith("SELECT * FROM metal_production_material_source WHERE factory_id=? AND execution_id=?"),eq(1L),eq(2L))).thenReturn(List.of(s));
  var a=allocation("700.00",70);var material=new ProductionBatchMaterialService(jdbc);assertDoesNotThrow(()->material.validateWip(1,2,List.of(a)));
  a.put("returnableWeight","30.00");a.put("returnableQuantity",3);
  assertThrows(ServiceException.class,()->material.validateWip(1,2,List.of(a)));
  var first=allocation("400.00",40);first.put("returnableWeight","20.00");first.put("returnableQuantity",2);
  var second=allocation("300.00",30);second.put("returnableWeight","20.00");second.put("returnableQuantity",2);
  assertThrows(ServiceException.class,()->material.validateWip(1,2,List.of(first,second)));
  verify(jdbc,never()).update(anyString(),any(Object[].class));
 }
 @Test void carryAuthorityCannotReturnAnotherIssueFromTheSameExecution(){
  var jdbc=mock(JdbcTemplate.class);var s=source();s.put("line_id",9L);s.put("stock_id",5L);
  when(jdbc.queryForList(startsWith("SELECT s.*,a.line_id,a.stock_id,a.stock_slice_id"),eq(1L),eq(3L),eq(2L))).thenReturn(List.of(s));
  var line=new LinkedHashMap<String,Object>(Map.of("carryAllocationId","3","stockId","5","issueLineId","8","netWeight","10.00","quantity",1));
  assertThrows(ServiceException.class,()->new ProductionBatchMaterialService(jdbc).requireCarryReturn(1,2,line));
  verify(jdbc,never()).update(anyString(),any(Object[].class));
 }
 @Test void extraPrecisionIsRejectedInsteadOfSilentlyRoundingTheSource(){
  var jdbc=mock(JdbcTemplate.class);when(jdbc.queryForList(startsWith("SELECT * FROM metal_production_material_source WHERE factory_id=? AND execution_id=?"),eq(1L),eq(2L))).thenReturn(List.of(source()));
  assertThrows(ServiceException.class,()->new ProductionBatchMaterialService(jdbc).validateWip(1,2,List.of(allocation("0.001",1))));
  verify(jdbc,never()).update(anyString(),any(Object[].class));
 }
 @Test void readOnlyPreciseSourceReportUsesSnapshotReadsWithoutRowLocks(){
  var jdbc=mock(JdbcTemplate.class);
  when(jdbc.queryForObject(contains("information_schema.tables"),eq(Integer.class))).thenReturn(1);
  when(jdbc.queryForList(startsWith("SELECT * FROM metal_production_material_source WHERE execution_id=?"),eq(2L))).thenReturn(List.of(Map.of("id",3L,"initial_weight","100.00","source_kind","FACTORY")));
  org.springframework.transaction.support.TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
  try{assertEquals(List.of(new BigDecimal("0.00"),new BigDecimal("100.00")),new ProductionSourceBalances(jdbc).net(2));verify(jdbc,never()).queryForList(contains("FOR UPDATE"),any(Object[].class));}
  finally{org.springframework.transaction.support.TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);}
  assertTrue(ProductionBatchMaterialService.currentRead("SELECT * FROM example FOR UPDATE").endsWith("FOR UPDATE"));
 }
 @Test void readOnlyLegacyOutputReportAlsoDropsLocksFromFallbackQueries(){
  var jdbc=mock(JdbcTemplate.class);
  when(jdbc.queryForObject("SELECT order_id FROM metal_production_execution WHERE id=?",Long.class,1L)).thenReturn(7L);
  when(jdbc.queryForObject("SELECT batch_id FROM metal_production_execution WHERE id=?",Long.class,1L)).thenReturn(10L);
  when(jdbc.queryForObject("SELECT processing_type FROM metal_production_order WHERE id=?",String.class,7L)).thenReturn("DIRECT_CUSTOM");
  when(jdbc.queryForList(startsWith("SELECT ev.*,st.origin_order_id"),eq(1L))).thenReturn(List.of(Map.of("customer_weight","40.00","factory_weight","60.00","document_id",2L,"line_id",3L)));
  when(jdbc.queryForList(startsWith("SELECT id,weight,customer_weight,factory_weight FROM metal_production_execution_event"),eq(1L))).thenReturn(List.of(Map.of("id",4L,"weight","10.00","customer_weight","4.00","factory_weight","6.00")));
  org.springframework.transaction.support.TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
  try{assertEquals(List.of(new BigDecimal("36.00"),new BigDecimal("54.00")),new ProductionSourceBalances(jdbc).remaining(1));verify(jdbc,never()).queryForList(contains("FOR UPDATE"),any(Object[].class));verify(jdbc,never()).queryForMap(contains("FOR UPDATE"),any(Object[].class));}
  finally{org.springframework.transaction.support.TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);}
 }
}
