package com.ruoyi.metal.service;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ProcessingPolicyTest {
 @Test void productRoutesKeepCallerModesIncludingPiecePreparationAndWeightSmelting(){
  assertEquals(List.of("PIECE","WEIGHT","PIECE"),ProcessingPolicy.route("PRODUCTION",List.of("酸洗","融定","焊接"),List.of("PIECE","WEIGHT","PIECE")).stream().map(ProcessingPolicy.Step::measurementMode).toList());
 }
 @Test void nonFinalPostProcessCanUseWeightButFinalStepMustUsePieces(){
  assertEquals(List.of("WEIGHT","PIECE"),ProcessingPolicy.route("PRODUCTION",List.of("焊接","打磨"),List.of("WEIGHT","PIECE")).stream().map(ProcessingPolicy.Step::measurementMode).toList());
  assertThrows(RuntimeException.class,()->ProcessingPolicy.route("PRODUCTION",List.of("焊接","打磨"),List.of("PIECE","WEIGHT")));
  assertThrows(RuntimeException.class,()->ProcessingPolicy.route("PRODUCTION",List.of("融定"),List.of("WEIGHT")));
  assertEquals("PIECE",ProcessingPolicy.route("PRODUCTION",List.of("融定"),List.of("PIECE")).get(0).measurementMode());
 }
 @Test void preparationCannotFollowPieceFormation(){
  for(String type:List.of("酸洗","碎料","粉料","旧锅清理"))assertThrows(RuntimeException.class,()->ProcessingPolicy.route("PRODUCTION",List.of("融定",type,"焊接"),List.of("PIECE","WEIGHT","PIECE")));
 }
 @Test void acidAndPreparationRemainRawUntilSmelting(){
  var route=ProcessingPolicy.route("PRODUCTION",List.of("旧锅清理","碎料","酸洗","融定","闸板"),Collections.nCopies(5,"PIECE"));
  assertEquals(List.of("RAW","RAW","RAW","SEMI","FINISHED"),route.stream().map(ProcessingPolicy.Step::outputStage).toList());
  assertEquals("PIECE",route.get(2).measurementMode());assertEquals("ACID_WASH",route.get(2).processRole());
 }
 @Test void lastSmeltingIsFinishedAndRawOnlyNeverFinished(){
  assertEquals("FINISHED",ProcessingPolicy.route("PRODUCTION",List.of("酸洗","融定"),List.of("WEIGHT","PIECE")).get(1).outputStage());
  assertEquals("RAW",ProcessingPolicy.route("RAW_PROCESSING",List.of("酸洗"),List.of("PIECE")).get(0).outputStage());
  assertEquals(List.of("WEIGHT","WEIGHT"),ProcessingPolicy.route("RAW_PROCESSING",List.of("碎料","酸洗"),List.of("PIECE","WEIGHT")).stream().map(ProcessingPolicy.Step::measurementMode).toList());
  assertThrows(RuntimeException.class,()->ProcessingPolicy.route("RAW_PROCESSING",List.of("融定"),List.of("PIECE")));
  assertThrows(RuntimeException.class,()->ProcessingPolicy.route("PRODUCTION",List.of("融定","酸洗"),List.of("PIECE","WEIGHT")));
 }
 @Test void invalidMeasurementModeIsRejectedInsteadOfRewritten(){
  assertThrows(RuntimeException.class,()->ProcessingPolicy.route("PRODUCTION",List.of("焊接","打磨"),List.of("INVALID","PIECE")));
 }
 @Test void snapshotsChooseNewStageAndLegacyRemainsLastStepBased(){
  assertEquals("RAW_PROCESS_IN",ProcessingPolicy.outputType(Map.of("processing_policy_version",2,"output_stage","RAW"),1,1));
  assertEquals("FINISHED_IN",ProcessingPolicy.outputType(Map.of(),1,1));
  assertEquals("SEMI_IN",ProcessingPolicy.outputType(Map.of(),1,2));
 }
 @Test void preparationOnlyRequiresRawTaskAndPostRouteCanUseQualifiedSemi(){
  assertThrows(RuntimeException.class,()->ProcessingPolicy.route("PRODUCTION",List.of("酸洗"),List.of("WEIGHT")));
  assertEquals("POST_PROCESS",ProcessingPolicy.route("PRODUCTION",List.of("闸板","线切割"),List.of("PIECE","PIECE")).get(0).processRole());
 }
}
