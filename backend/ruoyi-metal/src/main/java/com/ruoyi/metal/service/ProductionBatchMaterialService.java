package com.ruoyi.metal.service;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.ruoyi.metal.service.StorageLedgerService.*;

/** Transfers existing material responsibility, without posting physical stock or debt. */
public final class ProductionBatchMaterialService {
 private final JdbcTemplate jdbc;
 public ProductionBatchMaterialService(JdbcTemplate jdbc){this.jdbc=jdbc;}
 static String currentRead(String sql){return org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly()?sql.replace(" FOR UPDATE",""):sql;}
 private List<Map<String,Object>> rows(String sql,Object...args){return jdbc.queryForList(currentRead(sql),args);}
 public boolean enabled(){Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='metal_production_material_source'",Integer.class);return count!=null&&count==1;}
 private Map<String,Object> one(String sql,Object...args){var rows=rows(sql,args);if(rows.size()!=1)throw fail("来源分配不存在或不属于本厂");return rows.get(0);}
 private static BigDecimal weight(Map<String,Object> row){return amount(row.getOrDefault("netWeight",row.getOrDefault("weight","0")));}
 private static long qty(Map<String,Object> row){return row.get("quantity")==null?0:StorageLedgerService.qty(row.get("quantity"));}
 private static List<Map<String,Object>> maps(Object value){return value==null?List.of():StorageSupport.maps(value);}
 private static Long optional(Object value){return value==null||text(value).isBlank()?null:id(value);}
 private static BigDecimal positive(Map<String,Object> row){BigDecimal w=weight(row);if(w.signum()<=0||w.scale()>2)throw fail("来源分配重量须为正数且最多两位小数");return w;}
 private static void basis(Map<String,Object> row){if(!"CONFIRMED_BALANCE".equals(text(row.get("allocationBasis"))))throw fail("本阶段分配须采用已确认余额；实测必须先形成可审核的真实入库或加工事实");}
 String accepted(long factory,long slice){
  var origin=one("SELECT b.origin_action,CAST(op.result AS CHAR) operation_result FROM metal_storage_batch_slice sl JOIN metal_production_batch b ON b.factory_id=sl.factory_id AND b.id=sl.batch_id LEFT JOIN metal_production_execution_operation op ON op.factory_id=b.factory_id AND op.id=b.origin_operation_id WHERE sl.factory_id=? AND sl.id=?",factory,slice);
  List<Map<String,Object>> rows;
  if("RETURN_RESIDUAL".equals(origin.get("origin_action"))){var result=JSON.parseObject(text(origin.get("operation_result")));if(result==null||result.get("returnEventId")==null)throw fail("退料承接缺少真实退料依据");rows=rows("SELECT d.id,d.number FROM metal_storage_event e JOIN metal_storage_document d ON d.factory_id=e.factory_id AND d.id=e.document_id WHERE e.factory_id=? AND e.id=? AND e.action='RETURN' AND e.reversed_by IS NULL AND d.status='POSTED' AND d.deleted=0",factory,id(result.get("returnEventId")));}
  else rows=rows("SELECT d.id,d.number FROM metal_storage_batch_slice sl JOIN metal_storage_stock st ON st.id=sl.stock_id AND st.factory_id=sl.factory_id JOIN metal_storage_document d ON d.id=st.origin_document_id AND d.factory_id=st.factory_id JOIN metal_storage_event ev ON ev.document_id=d.id AND ev.line_id=st.origin_line_id AND ev.factory_id=d.factory_id WHERE sl.factory_id=? AND sl.id=? AND d.status='POSTED' AND d.deleted=0 AND ev.action IN ('INBOUND','RETURN') AND ev.reversed_by IS NULL",factory,slice);
  if(rows.size()!=1)throw fail("库存缺少有效已复验入库依据，不能拆分合并");return "STORAGE_DOCUMENT:"+text(rows.get(0).get("id"));
 }
 private long insertSource(long factory,Long slice,Long execution,Long parent,Long root,long owner,long sourceOwner,String kind,BigDecimal w,Long q,BigDecimal returnable,long returnQ){
  Long legacy=parent==null?null:optional(one("SELECT * FROM metal_production_material_source WHERE factory_id=? AND id=?",factory,parent).get("legacy_adoption_operation_id"));
  long source=StorageSupport.insert(jdbc,"INSERT INTO metal_production_material_source(factory_id,stock_slice_id,execution_id,source_allocation_id,root_issue_allocation_id,current_owner_id,source_owner_id,source_kind,initial_weight,initial_quantity,weight,quantity,returnable_weight,returnable_quantity) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",factory,slice,execution,parent,root,owner,sourceOwner,kind,w,q,w,q,legacy==null?returnable:BigDecimal.ZERO,legacy==null?returnQ:0);
  if(legacy!=null)jdbc.update("UPDATE metal_production_material_source SET legacy_adoption_operation_id=? WHERE factory_id=? AND id=?",legacy,factory,source);return source;
 }
 private void movement(long factory,Long operation,Long event,Long from,Long to,Long transfer,String purpose,BigDecimal w,Long q){jdbc.update("INSERT INTO metal_production_material_allocation(factory_id,operation_id,storage_event_id,from_source_id,to_source_id,wip_transfer_id,purpose,weight,quantity,allocation_basis) VALUES(?,?,?,?,?,?,?,?,?,'CONFIRMED_BALANCE')",factory,operation,event,from,to,transfer,purpose,w,q);}
 private List<Map<String,Object>> sources(long factory,String column,long location){return rows("SELECT * FROM metal_production_material_source WHERE factory_id=? AND "+column+"=? ORDER BY id FOR UPDATE",factory,location);}
 private Map<String,Object> apiSource(Map<String,Object> row){
  var out=new LinkedHashMap<String,Object>();out.put("sourceAllocationId",text(row.get("id")));out.put("currentOwnerId",text(row.get("current_owner_id")));out.put("sourceOwnerId",text(row.get("source_owner_id")));out.put("sourceKind",row.get("source_kind"));out.put("netWeight",dec(row.get("weight")).toPlainString());out.put("weight",out.get("netWeight"));out.put("quantity",row.get("quantity"));out.put("returnableWeight",dec(row.get("returnable_weight")).toPlainString());out.put("returnableQuantity",row.get("returnable_quantity"));
  if(row.get("root_issue_allocation_id")!=null){long root=id(row.get("root_issue_allocation_id"));out.put("rootIssueAllocationId",text(root));out.put("issueAllocationId",text(root));var issue=one("SELECT line_id,stock_id,stock_slice_id FROM metal_storage_issue_allocation WHERE factory_id=? AND id=?",row.get("factory_id"),root);out.put("issueLineId",text(issue.get("line_id")));out.put("stockId",text(issue.get("stock_id")));out.put("stockSliceId",issue.get("stock_slice_id")==null?null:text(issue.get("stock_slice_id")));out.put("carryAllocationId",text(row.get("id")));}
  return out;
 }
 public List<Map<String,Object>> stockContext(long factory,long batch){
  var result=new ArrayList<Map<String,Object>>();
  for(var s:rows("SELECT sl.*,st.version stock_version,st.origin_step_id,st.measurement_mode,st.category FROM metal_storage_batch_slice sl JOIN metal_storage_stock st ON st.factory_id=sl.factory_id AND st.id=sl.stock_id WHERE sl.factory_id=? AND sl.batch_id=? AND (sl.weight>0 OR sl.quantity>0) ORDER BY sl.id FOR UPDATE",factory,batch)){
   long slice=id(s.get("id"));var out=new LinkedHashMap<String,Object>();out.put("id",text(slice));out.put("stockSliceId",text(slice));out.put("stockId",text(s.get("stock_id")));out.put("stockVersion",s.get("stock_version"));out.put("sliceVersion",s.get("version"));out.put("netWeight",dec(s.get("weight")).toPlainString());out.put("weight",out.get("netWeight"));out.put("quantity",s.get("quantity"));out.put("originStepId",text(s.get("origin_step_id")));out.put("measurementMode",s.get("measurement_mode"));
   out.put("category",s.get("category"));try{out.put("qualityEligibility",Map.of("allowed",true,"basisRef",accepted(factory,slice)));}catch(ServiceException error){out.put("qualityEligibility",Map.of("allowed",false,"message",error.getMessage()));}out.put("owners",rows("SELECT CAST(owner_id AS CHAR) ownerId,CAST(weight AS CHAR) netWeight,quantity FROM metal_storage_batch_slice_owner WHERE factory_id=? AND stock_slice_id=? AND (weight>0 OR quantity>0) ORDER BY owner_id",factory,slice));out.put("sourceAllocations",sources(factory,"stock_slice_id",slice).stream().filter(x->dec(x.get("weight")).signum()>0).map(this::apiSource).toList());result.add(out);
  }
  return result;
 }
 public List<Map<String,Object>> wipContext(long factory,long execution){String mode=text(one("SELECT s.measurement_mode FROM metal_production_execution e JOIN metal_production_order_step s ON s.id=e.order_step_id WHERE e.factory_id=? AND e.id=?",factory,execution).get("measurement_mode"));var result=new ArrayList<Map<String,Object>>();for(var s:sources(factory,"execution_id",execution))if(dec(s.get("weight")).signum()>0){var out=apiSource(s);out.put("measurementMode",mode);result.add(out);}return result;}
 public List<Map<String,Object>> transformSnapshot(long factory,Collection<Long> batches){
  if(batches.isEmpty()||batches.size()>32)throw fail("拆合来源范围无效");
  String marks=String.join(",",Collections.nCopies(batches.size(),"?"));var args=new ArrayList<Object>();args.add(factory);args.addAll(batches);args.addAll(batches);
  var snapshot=rows("SELECT s.id,s.weight,s.quantity,s.returnable_weight,s.returnable_quantity FROM metal_production_material_source s LEFT JOIN metal_storage_batch_slice sl ON sl.factory_id=s.factory_id AND sl.id=s.stock_slice_id LEFT JOIN metal_production_execution e ON e.factory_id=s.factory_id AND e.id=s.execution_id WHERE s.factory_id=? AND (sl.batch_id IN ("+marks+") OR e.batch_id IN ("+marks+")) ORDER BY s.id FOR UPDATE",args.toArray());
  for(var row:snapshot){row.put("id",text(row.get("id")));row.put("weight",dec(row.get("weight")).toPlainString());row.put("returnable_weight",dec(row.get("returnable_weight")).toPlainString());}return snapshot;
 }
 private boolean hasEffectiveConsumer(long factory,long source,long original){
  return !rows("SELECT a.id FROM metal_production_material_allocation a LEFT JOIN metal_storage_event e ON e.factory_id=a.factory_id AND e.id=a.storage_event_id WHERE a.factory_id=? AND a.from_source_id=? AND (a.operation_id IS NULL OR a.operation_id<>?) AND a.purpose<>'REVERSAL' AND (a.storage_event_id IS NULL OR e.reversed_by IS NULL) AND NOT EXISTS(SELECT 1 FROM metal_production_execution_operation r WHERE r.factory_id=a.factory_id AND r.reversal_of=a.operation_id) FOR UPDATE",factory,source,original).isEmpty();
 }
 public void requireReversibleTransform(long factory,long original){
  var op=one("SELECT CAST(result AS CHAR) result FROM metal_production_execution_operation WHERE factory_id=? AND id=?",factory,original);var snapshot=JSON.parseObject(text(op.get("result"))).getJSONObject("reversalSnapshot");
  if(snapshot==null||!snapshot.containsKey("materialBefore"))throw fail("历史拆合缺少原来源及可退责任快照，不能推测撤销");
  var state=new HashMap<Long,Map<String,Object>>();
  for(var a:rows("SELECT * FROM metal_production_material_allocation WHERE factory_id=? AND operation_id=? AND purpose IN ('IDENTITY_TRANSFER','WIP_TRANSFER') ORDER BY id DESC FOR UPDATE",factory,original)){
   long from=id(a.get("from_source_id")),to=id(a.get("to_source_id"));var source=state.computeIfAbsent(from,key->new LinkedHashMap<>(one("SELECT * FROM metal_production_material_source WHERE factory_id=? AND id=? FOR UPDATE",factory,key)));var target=state.computeIfAbsent(to,key->new LinkedHashMap<>(one("SELECT * FROM metal_production_material_source WHERE factory_id=? AND id=? FOR UPDATE",factory,key)));BigDecimal w=dec(a.get("weight"));Long q=optional(a.get("quantity"));
   if(hasEffectiveConsumer(factory,to,original))throw fail("结果来源已有有效下游使用，须先逆序处理");
   if(dec(target.get("weight")).compareTo(w)!=0||!Objects.equals(optional(target.get("quantity")),q)||id(source.get("current_owner_id"))!=id(target.get("current_owner_id"))||id(source.get("source_owner_id"))!=id(target.get("source_owner_id"))||!text(source.get("source_kind")).equals(text(target.get("source_kind"))))throw fail("结果的来源、料权或实物余额已变化，不能直接撤销");
   target.put("weight",BigDecimal.ZERO);target.put("quantity",q==null?null:0L);source.put("weight",dec(source.get("weight")).add(w));if(source.get("quantity")!=null)source.put("quantity",qty(source)+(q==null?0:q));
  }
  for(var old:maps(snapshot.get("materialBefore"))){long id=id(old.get("id"));var current=state.get(id);if(current==null)current=one("SELECT * FROM metal_production_material_source WHERE factory_id=? AND id=?",factory,id);if(dec(current.get("weight")).compareTo(dec(old.get("weight")))!=0||!Objects.equals(optional(current.get("quantity")),optional(old.get("quantity"))))throw fail("恢复后的来源余额与原确认快照不符");}
 }
 public void reverseTransform(long factory,long original,long reversal){
  requireReversibleTransform(factory,original);
  var operation=one("SELECT CAST(result AS CHAR) result FROM metal_production_execution_operation WHERE factory_id=? AND id=? FOR UPDATE",factory,original);
  var result=JSON.parseObject(text(operation.get("result")));var snapshot=result.getJSONObject("reversalSnapshot");
  if(snapshot==null||!snapshot.containsKey("materialBefore"))throw fail("历史拆合缺少原来源及可退责任快照，不能推测撤销");
  var before=maps(snapshot.get("materialBefore"));var touched=new TreeSet<Long>();
  for(var a:rows("SELECT * FROM metal_production_material_allocation WHERE factory_id=? AND operation_id=? AND purpose IN ('IDENTITY_TRANSFER','WIP_TRANSFER') ORDER BY id DESC FOR UPDATE",factory,original)){
   long from=id(a.get("from_source_id")),to=id(a.get("to_source_id"));var source=one("SELECT * FROM metal_production_material_source WHERE factory_id=? AND id=? FOR UPDATE",factory,from);var target=one("SELECT * FROM metal_production_material_source WHERE factory_id=? AND id=? FOR UPDATE",factory,to);
   if(hasEffectiveConsumer(factory,to,original))throw fail("结果来源已有有效下游使用，须先逆序处理");
   BigDecimal w=dec(a.get("weight"));Long q=optional(a.get("quantity"));long count=q==null?0:q;
   if(dec(target.get("weight")).compareTo(w)!=0||!Objects.equals(optional(target.get("quantity")),q)||id(source.get("current_owner_id"))!=id(target.get("current_owner_id"))||id(source.get("source_owner_id"))!=id(target.get("source_owner_id"))||!text(source.get("source_kind")).equals(text(target.get("source_kind"))))throw fail("结果的来源、料权或实物余额已变化，不能直接撤销");
   consumeSource(to,w,q);jdbc.update("UPDATE metal_production_material_source SET weight=weight+?,quantity=CASE WHEN quantity IS NULL THEN NULL ELSE quantity+? END WHERE factory_id=? AND id=?",w,count,factory,from);
   if("IDENTITY_TRANSFER".equals(a.get("purpose"))){
    long fromSlice=id(source.get("stock_slice_id")),toSlice=id(target.get("stock_slice_id")),owner=id(source.get("current_owner_id"));var left=one("SELECT stock_id FROM metal_storage_batch_slice WHERE factory_id=? AND id=? FOR UPDATE",factory,fromSlice);var right=one("SELECT stock_id FROM metal_storage_batch_slice WHERE factory_id=? AND id=? FOR UPDATE",factory,toSlice);if(id(left.get("stock_id"))!=id(right.get("stock_id")))throw fail("拆合库存身份转移引用不一致");
    if(jdbc.update("UPDATE metal_storage_batch_slice_owner SET weight=weight-?,quantity=quantity-? WHERE factory_id=? AND stock_slice_id=? AND owner_id=? AND weight>=? AND quantity>=?",w,count,factory,toSlice,owner,w,count)!=1)throw fail("结果份额料权已变化");
    jdbc.update("UPDATE metal_storage_batch_slice_owner SET weight=weight+?,quantity=quantity+? WHERE factory_id=? AND stock_slice_id=? AND owner_id=?",w,count,factory,fromSlice,owner);
    jdbc.update("UPDATE metal_storage_batch_slice SET weight=weight-?,quantity=quantity-?,version=version+1,update_time=NOW() WHERE factory_id=? AND id=?",w,count,factory,toSlice);jdbc.update("UPDATE metal_storage_batch_slice SET weight=weight+?,quantity=quantity+?,version=version+1,update_time=NOW() WHERE factory_id=? AND id=?",w,count,factory,fromSlice);touched.add(fromSlice);touched.add(toSlice);
   }else{
    long transfer=id(a.get("wip_transfer_id"));var t=one("SELECT * FROM metal_production_wip_transfer WHERE factory_id=? AND id=? AND operation_id=? FOR UPDATE",factory,transfer,original);
    jdbc.update("UPDATE metal_production_execution SET carry_out_weight=carry_out_weight-?,carry_out_quantity=carry_out_quantity-?,version=version+1 WHERE factory_id=? AND id=?",w,count,factory,t.get("source_execution_id"));jdbc.update("UPDATE metal_production_execution SET carry_in_weight=carry_in_weight-?,carry_in_quantity=carry_in_quantity-?,version=version+1 WHERE factory_id=? AND id=?",w,count,factory,t.get("target_execution_id"));
   }
   movement(factory,reversal,null,to,from,optional(a.get("wip_transfer_id")),"REVERSAL",w,q);
  }
  for(var old:before){long source=id(old.get("id"));var now=one("SELECT * FROM metal_production_material_source WHERE factory_id=? AND id=? FOR UPDATE",factory,source);if(dec(now.get("weight")).compareTo(dec(old.get("weight")))!=0||!Objects.equals(optional(now.get("quantity")),optional(old.get("quantity"))))throw fail("撤销后来源余额与原确认快照不符");jdbc.update("UPDATE metal_production_material_source SET returnable_weight=?,returnable_quantity=? WHERE factory_id=? AND id=?",old.get("returnable_weight"),old.get("returnable_quantity"),factory,source);}
  for(long slice:touched){checkSlice(factory,slice);var row=one("SELECT stock_id FROM metal_storage_batch_slice WHERE factory_id=? AND id=?",factory,slice);new StorageBatchSliceService(jdbc,null).reconcile(factory,id(row.get("stock_id")));}
 }
 List<BigDecimal> initialExecutionComposition(long execution){
  if(!enabled())return null;var rows=rows("SELECT * FROM metal_production_material_source WHERE execution_id=? ORDER BY id FOR UPDATE",execution);if(rows.isEmpty())return null;
  BigDecimal cw=BigDecimal.ZERO,fw=BigDecimal.ZERO;for(var s:rows){
   if(!rows("SELECT a.id FROM metal_production_material_allocation a LEFT JOIN metal_storage_event e ON e.factory_id=a.factory_id AND e.id=a.storage_event_id WHERE a.to_source_id=? AND a.purpose IN ('ISSUE','WIP_TRANSFER') AND (e.reversed_by IS NOT NULL OR EXISTS(SELECT 1 FROM metal_production_execution_operation r WHERE r.factory_id=a.factory_id AND r.reversal_of=a.operation_id)) FOR UPDATE",s.get("id")).isEmpty())continue;
   BigDecimal w=dec(s.get("initial_weight"));for(var out:rows("SELECT a.weight FROM metal_production_material_allocation a LEFT JOIN metal_storage_event e ON e.id=a.storage_event_id WHERE a.from_source_id=? AND a.purpose IN ('RETURN','WIP_TRANSFER') AND (a.storage_event_id IS NULL OR e.reversed_by IS NULL) AND NOT EXISTS(SELECT 1 FROM metal_production_execution_operation r WHERE r.factory_id=a.factory_id AND r.reversal_of=a.operation_id) FOR UPDATE",s.get("id")))w=w.subtract(dec(out.get("weight")));if(w.signum()<0)throw fail("在制来源责任不守恒");if("CUSTOMER".equals(s.get("source_kind")))cw=cw.add(w);else fw=fw.add(w);
  }return List.of(cw.setScale(2),fw.setScale(2));
 }
 public Map<String,Object> remaining(long execution){
  var e=one("SELECT * FROM metal_production_execution WHERE id=?",execution);long f=id(e.get("factory_id"));BigDecimal cw=BigDecimal.ZERO,fw=BigDecimal.ZERO;long q=0;
  for(var s:sources(f,"execution_id",execution)){if("CUSTOMER".equals(s.get("source_kind")))cw=cw.add(dec(s.get("weight")));else fw=fw.add(dec(s.get("weight")));q+=qty(s);}
  return new LinkedHashMap<>(Map.of("weight",cw.add(fw).toPlainString(),"netWeight",cw.add(fw).toPlainString(),"quantity",q,"customerWeight",cw.toPlainString(),"factoryWeight",fw.toPlainString()));
 }
 public void recordLoss(long factory,long execution,BigDecimal loss,long lossQuantity){
  if(!enabled())return;var pool=sources(factory,"execution_id",execution);if(pool.isEmpty()){if(loss.signum()>0)throw fail("已确认损耗缺少精确来源，请先核对历史映射");return;}
  BigDecimal total=pool.stream().map(s->dec(s.get("weight"))).reduce(BigDecimal.ZERO,BigDecimal::add);long quantity=pool.stream().mapToLong(ProductionBatchMaterialService::qty).sum();if(total.compareTo(loss)!=0||quantity!=lossQuantity)throw fail("确认损耗与当前剩余来源件重不一致");
  for(var source:pool){BigDecimal w=dec(source.get("weight"));Long q=optional(source.get("quantity"));if(w.signum()==0&&(q==null||q==0))continue;consumeSource(id(source.get("id")),w,q);movement(factory,null,null,id(source.get("id")),null,null,"LOSS",w,q);}
 }
 private List<Map<String,Object>> stockParts(long factory,long slice,Map<String,Object> a){
  basis(a);accepted(factory,slice);BigDecimal w=positive(a);long q=qty(a);var sl=one("SELECT sl.*,st.version stock_version FROM metal_storage_batch_slice sl JOIN metal_storage_stock st ON st.id=sl.stock_id WHERE sl.factory_id=? AND sl.id=? FOR UPDATE",factory,slice);
  if(optional(a.get("stockId"))==null||id(a.get("stockId"))!=id(sl.get("stock_id"))||a.get("stockVersion")==null||id(a.get("stockVersion"))!=id(sl.get("stock_version"))||a.get("sliceVersion")==null||id(a.get("sliceVersion"))!=id(sl.get("version")))throw fail("库存或份额版本已变化，请重新核对");
  if(w.compareTo(dec(sl.get("weight")))>0||q>qty(sl))throw fail("拆合分配超过实际份额余额");
  var available=sources(factory,"stock_slice_id",slice).stream().filter(x->dec(x.get("weight")).signum()>0).toList();var parts=maps(a.get("sourceAllocations"));boolean full=w.compareTo(dec(sl.get("weight")))==0&&q==qty(sl);
  if(parts.isEmpty()){
   if(!full&&available.size()!=1)throw fail("部分分配涉及多个来源，请逐来源确认件重");parts=new ArrayList<>();
   for(var source:available){var p=new LinkedHashMap<String,Object>();p.put("sourceAllocationId",text(source.get("id")));p.put("currentOwnerId",text(source.get("current_owner_id")));p.put("netWeight",full?source.get("weight"):w);p.put("quantity",full?source.get("quantity"):q);parts.add(p);}
  }
  var checked=new ArrayList<Map<String,Object>>();var seen=new HashSet<Long>();BigDecimal total=BigDecimal.ZERO;long totalQ=0;var ownerW=new HashMap<Long,BigDecimal>();var ownerQ=new HashMap<Long,Long>();
  for(var part:parts){long source=id(part.get("sourceAllocationId"));if(!seen.add(source))throw fail("同一来源不可重复分配");var s=available.stream().filter(x->id(x.get("id"))==source).findFirst().orElseThrow(()->fail("所选来源不属于当前份额"));BigDecimal pw=positive(part);Long pq=part.get("quantity")==null?null:qty(part);if(pw.compareTo(dec(s.get("weight")))>0||pq!=null&&s.get("quantity")!=null&&pq>qty(s))throw fail("分配超过来源可用余额");if(optional(part.get("currentOwnerId"))!=null&&id(part.get("currentOwnerId"))!=id(s.get("current_owner_id")))throw fail("当前料权与来源不一致");var entry=new LinkedHashMap<>(s);entry.put("partWeight",pw);entry.put("partQuantity",pq);checked.add(entry);total=total.add(pw);if(pq!=null)totalQ+=pq;long owner=id(s.get("current_owner_id"));ownerW.merge(owner,pw,BigDecimal::add);ownerQ.merge(owner,pq==null?0:pq,Long::sum);}
  if(total.compareTo(w)!=0||totalQ!=q)throw fail("来源分配件重合计与本次转移不一致");var owners=maps(a.get("ownerAllocations"));
  if(owners.isEmpty()){if(!full&&ownerW.size()>1)throw fail("部分分配涉及多料权，请逐料权确认");}
  else{var seenOwner=new HashSet<Long>();for(var o:owners){long owner=id(o.get("ownerId"));if(!seenOwner.add(owner)||weight(o).compareTo(ownerW.getOrDefault(owner,BigDecimal.ZERO))!=0||qty(o)!=ownerQ.getOrDefault(owner,0L))throw fail("料权轴和来源轴不一致");}if(!seenOwner.equals(ownerW.keySet()))throw fail("料权分配未覆盖所有来源");}
  for(long owner:ownerW.keySet()){var o=one("SELECT * FROM metal_storage_batch_slice_owner WHERE factory_id=? AND stock_slice_id=? AND owner_id=? FOR UPDATE",factory,slice,owner);if(ownerW.get(owner).compareTo(dec(o.get("weight")))>0||ownerQ.get(owner)>qty(o))throw fail("当前份额料权不足");}
  return checked;
 }
 public void validateStock(long factory,long slice,Map<String,Object> allocation){stockParts(factory,slice,allocation);}
 public long transferStock(long factory,long operation,long slice,long targetBatch,Map<String,Object> a){
  var parts=stockParts(factory,slice,a);var sl=one("SELECT * FROM metal_storage_batch_slice WHERE factory_id=? AND id=? FOR UPDATE",factory,slice);long stock=id(sl.get("stock_id"));
  jdbc.update("INSERT IGNORE INTO metal_storage_batch_slice(factory_id,stock_id,batch_id,slice_kind) VALUES(?,?,?,'BATCH')",factory,stock,targetBatch);long target=jdbc.queryForObject("SELECT id FROM metal_storage_batch_slice WHERE factory_id=? AND stock_id=? AND batch_id=? FOR UPDATE",Long.class,factory,stock,targetBatch);
  for(var s:parts){BigDecimal w=dec(s.get("partWeight"));Long q=optional(s.get("partQuantity"));long count=q==null?0:q;long owner=id(s.get("current_owner_id"));jdbc.update("UPDATE metal_storage_batch_slice_owner SET weight=weight-?,quantity=quantity-? WHERE factory_id=? AND stock_slice_id=? AND owner_id=?",w,count,factory,slice,owner);jdbc.update("INSERT INTO metal_storage_batch_slice_owner(factory_id,stock_slice_id,owner_id,weight,quantity) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE weight=weight+VALUES(weight),quantity=quantity+VALUES(quantity)",factory,target,owner,w,count);consumeSource(id(s.get("id")),w,q);long to=insertSource(factory,target,null,id(s.get("id")),optional(s.get("root_issue_allocation_id")),owner,id(s.get("source_owner_id")),text(s.get("source_kind")),w,q,BigDecimal.ZERO,0);movement(factory,operation,null,id(s.get("id")),to,null,"IDENTITY_TRANSFER",w,q);}
  jdbc.update("UPDATE metal_storage_batch_slice SET weight=weight-?,quantity=quantity-?,version=version+1,update_time=NOW() WHERE factory_id=? AND id=?",weight(a),qty(a),factory,slice);jdbc.update("UPDATE metal_storage_batch_slice SET weight=weight+?,quantity=quantity+?,version=version+1,update_time=NOW() WHERE factory_id=? AND id=?",weight(a),qty(a),factory,target);checkSlice(factory,slice);checkSlice(factory,target);new StorageBatchSliceService(jdbc,null).reconcile(factory,stock);return target;
 }
 private List<Map<String,Object>> wipParts(long factory,long execution,List<Map<String,Object>> allocations){
  var available=sources(factory,"execution_id",execution);var result=new ArrayList<Map<String,Object>>();var usedW=new HashMap<Long,BigDecimal>();var usedQ=new HashMap<Long,Long>();var usedReturnW=new HashMap<Long,BigDecimal>();var usedReturnQ=new HashMap<Long,Long>();
  for(var a:allocations){basis(a);long source=id(a.get("sourceAllocationId"));var s=available.stream().filter(x->id(x.get("id"))==source).findFirst().orElseThrow(()->fail("在制来源不属于原加工记录"));BigDecimal w=positive(a);long q=qty(a);usedW.merge(source,w,BigDecimal::add);usedQ.merge(source,q,Long::sum);if(usedW.get(source).compareTo(dec(s.get("weight")))>0||s.get("quantity")!=null&&usedQ.get(source)>qty(s))throw fail("在制承接超过实际来源余额");var part=new LinkedHashMap<>(s);part.put("partWeight",w);part.put("partQuantity",q);BigDecimal rw=dec(a.getOrDefault("returnableWeight","0"));long rq=a.get("returnableQuantity")==null?0:id(a.get("returnableQuantity"));usedReturnW.merge(source,rw,BigDecimal::add);usedReturnQ.merge(source,rq,Long::sum);if(rw.signum()<0||rw.compareTo(w)>0||usedReturnW.get(source).compareTo(dec(s.get("returnable_weight")))>0||rq<0||rq>q||usedReturnQ.get(source)>id(s.get("returnable_quantity")))throw fail("承接可退原料责任超过已确认来源");part.put("partReturnableWeight",rw);part.put("partReturnableQuantity",rq);result.add(part);}
  if(result.isEmpty())throw fail("请选择明确的在制来源");return result;
 }
 public void validateWip(long factory,long execution,List<Map<String,Object>> allocations){wipParts(factory,execution,allocations);}
 public void transferWip(long factory,long operation,long source,long target,List<Map<String,Object>> allocations){
  var parts=wipParts(factory,source,allocations);var e=one("SELECT * FROM metal_production_execution WHERE factory_id=? AND id=? FOR UPDATE",factory,source);var t=one("SELECT * FROM metal_production_execution WHERE factory_id=? AND id=? FOR UPDATE",factory,target);if(id(e.get("order_step_id"))!=id(t.get("order_step_id")))throw fail("在制承接必须为同一工序节点");BigDecimal total=BigDecimal.ZERO;long count=0;for(var p:parts){total=total.add(dec(p.get("partWeight")));count+=id(p.get("partQuantity"));}
  long transfer=StorageSupport.insert(jdbc,"INSERT INTO metal_production_wip_transfer(factory_id,operation_id,source_execution_id,target_execution_id,order_step_id,weight,quantity,allocation_basis) VALUES(?,?,?,?,?,?,?,'CONFIRMED_BALANCE')",factory,operation,source,target,e.get("order_step_id"),total,count);
  for(var s:parts){BigDecimal w=dec(s.get("partWeight"));long q=id(s.get("partQuantity"));BigDecimal rw=dec(s.get("partReturnableWeight"));long rq=id(s.get("partReturnableQuantity"));consumeSource(id(s.get("id")),w,q);long to=insertSource(factory,null,target,id(s.get("id")),optional(s.get("root_issue_allocation_id")),id(s.get("current_owner_id")),id(s.get("source_owner_id")),text(s.get("source_kind")),w,q,rw,rq);movement(factory,operation,null,id(s.get("id")),to,transfer,"WIP_TRANSFER",w,q);}
  jdbc.update("UPDATE metal_production_execution SET carry_out_weight=carry_out_weight+?,carry_out_quantity=carry_out_quantity+?,version=version+1 WHERE factory_id=? AND id=?",total,count,factory,source);jdbc.update("UPDATE metal_production_execution SET carry_in_weight=carry_in_weight+?,carry_in_quantity=carry_in_quantity+?,version=version+1 WHERE factory_id=? AND id=?",total,count,factory,target);
 }
 private void consumeSource(long id,BigDecimal w,Long q){int changed=jdbc.update("UPDATE metal_production_material_source SET weight=weight-?,quantity=CASE WHEN quantity IS NULL THEN NULL ELSE quantity-? END,returnable_weight=LEAST(returnable_weight,weight),returnable_quantity=LEAST(returnable_quantity,COALESCE(quantity,returnable_quantity)) WHERE id=? AND weight>=? AND (quantity IS NULL OR quantity>=?)",w,q==null?0:q,id,w,q==null?0:q);if(changed!=1)throw fail("来源余额已变化，请重新核对");}
 private void checkSlice(long factory,long slice){var s=one("SELECT * FROM metal_storage_batch_slice WHERE factory_id=? AND id=?",factory,slice);BigDecimal total=BigDecimal.ZERO;var byOwner=new HashMap<Long,BigDecimal>();for(var p:sources(factory,"stock_slice_id",slice)){total=total.add(dec(p.get("weight")));byOwner.merge(id(p.get("current_owner_id")),dec(p.get("weight")),BigDecimal::add);}if(total.compareTo(dec(s.get("weight")))!=0)throw fail("份额来源重量不守恒");for(var owner:rows("SELECT * FROM metal_storage_batch_slice_owner WHERE factory_id=? AND stock_slice_id=?",factory,slice)){if(byOwner.getOrDefault(id(owner.get("owner_id")),BigDecimal.ZERO).compareTo(dec(owner.get("weight")))!=0)throw fail("份额来源与当前料权不守恒");byOwner.remove(id(owner.get("owner_id")));}if(byOwner.values().stream().anyMatch(w->w.signum()!=0))throw fail("来源存在未匹配料权");}
 static Long inputSourceQuantity(boolean piece,String stockMode,Long quantity){
  // Weight-managed input has no piece balance; output pieces are measured after processing.
  return !piece?Long.valueOf(0):"WEIGHT".equals(stockMode)?null:quantity;
 }
 private void recordInput(long factory,long execution,Map<String,Object> event){
  String stockMode=jdbc.queryForObject("SELECT measurement_mode FROM metal_storage_stock WHERE factory_id=? AND id=?",String.class,factory,event.get("stock_id"));
  long storage=jdbc.queryForObject("SELECT id FROM metal_storage_event WHERE factory_id=? AND document_id=? AND line_id=? AND action='ISSUE' AND reversed_by IS NULL",Long.class,factory,event.get("document_id"),event.get("line_id"));
  var parts=rows("SELECT a.*,s.source_owner_id root_owner,s.current_owner_id issued_owner,s.source_kind,s.root_issue_allocation_id root_issue FROM metal_production_material_allocation a JOIN metal_production_material_source s ON s.id=a.from_source_id WHERE a.factory_id=? AND a.storage_event_id=? AND a.purpose='ISSUE' ORDER BY a.id FOR UPDATE",factory,storage);
  // Measured output pieces belong to the document; raw-source pieces do not predict finished pieces.
  boolean automatic=jdbc.queryForObject("SELECT b.planned_quantity IS NULL FROM metal_production_execution e JOIN metal_production_batch b ON b.id=e.batch_id AND b.factory_id=e.factory_id WHERE e.factory_id=? AND e.id=?",Boolean.class,factory,execution);
  long customer=jdbc.queryForObject("SELECT o.customer_id FROM metal_production_execution e JOIN metal_production_order o ON o.id=e.order_id WHERE e.id=?",Long.class,execution);boolean piece="PIECE".equals(jdbc.queryForObject("SELECT s.measurement_mode FROM metal_production_execution e JOIN metal_production_order_step s ON s.id=e.order_step_id WHERE e.id=?",String.class,execution));
  if(!parts.isEmpty()){BigDecimal sent=parts.stream().map(p->dec(p.get("weight"))).reduce(BigDecimal.ZERO,BigDecimal::add);BigDecimal received=dec(event.get("weight"));if(sent.compareTo(received)!=0)throw fail("来源不同的交接损耗需明确逐来源实收分配");for(var p:parts){long issued=jdbc.queryForObject("SELECT id FROM metal_storage_issue_allocation WHERE factory_id=? AND document_id=? AND line_id=? AND source_owner_id=?",Long.class,factory,event.get("document_id"),event.get("line_id"),p.get("issued_owner"));Long partQuantity=automatic?null:inputSourceQuantity(piece,stockMode,optional(p.get("quantity")));long target=insertSource(factory,null,execution,id(p.get("from_source_id")),issued,customer,id(p.get("root_owner")),text(p.get("source_kind")),dec(p.get("weight")),partQuantity,dec(p.get("weight")),partQuantity==null?0:partQuantity);jdbc.update("UPDATE metal_production_material_allocation SET to_source_id=? WHERE factory_id=? AND id=?",target,factory,p.get("id"));}return;}
  var stock=one("SELECT st.origin_order_id,CAST(st.identity_snapshot AS CHAR) identity_snapshot FROM metal_storage_stock st WHERE st.factory_id=? AND st.id=?",factory,event.get("stock_id"));var identity=JSON.parseObject(text(stock.get("identity_snapshot")));if(identity!=null&&identity.get("sourceExecutionId")!=null)throw fail("历史生产库存缺少精确份额来源，须先受控映射，不能按当前料权重写供料来源");
  for(var a:rows("SELECT * FROM metal_storage_issue_allocation WHERE factory_id=? AND document_id=? AND line_id=? ORDER BY id FOR UPDATE",factory,event.get("document_id"),event.get("line_id"))){BigDecimal w=dec(a.get("received_weight"));Long q=automatic?null:inputSourceQuantity(piece,stockMode,id(a.get("received_quantity")));long target=insertSource(factory,null,execution,null,id(a.get("id")),customer,id(a.get("source_owner_id")),id(a.get("source_owner_id"))==customer?"CUSTOMER":"FACTORY",w,q,w,q==null?0:q);movement(factory,null,storage,null,target,null,"ISSUE",w,q);}
 }
 static boolean wholeUnknownQuantity(List<Map<String,Object>> sources,BigDecimal weight,long quantity){return quantity>0&&!sources.isEmpty()&&sources.stream().allMatch(s->s.get("quantity")==null)&&sources.stream().map(s->dec(s.get("weight"))).reduce(BigDecimal.ZERO,BigDecimal::add).compareTo(weight)==0;}
 private List<Map<String,Object>> consumeExecution(long factory,long execution,Long storage,String purpose,BigDecimal w,long q,BigDecimal customer,BigDecimal factoryWeight){
  var result=new ArrayList<Map<String,Object>>();var pool=sources(factory,"execution_id",execution).stream().filter(s->dec(s.get("weight")).signum()>0).toList();boolean output=Set.of("OUTPUT","RECOVERY").contains(purpose);boolean aggregate=q>0&&!pool.isEmpty()&&pool.stream().allMatch(s->s.get("quantity")==null);boolean single=output&&q>0&&pool.size()==1&&pool.get(0).get("quantity")==null;
  if(output&&q>0&&pool.size()>1&&pool.stream().anyMatch(s->s.get("quantity")==null)&&!aggregate)throw fail("UNKNOWN_SOURCE_QUANTITY: 多来源件数未知，仅支持全余量实际产出；逐来源拆件须先确认");
  BigDecimal remaining=w,cLeft=customer,fLeft=factoryWeight;long remainingQ=q;
  for(var s:pool){if(remaining.signum()==0)break;BigDecimal cap=dec(s.get("weight"));if(customer!=null)cap=cap.min("CUSTOMER".equals(s.get("source_kind"))?cLeft:fLeft);BigDecimal take=cap.min(remaining);if(take.signum()==0)continue;Long tq;if(s.get("quantity")==null)tq=single?Long.valueOf(remainingQ):null;else tq=Math.min(remainingQ,qty(s));if(tq!=null&&remainingQ>0&&take.compareTo(dec(s.get("weight")))<0&&pool.size()>1)throw fail("多来源计件在制部分产出缺少逐来源件数依据");if("RETURN".equals(purpose)){if(dec(s.get("returnable_weight")).compareTo(take)<0||id(s.get("returnable_quantity"))<(tq==null?0:tq))throw fail("当前来源无足够已确认未加工可退责任");jdbc.update("UPDATE metal_production_material_source SET returnable_weight=returnable_weight-?,returnable_quantity=returnable_quantity-? WHERE id=?",take,tq==null?0:tq,s.get("id"));}consumeSource(id(s.get("id")),take,tq);if(storage!=null&&!output)movement(factory,null,storage,id(s.get("id")),null,null,purpose,take,tq);var part=new LinkedHashMap<>(s);part.put("partWeight",take);part.put("partQuantity",tq);result.add(part);remaining=remaining.subtract(take);remainingQ-=tq==null?0:tq;if(customer!=null){if("CUSTOMER".equals(s.get("source_kind")))cLeft=cLeft.subtract(take);else fLeft=fLeft.subtract(take);}}
  if(remaining.signum()!=0||remainingQ!=0&&!aggregate)throw fail("实际产出超过当前来源件重余额");return result;
 }
 public void recordProductionEvent(long factory,long execution,long productionEvent){
  if(!enabled()||jdbc.queryForObject("SELECT batch_id FROM metal_production_execution WHERE factory_id=? AND id=?",Long.class,factory,execution)==null)return;var ev=one("SELECT * FROM metal_production_execution_event WHERE factory_id=? AND execution_id=? AND id=?",factory,execution,productionEvent);String type=text(ev.get("event_type"));
  if("INPUT".equals(type)){recordInput(factory,execution,ev);return;}
  long event=jdbc.queryForObject("SELECT id FROM metal_storage_event WHERE factory_id=? AND document_id=? AND line_id=? AND reversed_by IS NULL",Long.class,factory,ev.get("document_id"),ev.get("line_id"));
  // A carried return is consumed by its explicit authority in the warehouse transaction.
  var actual=JSON.parseObject(text(jdbc.queryForObject("SELECT CAST(result AS CHAR) FROM metal_storage_event WHERE id=?",String.class,event)));
  if("RETURN".equals(type)&&actual.get("carryAllocationId")!=null)return;
  var parts=consumeExecution(factory,execution,event,type,dec(ev.get("weight")),qty(ev),Set.of("OUTPUT","RECOVERY").contains(type)?dec(ev.get("customer_weight")):null,Set.of("OUTPUT","RECOVERY").contains(type)?dec(ev.get("factory_weight")):null);
  if(Set.of("OUTPUT","RECOVERY").contains(type)){Long slice=jdbc.queryForObject("SELECT stock_slice_id FROM metal_storage_event WHERE id=?",Long.class,event);if(slice==null)throw fail("产出缺少库存份额");var owners=rows("SELECT owner_id FROM metal_storage_batch_slice_owner WHERE factory_id=? AND stock_slice_id=? AND weight>0",factory,slice);if(owners.size()!=1)throw fail("产出料权来源不明确");long owner=id(owners.get(0).get("owner_id"));for(var p:parts){long to=insertSource(factory,slice,null,id(p.get("id")),optional(p.get("root_issue_allocation_id")),owner,id(p.get("source_owner_id")),text(p.get("source_kind")),dec(p.get("partWeight")),optional(p.get("partQuantity")),BigDecimal.ZERO,0);movement(factory,null,event,id(p.get("id")),to,null,type,dec(p.get("partWeight")),optional(p.get("partQuantity")));}checkSlice(factory,slice);}
 }
 /** Called before a real ownership delta; uninitialized historical/ordinary stock is left unmapped. */
 void consumeSlice(long factory,long event,long slice,long owner,BigDecimal delta,long quantity,String purpose){
  if(!enabled())return;var all=sources(factory,"stock_slice_id",slice);if(all.isEmpty())return;
  if(delta.signum()>0){restoreSlice(factory,event,slice,owner,delta,quantity,purpose);return;}if(delta.signum()==0){if(quantity!=0)throw fail("有精确来源的份额不能只调整件数而不确认来源重量");return;}
  var owned=all.stream().filter(s->id(s.get("current_owner_id"))==owner&&dec(s.get("weight")).signum()>0).toList();boolean aggregate=Set.of("ISSUE","DISPATCH").contains(purpose)&&quantity<0&&!owned.isEmpty()&&owned.stream().allMatch(s->s.get("quantity")==null);if(aggregate){var actual=one("SELECT weight,quantity FROM metal_storage_batch_slice_owner WHERE factory_id=? AND stock_slice_id=? AND owner_id=? FOR UPDATE",factory,slice,owner);if(dec(actual.get("weight")).compareTo(delta.negate())<0||qty(actual)<-quantity)throw fail("实际领用超过料权份额的已确认件重余额");}
  BigDecimal left=delta.negate();long leftQ=-quantity;for(var source:all){if(id(source.get("current_owner_id"))!=owner||left.signum()==0)continue;BigDecimal w=dec(source.get("weight")).min(left);Long q=source.get("quantity")==null?null:Math.min(leftQ,qty(source));consumeSource(id(source.get("id")),w,q);movement(factory,null,event,id(source.get("id")),null,null,Set.of("ISSUE","DISPATCH","ADJUSTMENT").contains(purpose)?purpose:"ADJUSTMENT",w,q);left=left.subtract(w);leftQ-=q==null?0:q;}if(left.signum()!=0||leftQ!=0&&!aggregate)throw fail("UNKNOWN_SOURCE_QUANTITY: 所选份额来源余额不足或逐来源件数未确认");
 }
 private void restoreSlice(long factory,long event,long slice,long owner,BigDecimal w,long q,String purpose){
  if("REPAYMENT".equals(purpose)){
   BigDecimal left=w;long leftQ=q;for(var move:rows("SELECT a.*,s.source_owner_id,s.source_kind,s.root_issue_allocation_id FROM metal_production_material_allocation a JOIN metal_production_material_source s ON s.id=a.from_source_id WHERE a.factory_id=? AND a.storage_event_id=? AND a.to_source_id IS NULL AND s.stock_slice_id=? ORDER BY a.id FOR UPDATE",factory,event,slice)){
    if(left.signum()==0)break;BigDecimal mw=dec(move.get("weight"));long mq=qty(move);if(mw.compareTo(left)>0||mq>leftQ)throw fail("料权返还须明确逐来源份额，不得混用两轴");long target=insertSource(factory,slice,null,id(move.get("from_source_id")),optional(move.get("root_issue_allocation_id")),owner,id(move.get("source_owner_id")),text(move.get("source_kind")),mw,optional(move.get("quantity")),BigDecimal.ZERO,0);jdbc.update("UPDATE metal_production_material_allocation SET to_source_id=? WHERE factory_id=? AND id=?",target,factory,move.get("id"));left=left.subtract(mw);leftQ-=mq;
   }if(left.signum()!=0||leftQ!=0)throw fail("料权返还缺少对应来源转出");return;
  }
  if("RETURN".equals(purpose)){
   var roots=rows("SELECT DISTINCT s.* FROM metal_storage_return_allocation r JOIN metal_storage_issue_allocation issue ON issue.id=r.issue_allocation_id JOIN metal_production_material_allocation a ON a.storage_event_id=issue.event_id JOIN metal_production_material_source s ON s.id=a.from_source_id WHERE a.factory_id=? AND r.event_id=? AND a.purpose='ISSUE' AND s.stock_slice_id=? FOR UPDATE",factory,event,slice);
   if(roots.size()!=1)throw fail("退料涉及多个材料来源，请先明确逐来源退回分配");var root=roots.get(0);long target=insertSource(factory,slice,null,id(root.get("id")),optional(root.get("root_issue_allocation_id")),owner,id(root.get("source_owner_id")),text(root.get("source_kind")),w,q,BigDecimal.ZERO,0);movement(factory,null,event,null,target,null,"RETURN",w,q);return;
  }
  throw fail("生产份额增加量缺少可核实来源，不允许自动推算");
 }
 List<BigDecimal> issueComposition(long factory,long event,BigDecimal received){var rows=rows("SELECT s.source_kind,a.weight FROM metal_production_material_allocation a JOIN metal_production_material_source s ON s.id=a.from_source_id WHERE a.factory_id=? AND a.storage_event_id=? AND a.purpose='ISSUE' ORDER BY a.id",factory,event);if(rows.isEmpty())return null;BigDecimal cw=BigDecimal.ZERO,fw=BigDecimal.ZERO;for(var r:rows){if("CUSTOMER".equals(r.get("source_kind")))cw=cw.add(dec(r.get("weight")));else fw=fw.add(dec(r.get("weight")));}return ProductionSourceBalances.take(new ArrayList<>(List.of(cw,fw)),received);}
 public void consumeCarryReturn(long factory,long source,long execution,long issueLine,BigDecimal w,long q,long storageEvent){
  var s=one("SELECT s.*,a.line_id FROM metal_production_material_source s JOIN metal_storage_issue_allocation a ON a.id=s.root_issue_allocation_id WHERE s.factory_id=? AND s.id=? AND s.execution_id=? FOR UPDATE",factory,source,execution);if(id(s.get("line_id"))!=issueLine||dec(s.get("returnable_weight")).compareTo(w)<0||id(s.get("returnable_quantity"))<q)throw fail("当前承接记录没有这份未加工原料的可退责任");jdbc.update("UPDATE metal_production_material_source SET returnable_weight=returnable_weight-?,returnable_quantity=returnable_quantity-? WHERE factory_id=? AND id=?",w,q,factory,source);consumeSource(source,w,q);movement(factory,null,storageEvent,source,null,null,"RETURN",w,q);
 }
 public void requireCarryReturn(long factory,long execution,Map<String,Object> line){
  Long carry=optional(line.get("carryAllocationId"));if(carry==null)throw fail("退回承接原料须指定当前可退责任来源");
  var s=one("SELECT s.*,a.line_id,a.stock_id,a.stock_slice_id FROM metal_production_material_source s JOIN metal_storage_issue_allocation a ON a.id=s.root_issue_allocation_id WHERE s.factory_id=? AND s.id=? AND s.execution_id=? FOR UPDATE",factory,carry,execution);
  BigDecimal w=dec(line.getOrDefault("sentWeight",line.get("netWeight")));long q=line.get("sentQuantity")==null?qty(line):id(line.get("sentQuantity"));
  if(optional(line.get("issueLineId"))==null||id(line.get("issueLineId"))!=id(s.get("line_id"))||id(line.get("stockId"))!=id(s.get("stock_id"))||w.signum()<=0||dec(s.get("returnable_weight")).compareTo(w)<0||id(s.get("returnable_quantity"))<q)throw fail("当前承接记录没有所选原领料的可退责任");
 }
 long createReturnResidual(long factory,long event,long sourceBatch,long originalStock,long targetStock,BigDecimal returned,long quantity){
  var source=one("SELECT * FROM metal_production_batch WHERE factory_id=? AND id=? FOR UPDATE",factory,sourceBatch);
  if(!Set.of("SPLIT","MERGED").contains(text(source.get("lifecycle"))))throw fail("退料来源不是已退出生产批次");
  var stock=one("SELECT origin_step_id,measurement_mode,origin_order_id FROM metal_storage_stock WHERE factory_id=? AND id=? FOR UPDATE",factory,originalStock);
  var steps=JSON.parseObject(text(source.get("route_snapshot"))).getJSONArray("steps");var completed=new ArrayList<String>();boolean found=false;
  for(int i=0;i<steps.size();i++){String step=steps.getJSONObject(i).getString("id");completed.add(step);if(step.equals(text(stock.get("origin_step_id")))){found=true;break;}}
  if(!found||id(source.get("order_id"))!=id(stock.get("origin_order_id")))throw fail("退回实物的原完成阶段无法核实");
  String request=UUID.nameUUIDFromBytes(("RETURN_RESIDUAL:"+factory+":"+event).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();long actor=com.ruoyi.common.utils.SecurityUtils.getUserId();
  long op=StorageSupport.insert(jdbc,"INSERT INTO metal_production_execution_operation(factory_id,actor_id,request_id,action,request_hash,result,reason) VALUES(?,?,?,'RETURN_RESIDUAL',?,'{}',?)",factory,actor,request,hash(Map.of("eventId",event,"sourceBatchId",sourceBatch)),"已退出生产来源原样退回，承接原实物阶段，不新增生产目标");
  long target=new ProductionBatchService(jdbc,null,null).createTransformed(factory,source,null,"RETURN_RESIDUAL",op,completed);
  jdbc.update("INSERT INTO metal_production_batch_edge(factory_id,operation_id,source_batch_id,target_batch_id,planned_quantity_part,weight,quantity,measurement_mode,allocation_basis) VALUES(?,?,?,?,NULL,?,?,?,'CONFIRMED_BALANCE')",factory,op,sourceBatch,target,returned,"WEIGHT".equals(stock.get("measurement_mode"))?null:quantity,stock.get("measurement_mode"));
  var result=new LinkedHashMap<String,Object>();result.put("operationId",text(op));result.put("requestId",request);result.put("committed",true);result.put("sourceBatchId",text(sourceBatch));result.put("batchId",text(target));result.put("returnEventId",text(event));result.put("stockId",text(targetStock));result.put("plannedQuantity",null);result.put("completedStepIds",completed);
  jdbc.update("UPDATE metal_production_execution_operation SET result=? WHERE factory_id=? AND id=?",JSON.toJSONString(result,com.alibaba.fastjson2.JSONWriter.Feature.WriteMapNullValue),factory,op);return target;
 }
 void bindResidualSources(long factory,long event,long slice,Map<String,Object> input,BigDecimal factoryWeight,long factoryQuantity,BigDecimal customerWeight,long customerQuantity,long customer){
  long execution=id(input.get("executionId"));Long carry=optional(input.get("carryAllocationId"));
  var roots=carry==null?rows("SELECT s.* FROM metal_production_material_source s JOIN metal_storage_issue_allocation a ON a.id=s.root_issue_allocation_id WHERE s.factory_id=? AND s.execution_id=? AND a.line_id=? AND s.weight>0 ORDER BY s.id FOR UPDATE",factory,execution,id(input.get("issueLineId"))):rows("SELECT * FROM metal_production_material_source WHERE factory_id=? AND execution_id=? AND id=? FOR UPDATE",factory,execution,carry);
  if(roots.size()!=1)throw fail("退回退出来源批次需要唯一明确的材料来源责任，请逐来源办理");var source=roots.get(0);
  for(var part:List.of(Map.of("owner",0L,"weight",factoryWeight,"quantity",factoryQuantity),Map.of("owner",customer,"weight",customerWeight,"quantity",customerQuantity))){BigDecimal w=(BigDecimal)part.get("weight");long q=(Long)part.get("quantity");if(w.signum()==0&&q==0)continue;long to=insertSource(factory,slice,null,id(source.get("id")),optional(source.get("root_issue_allocation_id")),(Long)part.get("owner"),id(source.get("source_owner_id")),text(source.get("source_kind")),w,q,BigDecimal.ZERO,0);movement(factory,null,event,null,to,null,"RETURN",w,q);}
  checkSlice(factory,slice);
 }
 void reverseReturnResidual(long factory,long original,long reversal){
  var rows=rows("SELECT DISTINCT b.* FROM metal_storage_event e JOIN metal_storage_batch_slice sl ON sl.id=e.stock_slice_id AND sl.factory_id=e.factory_id JOIN metal_production_batch b ON b.id=sl.batch_id AND b.factory_id=sl.factory_id WHERE e.factory_id=? AND e.id=? AND e.action='RETURN' AND b.origin_action='RETURN_RESIDUAL' FOR UPDATE",factory,original);
  for(var batch:rows){long bid=id(batch.get("id"));if(!"ACTIVE".equals(batch.get("lifecycle"))||!rows("SELECT id FROM metal_production_execution WHERE factory_id=? AND batch_id=?",factory,bid).isEmpty()||!rows("SELECT id FROM metal_production_batch_edge WHERE factory_id=? AND source_batch_id=?",factory,bid).isEmpty())throw fail("退料承接批次已有下游，须先逆序处理");
   if(jdbc.queryForObject("SELECT COUNT(*) FROM metal_storage_batch_slice WHERE factory_id=? AND batch_id=? AND (weight<>0 OR quantity<>0)",Integer.class,factory,bid)>0)throw fail("退料承接批次实物未完整逆转");
   jdbc.update("UPDATE metal_production_batch SET lifecycle='CANCELLED',version=version+1,update_time=NOW() WHERE factory_id=? AND id=?",factory,bid);
   String request=UUID.nameUUIDFromBytes(("REVERSE_RETURN_RESIDUAL:"+factory+":"+reversal).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();var result=Map.of("batchId",text(bid),"lifecycle","CANCELLED","originalReturnEventId",text(original),"reversalEventId",text(reversal));
   StorageSupport.insert(jdbc,"INSERT INTO metal_production_execution_operation(factory_id,actor_id,request_id,action,request_hash,result,reason,reversal_of) VALUES(?,?,?,'REVERSE_RETURN_RESIDUAL',?,?,?,?)",factory,com.ruoyi.common.utils.SecurityUtils.getUserId(),request,hash(result),JSON.toJSONString(result),"原样退料冲销，关闭无下游的余料承接身份",batch.get("origin_operation_id"));
  }
 }
 public void reverseSources(long factory,long original,long reversal){
  if(!enabled())return;for(var a:rows("SELECT * FROM metal_production_material_allocation WHERE factory_id=? AND storage_event_id=? AND purpose<>'REVERSAL' ORDER BY id DESC FOR UPDATE",factory,original)){if(a.get("to_source_id")!=null){var s=one("SELECT * FROM metal_production_material_source WHERE factory_id=? AND id=? FOR UPDATE",factory,a.get("to_source_id"));if(dec(s.get("weight")).compareTo(dec(s.get("initial_weight")))!=0)throw fail("来源已有下游消费，须先逆序处理");consumeSource(id(s.get("id")),dec(a.get("weight")),optional(a.get("quantity")));}if(a.get("from_source_id")!=null){boolean legacy=one("SELECT * FROM metal_production_material_source WHERE factory_id=? AND id=? FOR UPDATE",factory,a.get("from_source_id")).get("legacy_adoption_operation_id")!=null;jdbc.update("UPDATE metal_production_material_source SET weight=weight+?,quantity=CASE WHEN quantity IS NULL THEN NULL ELSE quantity+? END,returnable_weight=CASE WHEN root_issue_allocation_id IS NOT NULL AND execution_id IS NOT NULL THEN returnable_weight+? ELSE returnable_weight END,returnable_quantity=CASE WHEN root_issue_allocation_id IS NOT NULL AND execution_id IS NOT NULL THEN returnable_quantity+? ELSE returnable_quantity END WHERE factory_id=? AND id=?",a.get("weight"),a.get("quantity")==null?0:a.get("quantity"),legacy?BigDecimal.ZERO:a.get("weight"),legacy||a.get("quantity")==null?0:a.get("quantity"),factory,a.get("from_source_id"));}movement(factory,null,reversal,optional(a.get("to_source_id")),optional(a.get("from_source_id")),null,"REVERSAL",dec(a.get("weight")),optional(a.get("quantity")));}
 }
}














