package com.ruoyi.metal.service;

import java.util.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.metal.catalog.BatchCompletionRequests.*;
import com.ruoyi.metal.catalog.WarehouseRequests.*;
import com.ruoyi.metal.security.FactoryScope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Owns the single confirmation transaction; physical returns keep their existing ledger semantics. */
@Service
@Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
public class BatchReturnConfirmationService {
 private final JdbcTemplate jdbc;
 private final FactoryScope scope;
 private final WarehouseService warehouse;
 public BatchReturnConfirmationService(JdbcTemplate jdbc,FactoryScope scope,WarehouseService warehouse){this.jdbc=jdbc;this.scope=scope;this.warehouse=warehouse;}
 private static long n(Object x){return x==null?0:Long.parseLong(x.toString());}
 private Map<String,Object> one(String sql,Object... args){var rows=jdbc.queryForList(sql,args);if(rows.isEmpty())throw new ServiceException("批次不存在或已归档");return rows.get(0);}
 private void permit(String permission){if(!scope.isAdmin()&&!SecurityUtils.hasPermi(permission))throw new ServiceException("没有此操作权限",403);}
 private Map<String,Object> batch(long id,boolean lock){var b=one("SELECT b.*,w.order_no,w.customer,s.process_name FROM metal_process_batch b JOIN metal_work_order w ON w.work_order_id=b.work_order_id AND w.del_flag='0' JOIN metal_work_order_step s ON s.item_id=b.item_id AND s.process_id=b.process_id WHERE b.batch_id=?"+(lock?" FOR UPDATE":""),id);scope.check(n(b.get("factory_id")));return b;}
 private Map<String,Object> output(long id){return one("SELECT COUNT(DISTINCT o.order_id) output_count,COALESCE(SUM(l.quantity),0) output_quantity,COALESCE(SUM(l.net_weight),0) output_weight FROM metal_warehouse_order o JOIN metal_warehouse_order_line l ON l.order_id=o.order_id WHERE o.batch_id=? AND o.status='POSTED' AND o.type IN ('SEMI_IN','FINISHED_IN')",id);}
 private static String hash(Object value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JSON.toJSONString(value).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 private Map<String,Object> result(long id){var b=batch(id,false);var out=output(id);var result=new LinkedHashMap<String,Object>();result.put("batchId",Long.toString(id));result.put("factoryId",b.get("factory_id").toString());result.put("workOrderId",b.get("work_order_id").toString());result.put("itemId",b.get("item_id").toString());result.put("processId",b.get("process_id").toString());result.put("batchNo",b.get("batch_no"));result.put("orderNo",b.get("order_no"));result.put("processName",b.get("process_name"));result.put("customer",b.get("customer"));result.put("version",b.get("version"));result.put("status",b.get("status"));result.put("completionPolicy",b.get("completion_policy"));result.put("plannedQuantity",b.get("planned_quantity"));result.put("completedQuantity",out.get("output_quantity"));result.put("outputCount",out.get("output_count"));result.put("outputWeight",new BigDecimal(out.get("output_weight").toString()).setScale(6).toPlainString());
  var confirmations=jdbc.queryForList("SELECT confirmation_id,mode,status,actor_id,reason,create_time FROM metal_batch_return_confirmation WHERE batch_id=?",id);
  result.put("phase",BatchCompletionRules.POLICY.equals(b.get("completion_policy"))?BatchCompletionRules.phase(b.get("status").toString(),n(out.get("output_count")),!confirmations.isEmpty()):"LEGACY");
  result.put("confirmations",confirmations);result.put("documents",jdbc.queryForList("SELECT CAST(o.order_id AS CHAR) id,o.order_no orderNo,o.type,o.status FROM metal_batch_return_document d JOIN metal_batch_return_confirmation c ON c.confirmation_id=d.confirmation_id JOIN metal_warehouse_order o ON o.order_id=d.order_id WHERE c.batch_id=? ORDER BY o.order_id",id));
  result.put("weights",jdbc.queryForMap("SELECT COALESCE(SUM(CASE WHEN o.type='PRODUCTION_OUT' THEN l.net_weight ELSE 0 END),0) issued,COALESCE(SUM(CASE WHEN o.type IN ('RAW_RETURN','SEMI_RETURN','PROCESS_RAW_RETURN') THEN l.net_weight ELSE 0 END),0) returned FROM metal_warehouse_order o JOIN metal_warehouse_order_line l ON l.order_id=o.order_id WHERE o.batch_id=? AND o.status='POSTED'",id));
  result.put("reports",jdbc.queryForList("SELECT loss_weight lossWeight,completed_quantity completedQuantity FROM metal_work_report WHERE batch_id=? AND voided=FALSE",id));
  var next=jdbc.queryForList("SELECT process_id,process_name,sequence_no FROM metal_work_order_step WHERE item_id=? AND sequence_no>? ORDER BY sequence_no LIMIT 1",b.get("item_id"),b.get("sequence_no"));
  if(!next.isEmpty()){var step=next.get(0);result.put("nextProcess",Map.of("processId",step.get("process_id").toString(),"processName",step.get("process_name"),"sequenceNo",step.get("sequence_no")));}
  long planned=n(one("SELECT quantity FROM metal_work_order_item WHERE item_id=?",b.get("item_id")).get("quantity"));
  long completed=jdbc.queryForObject("SELECT COALESCE(SUM(completed_quantity),0) FROM metal_process_batch WHERE item_id=? AND process_id=? AND status='SETTLED'",Long.class,b.get("item_id"),b.get("process_id"));
  long credit=new SemiFlowService(jdbc,scope).credit(n(b.get("item_id")),n(b.get("sequence_no")));
  result.put("processRemainingQuantity",Math.max(0,planned-completed-credit));
  return result;
 }
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public Map<String,Object> context(long id){var result=result(id);long factory=n(result.get("factoryId"));boolean sourceAccess=scope.isAdmin()||SecurityUtils.hasPermi("metal:warehouse-order:add")||SecurityUtils.hasPermi("metal:warehouse-order:query");result.put("sources",sourceAccess?warehouse.options("out-lines",factory,null).stream().filter(x->Long.toString(id).equals(String.valueOf(x.get("batchId")))).toList():List.of());return result;}

 public Map<String,Object> confirmReturn(long id,ReturnConfirmation input){
  permit("metal:warehouse-order:add");permit("metal:process-batch:settle");
  var initial=batch(id,false);scope.lockFactories(List.of(n(initial.get("factory_id"))));var b=batch(id,true);
  String fingerprint=hash(List.of(id,input));var replay=jdbc.queryForList("SELECT * FROM metal_batch_return_confirmation WHERE request_id=?",input.requestId());
  if(!replay.isEmpty()){var old=replay.get(0);if(n(old.get("batch_id"))!=id||n(old.get("actor_id"))!=SecurityUtils.getUserId()||!fingerprint.equals(old.get("request_hash")))throw new ServiceException("相同请求编号的内容或操作人不一致",409);return result(id);}
  if(!BatchCompletionRules.POLICY.equals(b.get("completion_policy")))throw new ServiceException("历史批次请使用原还库及结算流程");
  if(!"OPEN".equals(b.get("status")))throw new ServiceException("批次已结算或已冲销");
  if(input.version()==null||input.version().longValue()!=n(b.get("version")))throw new ServiceException("批次版本已变化，请刷新后重试",409);
  var out=output(id);var documents=input.documents()==null?List.<ReturnDocument>of():input.documents();
  BatchCompletionRules.requireConfirmation(n(out.get("output_count")),input.mode(),Boolean.TRUE.equals(input.physicalChecked()),documents.size());
  if(documents.size()>100||documents.stream().mapToInt(x->x.lines().size()).sum()>1000)throw new ServiceException("一次还料最多1000条明细");
  if("RETURNED".equals(input.mode())&&documents.stream().flatMap(d->d.lines().stream()).map(Line::netWeight).reduce(BigDecimal.ZERO,BigDecimal::add).signum()<=0)throw new ServiceException("有余料确认须包含真实还库重量");
  // Lock shared material/product/process metadata across the complete group before any child.
  var allLines=documents.stream().flatMap(d->d.lines().stream()).toList();
  warehouse.lockPostingSources(new Order("completion-lock",n(b.get("factory_id")),null,"RAW_RETURN",null,null,List.of(),null,null,null,null,n(b.get("work_order_id")),id,input.version(),false,null,null,allLines,null,null));
  // Lock every referenced source inventory in a stable order before posting the first child.
  var inventories=new TreeSet<Long>();
  for(var d:documents)for(var line:d.lines())if(line.sourceLineId()!=null){var source=one("SELECT l.inventory_id,o.batch_id,o.factory_id FROM metal_warehouse_order_line l JOIN metal_warehouse_order o ON o.order_id=l.order_id WHERE l.line_id=? AND o.status='POSTED' AND o.type='PRODUCTION_OUT'",line.sourceLineId());if(n(source.get("batch_id"))!=id||n(source.get("factory_id"))!=n(b.get("factory_id")))throw new ServiceException("还料来源不属于当前批次",403);inventories.add(n(source.get("inventory_id")));}
  for(long stock:inventories)one("SELECT inventory_id FROM metal_inventory WHERE inventory_id=? FOR UPDATE",stock);
  long factory=n(b.get("factory_id"));
  jdbc.update("INSERT INTO metal_batch_return_confirmation(batch_id,factory_id,request_id,request_hash,mode,physical_checked,actor_id,reason) VALUES(?,?,?,?,?,?,?,?)",id,factory,input.requestId(),fingerprint,input.mode(),true,SecurityUtils.getUserId(),input.reason());
  long confirmation=jdbc.queryForObject("SELECT confirmation_id FROM metal_batch_return_confirmation WHERE batch_id=?",Long.class,id);
  int groupIndex=0;
  for(var d:documents){
   if(!Set.of("RAW_RETURN","SEMI_RETURN","PROCESS_RAW_RETURN").contains(d.type()))throw new ServiceException("还料类型不支持");
   var groups=new TreeMap<Long,List<Line>>();
   for(var line:d.lines()){
    long source=0;
    if(!"PROCESS_RAW_RETURN".equals(d.type())){if(line.sourceLineId()==null)throw new ServiceException("请选择原出库明细");source=n(one("SELECT order_id FROM metal_warehouse_order_line WHERE line_id=?",line.sourceLineId()).get("order_id"));}
    groups.computeIfAbsent(source,x->new ArrayList<>()).add(line);
   }
   for(var lines:groups.values()){
    int version=(int)n(batch(id,true).get("version"));String request=UUID.nameUUIDFromBytes(("batch-return:"+id+":"+input.requestId()+":"+groupIndex++).getBytes(StandardCharsets.UTF_8)).toString();
    var order=new Order(request,factory,d.warehouseId(),d.type(),d.orderDate(),null,d.operatorIds(),d.keeperId(),null,d.inspectorId(),d.reviewerId(),n(b.get("work_order_id")),id,version,false,null,d.reason(),lines,n(b.get("item_id")),n(b.get("process_id")));
    long orderId=Long.parseLong(warehouse.postCompletionDocument(order));jdbc.update("INSERT INTO metal_batch_return_document(confirmation_id,order_id) VALUES(?,?)",confirmation,orderId);
   }
  }
  warehouse.settleConfirmed(id,Math.toIntExact(n(out.get("output_quantity"))));
  return result(id);
 }
}
