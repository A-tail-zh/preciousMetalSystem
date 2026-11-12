package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class ProductionBatchLegacyAdoptionTest {
 private static BigDecimal d(String value){return new BigDecimal(value);}
 @Test void bookOpeningKeepsRootContributionsWithoutClaimingPhysicalPieces(){
  assertEquals(List.of(d("2.40"),d("2.40")),ProductionBatchLegacyAdoptionService.bookParts(d("4.80"),List.of(d("100"),d("100"))));
  assertEquals(List.of(d("24.00"),d("91.20")),ProductionBatchLegacyAdoptionService.bookParts(d("115.20"),List.of(d("1000"),d("3800"))));
  assertEquals(List.of(d("976.00"),d("3708.80")),ProductionBatchLegacyAdoptionService.bookParts(d("4684.80"),List.of(d("1000"),d("3800"))));
 }
 @Test void roundingIsDeterministicAndPreservesTheExactCent(){
  assertEquals(List.of(d("0.01"),d("0.00"),d("0.00")),ProductionBatchLegacyAdoptionService.bookParts(d("0.01"),List.of(d("1"),d("1"),d("1"))));
  assertEquals(List.of(),ProductionBatchLegacyAdoptionService.bookParts(d("0.00"),List.of()));
 }
 @Test void unsupportedSourceCapacityCannotBeInvented(){
  assertThrows(RuntimeException.class,()->ProductionBatchLegacyAdoptionService.bookParts(d("1.01"),List.of(d("1.00"))));
  assertThrows(RuntimeException.class,()->ProductionBatchLegacyAdoptionService.bookParts(d("0.01"),List.of()));
  assertThrows(RuntimeException.class,()->ProductionBatchLegacyAdoptionService.bookParts(d("0.00"),List.of(d("-1"),d("2"))));
 }
 @Test void unknownRootPiecesOnlyPermitWholeMaterialAndDoNotBecomeProportionalCounts(){
  var first=new HashMap<String,Object>();first.put("weight",d("40.00"));first.put("quantity",null);
  var second=new HashMap<String,Object>();second.put("weight",d("60.00"));second.put("quantity",null);
  var sources=List.<Map<String,Object>>of(first,second);
  assertTrue(ProductionBatchMaterialService.wholeUnknownQuantity(sources,d("100.00"),13));
  assertFalse(ProductionBatchMaterialService.wholeUnknownQuantity(sources,d("50.00"),6));
  second.put("quantity",6L);
  assertFalse(ProductionBatchMaterialService.wholeUnknownQuantity(sources,d("100.00"),13));
 }
 private static Map<String,Object> source(long id,String weight,String kind){var source=new HashMap<String,Object>();source.put("id",id);source.put("weight",d(weight));source.put("quantity",null);source.put("source_kind",kind);return source;}
 @SuppressWarnings("unchecked") private static List<Map<String,Object>> output(List<Map<String,Object>> sources,String weight,long pieces,String customer,String factory)throws Exception {
  var jdbc=mock(JdbcTemplate.class);when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(sources);when(jdbc.update(anyString(),any(Object[].class))).thenReturn(1);
  var method=ProductionBatchMaterialService.class.getDeclaredMethod("consumeExecution",long.class,long.class,Long.class,String.class,BigDecimal.class,long.class,BigDecimal.class,BigDecimal.class);method.setAccessible(true);
  return (List<Map<String,Object>>)method.invoke(new ProductionBatchMaterialService(jdbc),1L,2L,null,"OUTPUT",d(weight),pieces,d(customer),d(factory));
 }
 @Test void wholeUnknownMultiRootOutputKeepsRootPiecesUnknown()throws Exception {
  var sources=List.of(source(1,"40.00","CUSTOMER"),source(2,"60.00","FACTORY"));
  var output=output(sources,"100.00",13,"40.00","60.00");assertEquals(2,output.size());assertNull(output.get(0).get("partQuantity"));assertNull(output.get(1).get("partQuantity"));
  var partial=output(sources,"50.00",6,"20.00","30.00");assertEquals(2,partial.size());assertNull(partial.get(0).get("partQuantity"));assertNull(partial.get(1).get("partQuantity"));
 }
 @Test void onlyRealPieceOutputAssignsPiecesToOneUnknownRoot()throws Exception {
  var sources=List.of(source(1,"100.00","FACTORY"));assertNull(output(sources,"100.00",0,"0.00","100.00").get(0).get("partQuantity"));assertEquals(13L,output(sources,"100.00",13,"0.00","100.00").get(0).get("partQuantity"));
 }
}
