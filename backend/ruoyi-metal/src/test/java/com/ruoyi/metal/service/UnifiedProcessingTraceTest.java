package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.util.*;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.metal.catalog.ProductRecord;
import com.ruoyi.metal.security.FactoryScope;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class UnifiedProcessingTraceTest {
 private static final class Inventory {
  final JdbcTemplate jdbc=mock(JdbcTemplate.class);final FactoryScope scope=mock(FactoryScope.class);
  final Map<String,Object> stock=new HashMap<>(Map.of("inventory_id",1L,"id",1L,"factory_id",2L,"warehouse_id",3L,"category","RAW","origin_batch_id",4L,"net_weight",BigDecimal.TEN,"quantity",0L));
  Inventory(){when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(call->{
   String sql=call.getArgument(0);
   if(sql.startsWith("SELECT t.*"))return List.of(stock);
   if(sql.startsWith("SELECT factory_name"))return List.of(Map.of("factory_name","厂"));
   if(sql.startsWith("SELECT name FROM metal_warehouse"))return List.of(Map.of("name","库"));
   if(sql.contains("FROM metal_inventory i LEFT JOIN metal_material"))return List.of(Map.of("material_type","块料","customer_company_name","客户"));
   if(sql.contains("FROM metal_process_batch b")&&sql.contains("origin_work_order_id"))return List.of(Map.of("origin_work_order_id",5L,"origin_work_order_no","WO5","origin_item_id",6L,"origin_process_id",7L,"origin_batch_no","B4","origin_output_stage","RAW"));
   return List.of();
  });}
  Map<String,Object> detail(){return new WarehouseService(jdbc,scope).detail("inventory",1L);}
 }
 @Test void rawOutputInventoryReturnsItsActualSourceWorkItemAndBatch(){
  var f=new Inventory();var result=f.detail();
  assertEquals("5",result.get("originWorkOrderId"));assertEquals("WO5",result.get("originWorkOrderNo"));
  assertEquals("6",result.get("originItemId"));assertEquals("7",result.get("originProcessId"));
  assertEquals("B4",result.get("originBatchNo"));assertEquals("RAW",result.get("originOutputStage"));
  verify(f.scope).check(2L);verify(f.jdbc,never()).update(anyString(),any(Object[].class));
 }
 @Test void oldInventoryWithoutOriginReturnsExplicitNullSourceFields(){
  var f=new Inventory();f.stock.remove("origin_batch_id");var result=f.detail();
  for(String key:List.of("originWorkOrderId","originWorkOrderNo","originItemId","originProcessId","originBatchNo","originOutputStage")){assertTrue(result.containsKey(key),key);assertNull(result.get(key),key);}
  verify(f.jdbc,never()).queryForList(contains("origin_work_order_id"),any(Object[].class));
 }
 @Test void deniedInventoryScopeDoesNotReadOriginOrLedger(){
  var f=new Inventory();doThrow(new ServiceException("跨厂",403)).when(f.scope).check(2L);
  assertThrows(ServiceException.class,f::detail);
  verify(f.jdbc,times(1)).queryForList(anyString(),any(Object[].class));
 }
 @Test void stockLedgerProjectsDownstreamContextAlongsideItsHistoricalFacts(){
  var f=new Inventory();f.detail();String ledger=queries(f.jdbc).stream().filter(sql->sql.startsWith("SELECT l.*,l.ledger_id")).findFirst().orElseThrow();
  for(String field:List.of("o.work_order_id","work_order_no","o.batch_id","b.item_id","b.process_id","adjustment_reason","delta_gross_weight","delta_impurity"))assertTrue(ledger.contains(field),field);
 }
 @Test void rawStocksAreIncludedWithoutCallingSemiReleaseAccounting(){
  var jdbc=mock(JdbcTemplate.class);var raw=new HashMap<String,Object>(Map.of("inventory_id",8L,"category","RAW","net_weight",BigDecimal.TEN,"quantity",0L));
  when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(call->{String sql=call.getArgument(0);
   if(sql.startsWith("SELECT * FROM metal_work_order"))return List.of(Map.of("factory_id",2L,"status","IN_PROGRESS"));
   if(sql.startsWith("SELECT i.*,b.item_id"))return List.of(raw);
   return List.of();
  });
  var result=new SemiFlowService(jdbc,mock(FactoryScope.class)).overview(5L);
  assertEquals(List.of(raw),result.get("stocks"));assertEquals(BigDecimal.TEN,raw.get("unreleased_weight"));
  verify(jdbc,never()).queryForList(startsWith("SELECT release_id FROM metal_semi_release"),eq(8L));
  String stocks=queries(jdbc).stream().filter(sql->sql.startsWith("SELECT i.*,b.item_id")).findFirst().orElseThrow();
  assertTrue(stocks.contains("'RAW'"));assertTrue(stocks.contains("'SEMI'"));assertTrue(stocks.contains("b.status='SETTLED'"));
  String progress=queries(jdbc).stream().filter(sql->sql.startsWith("SELECT s.item_id")).findFirst().orElseThrow();
  assertTrue(progress.contains("s.process_role"));assertTrue(progress.contains("s.output_stage"));
 }
 @Test void reportProjectionCarriesSavedStageAndMeasurement(){
  var jdbc=mock(JdbcTemplate.class);when(jdbc.queryForList(startsWith("SELECT CAST(work_order_id"),any(Object[].class))).thenReturn(List.of(Map.of("factoryId","2")));
  new WorkOrderService(jdbc,null,mock(FactoryScope.class),null).reports(5L);
  String reports=queries(jdbc).stream().filter(sql->sql.startsWith("SELECT CAST(r.report_id")).findFirst().orElseThrow();
  for(String column:List.of("b.process_role processRole","b.output_stage outputStage","b.measurement_mode measurementMode"))assertTrue(reports.contains(column),column);
 }
 @Test void productPlanProbeReturnsVersionWithoutChangingItsSavedSnapshot(){
  var jdbc=mock(JdbcTemplate.class);var products=mock(ProductService.class);var product=new ProductRecord();
  product.setCode("P1");product.setName("杆");product.setCategory("铱杆实心");product.setDimensions(Map.of("diameter","1"));product.setProcesses(List.of());product.setVersion(9);
  when(products.detail(1L)).thenReturn(product);
  when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(call->call.<String>getArgument(0).startsWith("SELECT factory_id")?List.of(Map.of("factory_id",2L)):List.of(Map.of("product_id",1L)));
  var service=new WorkOrderService(jdbc,null,mock(FactoryScope.class),products);
  var result=service.productPlans(1L,3L);assertEquals(9,result.get("version"));assertEquals(3,((Map<?,?>)result.get("snapshot")).get("measurementPolicyVersion"));
  service.availableProducts(3L);String options=queries(jdbc).stream().filter(sql->sql.startsWith("SELECT CAST(p.product_id")).findFirst().orElseThrow();assertTrue(options.contains("p.version"));
 }
 private static List<String> queries(JdbcTemplate jdbc){return mockingDetails(jdbc).getInvocations().stream().filter(call->call.getMethod().getName().equals("queryForList")).map(call->(String)call.getArgument(0)).toList();}
}
