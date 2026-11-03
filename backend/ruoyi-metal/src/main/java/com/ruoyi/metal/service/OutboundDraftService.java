package com.ruoyi.metal.service;
import java.util.*;import java.math.BigDecimal;import java.util.regex.*;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.metal.catalog.OutboundSubmissionRequests.*;
import com.ruoyi.metal.security.FactoryScope;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.*;
/** Read-only draft validation. All balances are read again under the posting locks at save. */
@Service
public class OutboundDraftService {
 private final JdbcTemplate jdbc;private final FactoryScope scope;private final WarehouseService warehouse;private final WarehouseImportService imports;
 public OutboundDraftService(JdbcTemplate jdbc,FactoryScope scope,WarehouseService warehouse,WarehouseImportService imports){this.jdbc=jdbc;this.scope=scope;this.warehouse=warehouse;this.imports=imports;}
 private static long n(Object x){return x==null?0:Long.parseLong(x.toString());}
 private static BigDecimal d(Object x){return x==null?BigDecimal.ZERO:new BigDecimal(x.toString());}
 private Map<String,Object> one(String sql,Object...args){var rows=jdbc.queryForList(sql,args);if(rows.size()!=1)throw new ServiceException("生产关联不存在或已失效");return rows.get(0);}
 private void permit(boolean importing){if(!scope.isAdmin()&&!SecurityUtils.hasPermi(importing?"metal:warehouse-order:import":"metal:warehouse-order:add"))throw new ServiceException("没有此操作权限",403);}
 private static Map<String,String> error(String path,String message){return Map.of("field",path,"message",message,"code","INVALID_DRAFT");}
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public Map<String,Object> preview(Preview input){permit(false);return inspect(input.request());}
 // Import is audited in the same transaction; only the audit outbox may be written.
 @Transactional(isolation=Isolation.REPEATABLE_READ)
 public Map<String,Object> previewImport(Preview input){permit(true);return inspect(input.request());}
 Map<String,Object> inspect(Request r){return inspect(r,true);}
 Map<String,Object> inspectConfirmation(Request r){return inspect(r,false);}
 private Map<String,Object> inspect(Request r,boolean candidates){
  OutboundSubmissionRules.validate(r,true);scope.check(r.factoryId());
  var target=one("SELECT s.sequence_no,s.measurement_mode,s.weight_stage_closed,i.work_order_id,i.item_id,w.factory_id,w.status,s.process_id,s.process_role,s.output_stage,w.processing_policy_version,COALESCE(i.material_snapshot,i.product_snapshot) product_snapshot FROM metal_work_order_step s JOIN metal_work_order_item i ON i.item_id=s.item_id JOIN metal_work_order w ON w.work_order_id=i.work_order_id WHERE s.item_id=? AND s.process_id=? AND w.del_flag='0'",r.itemId(),r.processId());
  if(n(target.get("factory_id"))!=r.factoryId()||n(target.get("work_order_id"))!=r.workOrderId())throw new ServiceException("生产关联不属于当前工厂或工单",403);
  if(!Set.of("WAITING_MATERIAL","IN_PROGRESS").contains(target.get("status")))throw new ServiceException("请选择待领料或生产中的工单");
  if(Boolean.TRUE.equals(target.get("weight_stage_closed"))||"1".equals(String.valueOf(target.get("weight_stage_closed"))))throw new ServiceException("本工序已结束投产，不能追加领料");
  var open=jdbc.queryForList("SELECT batch_id,version FROM metal_process_batch WHERE item_id=? AND process_id=? AND status='OPEN'",r.itemId(),r.processId());
  Long batch=BatchCompletionRules.selectBatch(open.stream().map(x->n(x.get("batch_id"))).toList(),r.batchId());
  Integer version=batch==null?null:open.stream().filter(x->n(x.get("batch_id"))==batch).map(x->(int)n(x.get("version"))).findFirst().orElse(null);
  if(r.batchId()!=null&&(r.batchVersion()==null||!r.batchVersion().equals(version)))throw new ServiceException("批次版本已变化，请刷新后重试",409);
  var semi=new SemiFlowService(jdbc,scope);var policy=new SemiSourcePolicyService(jdbc,scope);long capacity=semi.entryCapacity(r.itemId(),n(target.get("sequence_no"))),external=0;String profile=batch==null?null:policy.existingProfile(batch,target,-1);
  var errors=new ArrayList<Map<String,String>>();var groups=new ArrayList<Map<String,Object>>();var weights=new HashMap<Long,BigDecimal>();var pieces=new HashMap<Long,Long>();var releases=new HashMap<Long,List<Map<String,Object>>>();var skips=new HashMap<Long,Long>();BigDecimal total=BigDecimal.ZERO;long qty=0;
  for(int gi=0;gi<r.groups().size();gi++){
   var g=r.groups().get(gi);var result=new LinkedHashMap<String,Object>();result.put("warehouseId",g.warehouseId().toString());result.put("stocks",List.of());groups.add(result);
   // Reject an invalid group before invoking a transactional stock snapshot; keep other groups and field paths intact.
   var warehouses=jdbc.queryForList("SELECT factory_id,status FROM metal_warehouse WHERE warehouse_id=? AND del_flag='0'",g.warehouseId());
   if(warehouses.size()!=1||n(warehouses.get(0).get("factory_id"))!=r.factoryId()||!"0".equals(warehouses.get(0).get("status"))){errors.add(error("groups["+gi+"].warehouseId","所选仓库不存在、已停用或不属于当前工厂，请重新选择"));continue;}
   if(candidates){var snapshot=warehouse.productionStockSnapshot(r.factoryId(),g.warehouseId(),r.workOrderId(),r.itemId(),r.processId());result.put("stocks",snapshot.stocks());}
   List<Map<String,Object>> parsed=List.of();
   if(!g.lines().isEmpty())try{parsed=imports.inspect(new WarehouseImportService.Input(r.factoryId(),g.warehouseId(),"PRODUCTION_OUT",r.workOrderId(),r.itemId(),r.processId(),g.lines(),Map.of(),batch));}catch(ServiceException ex){var m=Pattern.compile("第(\\d+)行").matcher(ex.getMessage());int row=m.find()?Math.max(0,Integer.parseInt(m.group(1))-2):0;errors.add(error("groups["+gi+"].lines["+row+"].inventoryId","仓库"+g.warehouseId()+"："+ex.getMessage()));}
   for(int li=0;li<parsed.size();li++){
    var row=parsed.get(li);String path="groups["+gi+"].lines["+li+"].inventoryId";long stockId=n(row.get("inventoryId"));var stock=one("SELECT * FROM metal_inventory WHERE inventory_id=? AND del_flag='0'",stockId);
    weights.merge(stockId,d(row.get("netWeight")),BigDecimal::add);pieces.merge(stockId,n(row.get("quantity")),Long::sum);total=total.add(d(row.get("netWeight")));qty+=n(row.get("quantity"));
    try{ProcessingInputService.require(jdbc,stock,target);}catch(ServiceException ex){errors.add(error(path,ex.getMessage()));continue;}
    try{var pending=WarehouseReservations.stock(jdbc,stockId);WarehouseFlowRules.available(d(stock.get("net_weight")),n(stock.get("quantity")),d(pending.get("weight")),n(pending.get("quantity")),weights.get(stockId),pieces.get(stockId),"RAW".equals(stock.get("category"))||SemiMeasurementRules.weight(stock));}catch(ServiceException ex){errors.add(error(path,ex.getMessage()));}
    if("SEMI".equals(stock.get("category")))try{
     var source=semi.validateSource(stock,target,row.get("releaseId")==null?null:n(row.get("releaseId")),row.get("skipPermitId")==null?null:n(row.get("skipPermitId")),row.get("skipPermitVersion")==null?null:(int)n(row.get("skipPermitVersion")));
     var trace=policy.decide(source,target,row.get("skipPermitId")==null?null:n(row.get("skipPermitId")),row.get("skipPermitVersion")==null?null:(int)n(row.get("skipPermitVersion")));
     String current=JSON.toJSONString(new TreeMap<>(Map.of("processed",trace.get("processed"),"skipped",trace.get("skipped"))));RouteExecutionRules.sameProfile(profile,current);profile=current;
     for(Object seq:(List<?>)trace.get("newSkipped"))skips.merge(n(seq),n(row.get("quantity")),Long::sum);
    }catch(ServiceException ex){errors.add(error(path,ex.getMessage()));}
    if(row.get("releaseId")!=null){long id=n(row.get("releaseId"));releases.computeIfAbsent(id,x->new ArrayList<>()).add(row);if(!SemiMeasurementRules.weight(stock)){external+=n(row.get("quantity"));try{OutboundDraftBalances.requireCapacity(capacity,external);}catch(ServiceException ex){errors.add(error("groups["+gi+"].lines["+li+"].quantity",ex.getMessage()));}}}
   }
  }
  for(var e:releases.entrySet())try{var release=semi.availableRelease(e.getKey());BigDecimal weight=e.getValue().stream().map(x->d(x.get("netWeight"))).reduce(BigDecimal.ZERO,BigDecimal::add);long count=e.getValue().stream().mapToLong(x->n(x.get("quantity"))).sum();if(SemiMeasurementRules.weight(release))SemiMeasurementRules.requireWeight(d(release.get("available_weight")),weight);else SemiFlowRules.release(n(release.get("available_quantity")),d(release.get("available_weight")),count,weight);}catch(ServiceException ex){errors.add(error("groups","释放记录"+e.getKey()+"："+ex.getMessage()));}
  for(var e:skips.entrySet())try{long local=jdbc.queryForObject("SELECT COALESCE(SUM(CASE WHEN status='SETTLED' THEN completed_quantity ELSE planned_quantity END),0) FROM metal_process_batch WHERE item_id=? AND sequence_no=? AND status IN ('OPEN','SETTLED')",Long.class,r.itemId(),e.getKey());long planned=jdbc.queryForObject("SELECT quantity FROM metal_work_order_item WHERE item_id=?",Long.class,r.itemId());RouteExecutionRules.capacity(planned,local,0,semi.credit(r.itemId(),e.getKey()),e.getValue()+external);}catch(ServiceException ex){errors.add(error("groups",ex.getMessage()));}
  for(var g:groups)for(var stock:(List<Map<String,Object>>)g.get("stocks")){long id=n(stock.get("id"));stock.put("draftRemainingWeight",d(stock.get("netWeight")).subtract(weights.getOrDefault(id,BigDecimal.ZERO)).setScale(6).toPlainString());stock.put("draftRemainingQuantity",n(stock.get("quantity"))-pieces.getOrDefault(id,0L));}
  var out=new LinkedHashMap<String,Object>();out.put("factoryId",r.factoryId().toString());out.put("workOrderId",r.workOrderId().toString());out.put("itemId",r.itemId().toString());out.put("processId",r.processId().toString());out.put("batchId",batch==null?null:batch.toString());out.put("batchVersion",version);out.put("groups",groups);out.put("entrySummary",Map.of("externalEntryCapacity",Long.toString(capacity),"filledQuantity",Long.toString(external),"remainingQuantity",Long.toString(capacity-external)));out.put("totalNetWeight",total.setScale(6).toPlainString());out.put("totalQuantity",qty);out.put("fieldErrors",errors);return out;
 }
 public void requireValid(Map<String,Object> preview){var fields=(List<Map<String,String>>)preview.get("fieldErrors");if(!fields.isEmpty())throw new OutboundFieldException(fields);}
}
