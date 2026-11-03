package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ruoyi.common.exception.ServiceException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class MeasurementBoundaryTest {
 private Map<String,Object> target(boolean current){
  return new HashMap<>(Map.of("processing_policy_version",2,"process_role","POST_PROCESS","measurement_mode","PIECE","sequence_no",1L,"item_id",10L,"batch_id",20L,"metal_type_id",1L,"product_snapshot",current?"{\"measurementPolicyVersion\":2}":"{}"));
 }
 private JdbcTemplate database(boolean current){
  var jdbc=mock(JdbcTemplate.class);
  when(jdbc.queryForObject(anyString(),eq(String.class),any(Object[].class))).thenReturn(current?"{\"measurementPolicyVersion\":2}":"{}");
  when(jdbc.queryForObject(anyString(),eq(BigDecimal.class),any(Object[].class))).thenReturn(BigDecimal.TEN);
  when(jdbc.queryForObject(anyString(),eq(Long.class),any(Object[].class))).thenReturn(5L);
  return jdbc;
 }
 private void output(boolean current,long quantity)throws Throwable{
  WarehouseService.validateDownstreamOutput(database(current),target(current),quantity);
 }
 @Test void firstPostProcessCannotOutputSixFromFivePieces(){assertThrows(ServiceException.class,()->output(true,6));}
 @Test void firstPostProcessAcceptsFiveActualPieces(){assertDoesNotThrow(()->output(true,5));}
 @Test void newPostProcessCannotTakeWeightSemiEvenWithSmeltingQualification(){
  var stock=Map.<String,Object>of("category","SEMI","measurement_mode","WEIGHT","smelting_status","PASSED","metal_type_id_snapshot",1L);
  assertThrows(ServiceException.class,()->ProcessingInputService.require(database(true),stock,target(true)));
 }
 @Test void legacyWeightConversionKeepsItsSavedRules(){
  var stock=Map.<String,Object>of("category","SEMI","measurement_mode","WEIGHT","smelting_status","PASSED","metal_type_id_snapshot",1L);
  assertDoesNotThrow(()->ProcessingInputService.require(database(false),stock,target(false)));
  assertDoesNotThrow(()->output(false,6));
 }
 @Test void missingVersionIsLegacyAndCurrentMarkerIsRecognized(){
  assertFalse(MeasurementPolicy.current(Map.of()));assertFalse(MeasurementPolicy.current(Map.of("measurementPolicyVersion",1)));
  assertTrue(MeasurementPolicy.current(Map.of("measurementPolicyVersion",2)));
 }
}
