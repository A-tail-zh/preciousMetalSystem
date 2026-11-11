package com.ruoyi.metal.service;

import java.util.*;
import com.ruoyi.common.exception.ServiceException;

/** Shared rules for catalog configuration and frozen production routes. */
final class ProductionRoutePolicy {
 private ProductionRoutePolicy() {}
 static void validate(List<Map<String,Object>> steps) {
  if(steps.isEmpty())throw new ServiceException("请配置完整工序路线",400);
  boolean special=false;
  for(int i=0;i<steps.size();i++){
   var s=steps.get(i);String semantic=text(s,"semanticCode");
   if(!Set.of("NORMAL","ACID_WASH","SMELTING").contains(semantic))fail(s,i,"未设置有效工艺类别，请在工序管理的新增或编辑中选择");
   special|=!semantic.equals("NORMAL");
  }
  boolean melted=false,semi=false;
  for(int i=0;i<steps.size();i++){
   var s=steps.get(i);String semantic=text(s,"semanticCode"),category=text(s,"outputCategory");boolean last=i==steps.size()-1;
   if(!Set.of("WEIGHT","PIECE").contains(text(s,"measurementMode")))fail(s,i,"请选择计重或计件");
   if(last&&!semantic.equals("NORMAL"))fail(s,i,"不能作为最后一道工序，请在后面配置产出成品的普通工序");
   if(semantic.equals("ACID_WASH")){
    if(melted)fail(s,i,"酸洗不能位于融定之后");
    if(!category.equals("RAW")||!text(s,"outputForm").equals("METAL"))fail(s,i,"酸洗必须产出原材料，形态为金属料");
   }
   if(semantic.equals("SMELTING")){if(melted)fail(s,i,"同一路线不能重复融定");melted=true;}
   if(last){if(!category.equals("PRODUCT"))fail(s,i,"最后一道工序必须产出成品");}
   else if(special){String expected=melted?"SEMI":"RAW";if(!category.equals(expected))fail(s,i,melted?"融定及之后的中间工序必须产出半成品":"融定前的中间工序必须产出原材料");}
   else {
    if(!Set.of("RAW","SEMI").contains(category))fail(s,i,"普通中间工序只能产出原材料或半成品");
    if(semi&&category.equals("RAW"))fail(s,i,"已经形成半成品，后续工序不能退回原材料");
   }
   semi|=category.equals("SEMI");
  }
 }
 private static String text(Map<String,Object> s,String key){return Objects.toString(s.get(key),"");}
 private static void fail(Map<String,Object> s,int index,String reason){throw new ServiceException("第"+(index+1)+"道工序「"+Objects.toString(s.get("processName"),Objects.toString(s.get("processId"),"未命名"))+"」："+reason,400);}
}
