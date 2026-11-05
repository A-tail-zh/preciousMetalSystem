package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.util.*;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import org.springframework.jdbc.core.JdbcTemplate;

/** Shared by direct posting, pending reservations and import validation under the factory lock. */
final class ProcessingInputService {
 private ProcessingInputService(){}
 static void requireBatchModes(JdbcTemplate jdbc,Map<String,Object> target,List<Map<String,Object>> stocks,Long excludedFlow){
  if(!MeasurementPolicy.configurable(jdbc,target)||!"POST_PROCESS".equals(target.get("process_role"))||SemiMeasurementRules.weight(target))return;
  var modes=new HashSet<String>();
  for(var stock:stocks)if("SEMI".equals(stock.get("category")))modes.add(InventoryMeasurement.mode(stock));
  if(modes.isEmpty())return;
  String sql="SELECT i.* FROM metal_warehouse_order_line l JOIN metal_warehouse_order o ON o.order_id=l.order_id JOIN metal_inventory i ON i.inventory_id=l.inventory_id WHERE o.batch_id=? AND o.type='PRODUCTION_OUT' AND o.status='POSTED' AND l.category='SEMI' UNION ALL SELECT i.* FROM metal_stock_reservation r JOIN metal_inventory i ON i.inventory_id=r.inventory_id WHERE r.batch_id=? AND r.status='ACTIVE' AND r.category='SEMI' AND (? IS NULL OR r.flow_id<>?)";
  var saved=InventoryMeasurement.enrich(jdbc,jdbc.queryForList(sql,target.get("batch_id"),target.get("batch_id"),excludedFlow,excludedFlow));
  for(var stock:saved)if("SEMI".equals(stock.get("category")))modes.add(InventoryMeasurement.mode(stock));
  if(modes.size()>1)throw new ServiceException("按件后加工的不同计量模式半成品须分批加工，不能混入已领或待确认来源");
 }
 static Map<String,Object> stock(JdbcTemplate jdbc,Map<String,Object> inventory){
  return stock(jdbc,inventory,false);
 }
 // Only service-owned candidate paths may reuse an earlier bulk InventoryMeasurement.enrich.
 static Map<String,Object> stockResolved(JdbcTemplate jdbc,Map<String,Object> inventory){
  return stock(jdbc,inventory,true);
 }
 private static Map<String,Object> stock(JdbcTemplate jdbc,Map<String,Object> inventory,boolean resolved){
  var result=new LinkedHashMap<>(inventory);
  if(!resolved)InventoryMeasurement.enrich(jdbc,List.of(result));
  if("RAW".equals(result.get("category"))){var rows=jdbc.queryForList("SELECT m.material_type,m.metal_type_id FROM metal_material m JOIN metal_type t ON t.metal_type_id=m.metal_type_id WHERE m.material_id=? AND m.del_flag='0'",result.get("reference_id"));if(rows.isEmpty())throw new ServiceException("来源物料不存在或已删除");result.putAll(rows.get(0));}
  if(result.get("origin_acid_wash_id")!=null&&"UNVERIFIED".equals(result.get("washing_status"))&&jdbc.queryForObject("SELECT COUNT(*) FROM metal_acid_wash_batch WHERE acid_wash_id=? AND status='SETTLED'",Long.class,result.get("origin_acid_wash_id"))>0)result.put("washing_status","PASSED");
  return result;
 }
 static void require(JdbcTemplate jdbc,Map<String,Object> inventory,Map<String,Object> target){
  require(jdbc,inventory,target,false);
 }
 static void requireResolved(JdbcTemplate jdbc,Map<String,Object> inventory,Map<String,Object> target){
  require(jdbc,inventory,target,true);
 }
 private static void require(JdbcTemplate jdbc,Map<String,Object> inventory,Map<String,Object> target,boolean resolved){
  if(!ProcessingPolicy.v2(target))return;
  var stock=stock(jdbc,inventory,resolved);long metal=SemiFlowService.num(target.get("metal_type_id"));
  if(metal==0){var snapshot=JSON.parseObject(Objects.toString(target.get("product_snapshot"),"{}"));metal=ProcessingPolicy.metalId(snapshot);}
  RawOriginService.requireMetal(stock,metal);
  String role=Objects.toString(target.get("process_role"),"");
  if("RAW".equals(stock.get("category"))){
   if("POST_PROCESS".equals(role))throw new ServiceException("融定后工序必须领用合格半成品，原料不能绕过融定");
   if("SMELTING".equals(role))RawOriginService.requireSmelting(stock,metal,RawOriginService.iridium(jdbc,metal,SemiFlowService.num(stock.get("factory_id"))));
   else if("UNVERIFIED".equals(stock.get("washing_status")))throw new ServiceException("旧库存来源尚未核对，请先完成来源核对");
   Map<String,Object> source=null;
   if(stock.get("origin_batch_id")!=null){source=jdbc.queryForMap("SELECT item_id,work_order_id,factory_id,sequence_no,status,handover_policy_version FROM metal_process_batch WHERE batch_id=?",stock.get("origin_batch_id"));if(!HandoverSource.canEnter(jdbc,stock,source,target))throw new ServiceException("本工单产出须验收后才能转序；跨工单原料仍须等来源批次整体结束，并沿用原审批规则");if(Objects.equals(String.valueOf(source.get("item_id")),String.valueOf(target.get("item_id")))&&SemiFlowService.num(source.get("sequence_no"))>=SemiFlowService.num(target.get("sequence_no")))throw new ServiceException("同任务原料产出只能承接后续工序");}
   if(ProcessingPolicy.currentExecution(target))rawRoute(jdbc,stock,source,target);
   var item=jdbc.queryForMap("SELECT material_id FROM metal_work_order_item WHERE item_id=?",target.get("item_id"));
   if(item.get("material_id")!=null&&!RawPlanService.internalInput(jdbc,SemiFlowService.num(stock.get("inventory_id")),SemiFlowService.num(target.get("item_id")))&&!Objects.equals(String.valueOf(item.get("material_id")),String.valueOf(stock.get("reference_id"))))throw new ServiceException("外部投入须使用原料任务登记的待处理物料编码");
  }else if("SEMI".equals(stock.get("category"))){if(!"POST_PROCESS".equals(role))throw new ServiceException("前处理及融定工序应领用原料，不能用半成品替代");if(MeasurementPolicy.current(jdbc,target)&&!MeasurementPolicy.configurable(jdbc,target)&&SemiMeasurementRules.weight(stock))throw new ServiceException("当前工单后加工按件管理，请选择按件计量的合规坯件库存；参考件数不能作为有效件数");RawOriginService.requirePostProcess(stock);}
  else throw new ServiceException("生产只接受原料或合格半成品");
 }
 private static void rawRoute(JdbcTemplate jdbc,Map<String,Object> stock,Map<String,Object> source,Map<String,Object> target){
  long sequence=SemiFlowService.num(target.get("sequence_no"));
  boolean own=source!=null&&SemiFlowService.num(source.get("item_id"))==SemiFlowService.num(target.get("item_id"));
  if(source!=null){
   if(SemiFlowService.num(source.get("factory_id"))!=SemiFlowService.num(stock.get("factory_id")))throw new ServiceException("加工原料来源批次与库存工厂不一致");
   if(SemiFlowService.num(source.get("work_order_id"))==SemiFlowService.num(target.get("work_order_id"))&&!own)throw new ServiceException("同一工单不同产品明细不能相互替代加工原料");
  }
  // Qualified external raw may enter smelting without inventing local preparation work.
  if(!own&&("SMELTING".equals(target.get("process_role"))||sequence==1))return;
  if(source==null||SemiFlowService.num(source.get("sequence_no"))+1!=sequence)throw new ServiceException("加工原料须承接已结算的相邻前工序产出，不能跳过计划工序");
  if(!own){
   String sql="SELECT process_id FROM metal_work_order_step WHERE item_id=? AND sequence_no<=? ORDER BY sequence_no";
   var completed=jdbc.queryForList(sql,source.get("item_id"),sequence-1).stream().map(r->SemiFlowService.num(r.get("process_id"))).toList();
   var required=jdbc.queryForList(sql,target.get("item_id"),sequence-1).stream().map(r->SemiFlowService.num(r.get("process_id"))).toList();
   if(completed.size()!=sequence-1||!completed.equals(required))throw new ServiceException("加工原料来源已完成路线与目标前工序快照不匹配");
  }
 }
 static void capacity(JdbcTemplate jdbc,Map<String,Object> stock,Map<String,Object> target,BigDecimal weight,Long excludedFlow){
  if(ProcessingPolicy.v2(target)&&"RAW".equals(stock.get("category"))&&!RawPlanService.internalInput(jdbc,SemiFlowService.num(stock.get("inventory_id")),SemiFlowService.num(target.get("item_id"))))RawPlanService.requireCapacity(jdbc,SemiFlowService.num(target.get("item_id")),weight,excludedFlow);
 }
 static void qualification(JdbcTemplate jdbc,long line,Map<String,Object> inventory,Map<String,Object> target){
  if(!ProcessingPolicy.v2(target)||!"SMELTING".equals(target.get("process_role"))||!"RAW".equals(inventory.get("category")))return;
  var stock=stock(jdbc,inventory);var snapshot=new LinkedHashMap<String,Object>();for(String key:List.of("powder_origin","washing_status","origin_policy_version","origin_verification_id","origin_acid_wash_id","origin_batch_id","metal_type_id_snapshot"))snapshot.put(key,stock.get(key));
  jdbc.update("INSERT INTO metal_raw_qualification_use(line_id,item_id,process_id,inventory_id,qualification_snapshot,actor_id) VALUES(?,?,?,?,?,?)",line,target.get("item_id"),target.get("process_id"),stock.get("inventory_id"),JSON.toJSONString(snapshot),SecurityUtils.getUserId());
 }
}
