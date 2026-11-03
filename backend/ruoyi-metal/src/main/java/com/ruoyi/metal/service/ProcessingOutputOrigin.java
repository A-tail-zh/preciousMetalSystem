package com.ruoyi.metal.service;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.metal.catalog.WarehouseRequests.Line;

/** Output qualifications come from actual inputs and the saved role, never a target material label. */
record ProcessingOutputOrigin(String powder,String washing,String smelting,int policy,Long verification,String rawKind) {
 static ProcessingOutputOrigin determine(JdbcTemplate jdbc,String type,String category,Line line,Map<String,Object> source,Map<String,Object> inherited,Map<String,Object> batch){
  if(!Set.of("RAW_IN","OPENING").contains(type)&&line.powderOrigin()!=null)throw new ServiceException("加工产出及还料的粉料来源由原始来源继承，不能自行指定");
  if(inherited!=null)return new ProcessingOutputOrigin(Objects.toString(inherited.get("powder_origin"),"UNKNOWN"),Objects.toString(inherited.get("washing_status"),"UNVERIFIED"),Objects.toString(inherited.get("smelting_status"),"UNVERIFIED"),(int)Math.max(1,SemiFlowService.num(inherited.get("origin_policy_version"))),inherited.get("origin_verification_id")==null?null:SemiFlowService.num(inherited.get("origin_verification_id")),"RAW".equals(category)?RawMaterialKinds.kind(inherited.get("raw_material_kind"),source.get("material_type")):null);
  String kind="RAW".equals(category)?RawMaterialKinds.initial(source.get("material_type")):null;
  if("RAW".equals(category)&&Set.of("RAW_IN","OPENING").contains(type)){
   long metal=SemiFlowService.num(source.get("metal_type_id"));
   if(metal==1&&"粉料".equals(source.get("material_type"))&&line.powderOrigin()==null)throw new ServiceException("原料铱粉须登记原生或回收再生来源；旧模板请重新下载");
   var origin=RawOriginService.receipt(metal,Objects.toString(source.get("material_type"),""),line.powderOrigin());return new ProcessingOutputOrigin(origin.powderOrigin(),origin.washingStatus(),"UNVERIFIED",2,null,kind);
  }
  if("ACID_IN".equals(type))return new ProcessingOutputOrigin("RECYCLED","PASSED","UNVERIFIED",1,null,"METAL");
  if("PROCESS_RAW_RETURN".equals(type))return new ProcessingOutputOrigin("RECYCLED","REQUIRED","UNVERIFIED",ProcessingPolicy.v2(batch==null?Map.of():batch)?2:1,null,kind);
  if(batch!=null&&ProcessingPolicy.v2(batch)&&ProcessingPolicy.primaryOutput(type)){
   if(!"RAW".equals(category))return new ProcessingOutputOrigin("RECYCLED","PASSED","PASSED",2,null,null);
   if("ACID_WASH".equals(batch.get("process_role")))return new ProcessingOutputOrigin("RECYCLED","PASSED","UNVERIFIED",2,null,"METAL");
   var inputs=jdbc.queryForList("SELECT l.* FROM metal_warehouse_order_line l JOIN metal_warehouse_order o ON o.order_id=l.order_id WHERE o.batch_id=? AND o.type='PRODUCTION_OUT' AND o.status='POSTED'",batch.get("batch_id"));
   boolean washed=!inputs.isEmpty()&&inputs.stream().allMatch(i->"PASSED".equals(i.get("washing_status")));
   boolean nativePowder=!inputs.isEmpty()&&inputs.stream().allMatch(i->"NATIVE".equals(i.get("powder_origin"))&&"EXEMPT".equals(i.get("washing_status")))&&"粉料".equals(source.get("material_type"))&&SemiFlowService.num(source.get("metal_type_id"))==1;
   return new ProcessingOutputOrigin(nativePowder?"NATIVE":"RECYCLED",washed?"PASSED":nativePowder?"EXEMPT":"REQUIRED","UNVERIFIED",2,null,washed?"METAL":kind);
  }
  return new ProcessingOutputOrigin("UNKNOWN","UNVERIFIED","UNVERIFIED",1,null,kind);
 }
 String rawKey(Long batch,Long acid){return powder+":"+washing+":"+policy+":"+Objects.toString(batch,"0")+":"+Objects.toString(acid,"0")+":"+Objects.toString(verification,"0");}
}
