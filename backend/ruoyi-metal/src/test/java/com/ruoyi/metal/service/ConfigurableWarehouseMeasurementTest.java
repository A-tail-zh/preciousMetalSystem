package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.lang.reflect.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ruoyi.common.exception.ServiceException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfigurableWarehouseMeasurementTest {
 private Map<String,Object> batch(String mode){return new HashMap<>(Map.of("batch_id",7L,"item_id",9L,"processing_policy_version",3,"process_role","POST_PROCESS","measurement_mode",mode,"sequence_no",3L,"product_snapshot","{\"measurementPolicyVersion\":3}"));}
 private Map<String,Object> source(String mode,long quantity,String weight){return new HashMap<>(Map.of("category","SEMI","measurement_mode",mode,"quantity",quantity,"net_weight",new BigDecimal(weight),"type","PRODUCTION_OUT"));}
 private JdbcTemplate input(String mode,long quantity,String weight){var j=mock(JdbcTemplate.class);when(j.queryForObject(anyString(),eq(BigDecimal.class),any(Object[].class))).thenReturn(new BigDecimal(weight));when(j.queryForObject(anyString(),eq(Long.class),any(Object[].class))).thenReturn(quantity);when(j.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(source(mode,quantity,weight)));return j;}
 @Test void weightToPieceStartsARealCountWithoutInventingReferenceCredit(){assertDoesNotThrow(()->WarehouseService.validateDownstreamOutput(input("WEIGHT",0,"100"),batch("PIECE"),10));}
 @Test void weightToWeightDoesNotRequireNonzeroEffectivePieces(){assertDoesNotThrow(()->WarehouseService.validateDownstreamOutput(input("WEIGHT",0,"100"),batch("WEIGHT"),0));}
 @Test void continuousPieceOutputStillCannotExceedNetIssuedPieces(){assertThrows(ServiceException.class,()->WarehouseService.validateDownstreamOutput(input("PIECE",4,"100"),batch("PIECE"),5));}
 @Test void postProcessStillRequiresActualQualifiedSemiWeight(){assertThrows(ServiceException.class,()->WarehouseService.validateDownstreamOutput(input("WEIGHT",0,"0"),batch("PIECE"),10));}
 @Test void markerTwoKeepsItsExistingCountRestriction(){var b=batch("PIECE");b.put("product_snapshot","{\"measurementPolicyVersion\":2}");assertThrows(ServiceException.class,()->WarehouseService.validateDownstreamOutput(input("WEIGHT",0,"100"),b,10));}
 @Test void firstPostProcessStillRejectsSixPiecesFromFiveActualPieceInputs(){var b=batch("PIECE");b.put("sequence_no",1L);assertThrows(ServiceException.class,()->WarehouseService.validateDownstreamOutput(input("PIECE",5,"100"),b,6));}
 @Test void firstPostProcessAcceptsItsFiveActualPieceInputs(){var b=batch("PIECE");b.put("sequence_no",1L);assertDoesNotThrow(()->WarehouseService.validateDownstreamOutput(input("PIECE",5,"100"),b,5));}
 @Test void firstPostProcessWeightInputCanCreateActualPiecesWithinTheSeparatePlan(){var b=batch("PIECE");b.put("sequence_no",1L);assertDoesNotThrow(()->WarehouseService.validateDownstreamOutput(input("WEIGHT",0,"100"),b,10));}
 @Test void piecePreparationAndSmeltingAlsoRespectActualRawPieceInputs(){
  for(String role:List.of("PREPARATION","ACID_WASH","SMELTING")){
   var j=mock(JdbcTemplate.class);var raw=source("PIECE",5,"100");raw.put("category","RAW");raw.put("inventory_id",11L);
   when(j.queryForList(anyString(),any(Object[].class))).thenAnswer(c->{String q=c.getArgument(0);if(q.contains("FROM metal_inventory"))return List.of(Map.of("inventory_id",11L,"origin_batch_id",31L));if(q.contains("FROM metal_process_batch"))return List.of(Map.of("batch_id",31L,"measurement_mode","PIECE","product_snapshot","{\"measurementPolicyVersion\":3}"));assertTrue(q.contains("metal_warehouse_order"));return List.of(raw);});
   var b=batch("PIECE");b.put("process_role",role);assertThrows(ServiceException.class,()->WarehouseService.validateDownstreamOutput(j,b,6),role);assertDoesNotThrow(()->WarehouseService.validateDownstreamOutput(j,b,5),role);
  }
 }
 private void plan(String mode,int quantity,boolean configurable){
  var s=new HashMap<String,Object>(Map.of("processing_policy_version",3,"output_stage","RAW","measurement_mode",mode));
  try{var m=WarehouseService.class.getDeclaredMethod("validateBatchQuantity",Map.class,int.class,boolean.class);m.setAccessible(true);m.invoke(null,s,quantity,configurable);}
  catch(NoSuchMethodException e){fail("missing snapshot-aware RAW batch quantity validation",e);}
  catch(InvocationTargetException e){if(e.getCause() instanceof RuntimeException r)throw r;throw new AssertionError(e.getCause());}
  catch(IllegalAccessException e){throw new AssertionError(e);}
 }
 @Test void newPieceRawStageHasARealPositiveBatchPlan(){assertDoesNotThrow(()->plan("PIECE",10,true));assertThrows(ServiceException.class,()->plan("PIECE",0,true));}
 @Test void legacyRawZeroAndNewWeightRawZeroRemainValid(){assertDoesNotThrow(()->plan("PIECE",0,false));assertDoesNotThrow(()->plan("WEIGHT",0,true));assertThrows(ServiceException.class,()->plan("WEIGHT",10,true));}
 private void modes(JdbcTemplate j,Map<String,Object> b,List<Map<String,Object>> stocks,Long except){
  try{var m=ProcessingInputService.class.getDeclaredMethod("requireBatchModes",JdbcTemplate.class,Map.class,List.class,Long.class);m.setAccessible(true);m.invoke(null,j,b,stocks,except);}
  catch(NoSuchMethodException e){fail("missing batch-wide posted/pending/draft input-mode validation",e);}
  catch(InvocationTargetException e){if(e.getCause() instanceof RuntimeException r)throw r;throw new AssertionError(e.getCause());}
  catch(IllegalAccessException e){throw new AssertionError(e);}
 }
 @Test void piecePostProcessRejectsMixedModesWithinCurrentMultiwarehouseDraft(){assertThrows(ServiceException.class,()->modes(mock(JdbcTemplate.class),batch("PIECE"),List.of(source("PIECE",4,"40"),source("WEIGHT",0,"60")),null));}
 @Test void appendedInputCannotMixWithPostedOrOtherPendingMode(){var j=mock(JdbcTemplate.class);when(j.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(source("PIECE",4,"40")));assertThrows(ServiceException.class,()->modes(j,batch("PIECE"),List.of(source("WEIGHT",0,"60")),null));}
 @Test void sameModeAcrossWarehousesIsAllowedAndOwnPendingFlowIsExcluded(){var j=mock(JdbcTemplate.class);when(j.queryForList(anyString(),any(Object[].class))).thenAnswer(c->{String q=c.getArgument(0);assertTrue(q.contains("ACTIVE"));assertTrue(q.contains("flow_id<>?"));assertTrue(Arrays.asList((Object[])c.getRawArguments()[1]).contains(88L));return List.of(source("WEIGHT",0,"40"));});assertDoesNotThrow(()->modes(j,batch("PIECE"),List.of(source("WEIGHT",0,"60")),88L));}
 @Test void smeltingRawMixingAndWeightOutputsKeepTheirExistingScope(){var b=batch("PIECE");b.put("process_role","SMELTING");var j=mock(JdbcTemplate.class);assertDoesNotThrow(()->modes(j,b,List.of(source("PIECE",4,"40"),source("WEIGHT",0,"60")),null));assertDoesNotThrow(()->modes(j,batch("WEIGHT"),List.of(source("PIECE",4,"40"),source("WEIGHT",0,"60")),null));verifyNoInteractions(j);}
 private void rawHandover(long output){
  var j=mock(JdbcTemplate.class);var b=batch("PIECE");b.put("planned_quantity",10L);b.put("process_role","PREPARATION");
  when(j.queryForObject(anyString(),eq(Long.class),any(Object[].class))).thenAnswer(c->{String q=c.getArgument(0);if(q.contains("'SEMI_IN'"))return output;assertTrue(q.contains("'RAW_RETURN'"),"new RAW unused returns must participate in the pending piece total");return 1L;});
  var raw=source("PIECE",5,"100");raw.put("category","RAW");raw.put("inventory_id",11L);
  when(j.queryForList(anyString(),any(Object[].class))).thenAnswer(c->{String q=c.getArgument(0);if(q.contains("FROM metal_inventory"))return List.of(Map.of("inventory_id",11L,"origin_batch_id",31L));if(q.contains("FROM metal_process_batch"))return List.of(Map.of("batch_id",31L,"measurement_mode","PIECE","product_snapshot","{\"measurementPolicyVersion\":3}"));assertTrue(q.contains("metal_warehouse_order"));return List.of(raw);});
  try{var m=WarehouseWorkflowService.class.getDeclaredMethod("validateHandoverPieces",JdbcTemplate.class,Map.class,long.class);m.setAccessible(true);m.invoke(null,j,b,88L);}
  catch(NoSuchMethodException e){fail("missing pending RAW piece output/return reconciliation",e);}
  catch(InvocationTargetException e){if(e.getCause() instanceof RuntimeException r)throw r;throw new AssertionError(e.getCause());}
  catch(IllegalAccessException e){throw new AssertionError(e);}
 }
 @Test void pendingFiveOutputsPlusOneUnusedRawReturnCannotConsumeFiveInputs(){assertThrows(ServiceException.class,()->rawHandover(5));}
 @Test void pendingFourOutputsPlusOneUnusedRawReturnCanConsumeFiveInputs(){assertDoesNotThrow(()->rawHandover(4));}
}
