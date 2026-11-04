package com.ruoyi.metal.service;

import java.util.*;
import com.ruoyi.common.exception.ServiceException;

/** New orders save these decisions; configuration edits never reinterpret an existing route. */
public final class ProcessingPolicy {
 public static final int VERSION=2;
 public static final int EXECUTION_VERSION=3;
 private static final Set<String> PREPARATION=Set.of("粉料","旧锅清理","碎料","酸洗");
 private ProcessingPolicy(){}
 public record Step(String processRole,String outputStage,String measurementMode){}
 public static int version(Map<String,?> snapshot){Object value=snapshot.get("processing_policy_version");if(value==null)value=snapshot.get("processingPolicyVersion");return value==null?1:Integer.parseInt(value.toString());}
 public static boolean v2(Map<String,?> snapshot){return version(snapshot)>=VERSION;}
 public static boolean currentExecution(Map<String,?> snapshot){return version(snapshot)>=EXECUTION_VERSION;}
 public static List<Step> route(String taskType,List<String> types,List<String> modes){
  if(types.isEmpty()||types.size()!=modes.size()||!Set.of("PRODUCTION","RAW_PROCESSING").contains(taskType))throw new ServiceException("请选择有效任务类型及完整工序");
  boolean raw="RAW_PROCESSING".equals(taskType);int melting=types.indexOf("融定");
  var selectedModes=modes.stream().map(SemiMeasurementRules::mode).toList();
  if(!raw&&"WEIGHT".equals(selectedModes.get(selectedModes.size()-1)))throw new ServiceException("最后一道工序产出成品，须按件管理");
  if(raw&&types.stream().anyMatch(t->!PREPARATION.contains(t)))throw new ServiceException("原料加工任务只能选择前处理工序，不能安排融定或后加工");
  if(!raw&&melting<0&&types.stream().anyMatch(PREPARATION::contains))throw new ServiceException("产品路线的前处理后必须安排融定；仅前处理请创建原料加工任务");
  var result=new ArrayList<Step>();
  for(int i=0;i<types.size();i++){
   String type=types.get(i);boolean before=raw||melting>=0&&i<melting;
   if(before&&!PREPARATION.contains(type)||!before&&PREPARATION.contains(type)||i>melting&&melting>=0&&"融定".equals(type))throw new ServiceException("工序阶段不合法：前处理须位于融定之前，融定不能重复");
   result.add(new Step("酸洗".equals(type)?"ACID_WASH":"融定".equals(type)?"SMELTING":before?"PREPARATION":"POST_PROCESS",before?"RAW":i==types.size()-1?"FINISHED":"SEMI",raw?"WEIGHT":selectedModes.get(i)));
  }
  return List.copyOf(result);
 }
 public static String outputType(Map<String,?> step,long sequence,long last){
  if(!v2(step))return sequence==last?"FINISHED_IN":"SEMI_IN";
  return switch(Objects.toString(step.get("output_stage"),Objects.toString(step.get("outputStage"),""))){case "RAW"->"RAW_PROCESS_IN";case "SEMI"->"SEMI_IN";case "FINISHED"->"FINISHED_IN";default->throw new ServiceException("工序产出阶段快照缺失，请核对工单");};
 }
 public static boolean primaryOutput(String type){return Set.of("RAW_PROCESS_IN","SEMI_IN","FINISHED_IN").contains(type);}
 public static void requireOutput(String type,Map<String,?> step,long sequence,long last){if(!v2(step)){if("RAW_PROCESS_IN".equals(type))throw new ServiceException("历史工单不能使用新原料加工产出类型");OutputStageRules.require(type,sequence,last);return;}if(!type.equals(outputType(step,sequence,last)))throw new ServiceException("产出类型与工单阶段快照不一致");}
 public static long metalId(Map<String,?> snapshot){Object id=snapshot.get("metalTypeId");if(id==null)id=snapshot.get("metal_type_id");if(id==null||Long.parseLong(id.toString())<=0)throw new ServiceException("工单金属快照缺失，请核对");return Long.parseLong(id.toString());}
}
