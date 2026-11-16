package com.ruoyi.metal.service;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class WeightStageProgressTest {
 @Test void qualifiedSmeltingInputIsCoverageWithoutPieceOrAcidReporting(){
  var jdbc=mock(JdbcTemplate.class);var semi=mock(SemiFlowService.class);
  when(jdbc.queryForObject(anyString(),eq(Long.class),any(Object[].class))).thenAnswer(call->call.<String>getArgument(0).contains("metal_raw_qualification_use")?1L:0L);
  assertTrue(WeightStageProgressService.covered(jdbc,new HashMap<>(Map.of("item_id",2L,"sequence_no",1L,"measurement_mode","WEIGHT","weight_stage_closed",false)),semi));
  verifyNoInteractions(semi);
  var sql=org.mockito.ArgumentCaptor.forClass(String.class);
  verify(jdbc,atLeastOnce()).queryForObject(sql.capture(),eq(Long.class),any(Object[].class));
  String qualification=sql.getAllValues().stream().filter(value->value.contains("metal_raw_qualification_use")).findFirst().orElseThrow();
  assertTrue(qualification.contains("'SMELTING'"));assertTrue(qualification.contains("'SETTLED'"));assertTrue(qualification.contains("'POSTED'"));assertTrue(qualification.contains("'RAW_RETURN'"));
  assertFalse(qualification.contains("completed_quantity"));
 }
 @Test void rawWeightStageClosesByActualSettledWorkWithoutPiecePlan(){
  var jdbc=mock(JdbcTemplate.class);var semi=mock(SemiFlowService.class);
  when(jdbc.queryForObject(anyString(),eq(Long.class),any(Object[].class))).thenAnswer(call->call.<String>getArgument(0).startsWith("SELECT COUNT(*) FROM metal_process_batch WHERE item_id=")?1L:0L);
  var step=new HashMap<String,Object>(Map.of("item_id",2L,"sequence_no",2L,"measurement_mode","WEIGHT","weight_stage_closed",true,"quantity",0,"output_stage","RAW"));
  assertTrue(WeightStageProgressService.covered(jdbc,step,semi));
  step.put("weight_stage_closed",false);assertFalse(WeightStageProgressService.covered(jdbc,step,semi));
  verifyNoInteractions(semi);
 }
 @Test void absentOrInvalidQualificationDoesNotCoverUnclosedRawStage(){
  var jdbc=mock(JdbcTemplate.class);var semi=mock(SemiFlowService.class);
  when(jdbc.queryForObject(anyString(),eq(Long.class),any(Object[].class))).thenReturn(0L);
  assertFalse(WeightStageProgressService.covered(jdbc,new HashMap<>(Map.of("item_id",2L,"sequence_no",1L,"measurement_mode","WEIGHT","weight_stage_closed",false)),semi));
  verifyNoInteractions(semi);
 }
}
