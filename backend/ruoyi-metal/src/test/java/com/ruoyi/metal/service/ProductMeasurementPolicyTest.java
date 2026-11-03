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
  var p=new ProductRecord();p.setMetalTypeId(1L);p.setProductType("杆");p.setCalculationVersion(2);p.setDimensions(Map.of("innerDiameter","0","outerDiameter","1","length","2"));
  var steps=new ArrayList<ProductStep>();for(int i=0;i<types.size();i++){var step=new ProductStep();step.setProcessId((long)i+1);step.setProcessName("任意工序名称"+i);step.setLoss(BigDecimal.ONE);step.setMeasurementMode(i==types.size()-1?"PIECE":i==0?"PIECE":"WEIGHT");steps.add(step);}p.setProcesses(steps);return p;
 }
 private ProductService service(List<String> types){
  var jdbc=mock(JdbcTemplate.class);var rules=mock(QuoteRuleService.class);
  when(rules.metalName(1L)).thenReturn("铱");when(rules.volumeRule()).thenReturn(Map.of("piValue","3.14","curvedBottomFactor","1.03"));when(rules.rule(1L)).thenReturn(Map.of("density","22.65"));
  when(jdbc.queryForList(startsWith("SELECT process_id"),any(Object[].class))).thenReturn(List.of(Map.of("process_id",1L)));
  var rows=new ArrayList<Map<String,Object>>();for(int i=0;i<types.size();i++)rows.add(Map.of("process_id",(long)i+1,"type_name",types.get(i)));
  when(jdbc.queryForList(startsWith("SELECT p.process_id,t.type_name"),any(Object[].class))).thenReturn(rows);
  return new ProductService(null,null,jdbc,rules);
 }
 @Test void actualTypesFixUnitsAndDoNotTrustSuppliedModesOrNames(){
  var types=List.of("酸洗","融定","焊接");var p=product(types);service(types).prepare(p,null);
  assertEquals(List.of("WEIGHT","PIECE","PIECE"),p.getProcesses().stream().map(ProductStep::getMeasurementMode).toList());
 }
 @Test void postOnlyIsPieceAndFinalMeltingIsPiece(){
  for(var types:List.of(List.of("焊接","打磨"),List.of("融定"))){var p=product(types);service(types).prepare(p,null);assertTrue(p.getProcesses().stream().allMatch(s->"PIECE".equals(s.getMeasurementMode())));}
 }
 @Test void invalidStageOrderCannotBeSaved(){var types=List.of("融定","碎料","焊接");assertThrows(RuntimeException.class,()->service(types).prepare(product(types),null));}
}
