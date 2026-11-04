package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ruoyi.metal.catalog.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ProductMeasurementPolicyTest {
 private ProductRecord product(List<String> types){
  var p=new ProductRecord();p.setFactoryId(7L);p.setMetalTypeId(1L);p.setProductType("杆");p.setCalculationVersion(2);p.setDimensions(Map.of("innerDiameter","0","outerDiameter","1","length","2"));
  var steps=new ArrayList<ProductStep>();for(int i=0;i<types.size();i++){var step=new ProductStep();step.setProcessId((long)i+1);step.setProcessName("任意工序名称"+i);step.setLoss(BigDecimal.ONE);step.setMeasurementMode(i==types.size()-1?"PIECE":i==0?"PIECE":"WEIGHT");steps.add(step);}p.setProcesses(steps);return p;
 }
 private ProductService service(List<String> types){
  var jdbc=mock(JdbcTemplate.class);var rules=mock(QuoteRuleService.class);
  when(rules.metalName(1L)).thenReturn("铱");when(rules.volumeRuleForMetal(any())).thenReturn(Map.of("piValue","3.14","curvedBottomFactor","1.03"));when(rules.rule(1L)).thenReturn(Map.of("density","22.65"));
  when(jdbc.queryForList(startsWith("SELECT process_id"),any(Object[].class))).thenReturn(List.of(Map.of("process_id",1L)));
  var rows=new ArrayList<Map<String,Object>>();for(int i=0;i<types.size();i++)rows.add(Map.of("process_id",(long)i+1,"type_name",types.get(i)));
  when(jdbc.queryForList(startsWith("SELECT p.process_id,t.type_name"),any(Object[].class))).thenReturn(rows);
  return new ProductService(null,FactoryCatalogTestFixtures.scope(),jdbc,rules);
 }
 @Test void preparationAndIntermediateModesAreSavedWithoutStageNormalization(){
  var types=List.of("酸洗","融定","焊接");var p=product(types);service(types).prepare(p,null);
  assertEquals(List.of("PIECE","WEIGHT","PIECE"),p.getProcesses().stream().map(ProductStep::getMeasurementMode).toList());
 }
 @Test void postOnlyIsPieceAndFinalMeltingIsPiece(){
  for(var types:List.of(List.of("焊接","打磨"),List.of("融定"))){var p=product(types);service(types).prepare(p,null);assertTrue(p.getProcesses().stream().allMatch(s->"PIECE".equals(s.getMeasurementMode())));}
 }
 @Test void invalidStageOrderCannotBeSaved(){var types=List.of("融定","碎料","焊接");assertThrows(RuntimeException.class,()->service(types).prepare(product(types),null));}
 @Test void selectablePostProcessingModeIsNotReplacedWithPiece(){
  var types=List.of("焊接","打磨");var p=product(types);p.getProcesses().get(0).setMeasurementMode("WEIGHT");service(types).prepare(p,null);
  assertEquals(List.of("WEIGHT","PIECE"),p.getProcesses().stream().map(ProductStep::getMeasurementMode).toList());
 }
 @Test void finalWeightModeAndInvalidEnumsAreRejected(){
  var types=List.of("焊接","打磨");var finalWeight=product(types);finalWeight.getProcesses().get(1).setMeasurementMode("WEIGHT");
  assertThrows(com.ruoyi.common.exception.ServiceException.class,()->service(types).prepare(finalWeight,null));
  for(String mode:List.of("COUNT","weight",""," PIECE ")){var p=product(types);p.getProcesses().get(0).setMeasurementMode(mode);assertThrows(com.ruoyi.common.exception.ServiceException.class,()->service(types).prepare(p,null),mode);}
 }
 @Test void editingProductModesDoesNotMutateThePreviousRecord(){
  var types=List.of("酸洗","融定","焊接");var old=product(types);old.setId(42L);old.setCategory("铱杆");
  var current=product(types);current.getProcesses().get(0).setMeasurementMode("WEIGHT");service(types).prepare(current,old);
  assertEquals(List.of("PIECE","WEIGHT","PIECE"),old.getProcesses().stream().map(ProductStep::getMeasurementMode).toList());
  assertEquals(List.of("WEIGHT","WEIGHT","PIECE"),current.getProcesses().stream().map(ProductStep::getMeasurementMode).toList());
 }
 @Test void duplicateProcessesAndInvalidLossStayRejected(){
  var types=List.of("焊接","打磨");var duplicate=product(types);duplicate.getProcesses().get(1).setProcessId(1L);assertThrows(RuntimeException.class,()->service(types).prepare(duplicate,null));
  var loss=product(types);loss.getProcesses().get(0).setLoss(new BigDecimal("100.01"));assertThrows(RuntimeException.class,()->service(types).prepare(loss,null));
 }
 @Test void historicalPieceLossCannotBeCarriedIntoWeightMode(){
  var types=List.of("焊接","打磨");var old=product(types);old.setCategory("铱杆");old.getProcesses().get(0).setLossUnit("G_PER_PIECE");old.getProcesses().get(0).setLoss(new BigDecimal("0.123456"));
  var changed=product(types);changed.getProcesses().get(0).setMeasurementMode("WEIGHT");changed.getProcesses().get(0).setLossUnit("G_PER_PIECE");changed.getProcesses().get(0).setLoss(new BigDecimal("0.123456"));
  var error=assertThrows(com.ruoyi.common.exception.ServiceException.class,()->service(types).prepare(changed,old));assertEquals("按重量管理须先重新配置损耗百分比",error.getMessage());
 }
 @Test void historicalPieceLossStaysUnconvertedWhileModeIsPiece(){
  var types=List.of("焊接","打磨");var old=product(types);old.setCategory("铱杆");var changed=product(types);
  for(var p:List.of(old,changed)){p.getProcesses().get(0).setLossUnit("G_PER_PIECE");p.getProcesses().get(0).setLoss(new BigDecimal("0.123456"));}
  service(types).prepare(changed,old);assertEquals(new BigDecimal("0.123456"),changed.getProcesses().get(0).getLoss());assertEquals("G_PER_PIECE",changed.getProcesses().get(0).getLossUnit());
 }
}
