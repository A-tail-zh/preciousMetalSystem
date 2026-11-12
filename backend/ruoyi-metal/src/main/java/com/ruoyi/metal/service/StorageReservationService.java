package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.util.*;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.metal.security.FactoryScope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.ruoyi.metal.service.StorageSupport.*;

/** Factory locking serializes reservations and every ledger decrease. Expiry is checked in SQL. */
@Service
@Transactional(rollbackFor=Exception.class)
public class StorageReservationService {
 private final JdbcTemplate jdbc; private final FactoryScope scope; private final ProductionExecutionService production;
 private static final Set<String> OUT=Set.of("PRODUCTION_OUT","PRODUCT_OUT","SEMI_OUT","RAW_OUT");
 private static final String ACTIVE="(s.status='BOUND' OR (s.status='TEMP' AND s.expires_at>NOW()))";
 public StorageReservationService(JdbcTemplate jdbc,FactoryScope scope){this(jdbc,scope,new ProductionExecutionService(jdbc,scope));}
 @org.springframework.beans.factory.annotation.Autowired
 public StorageReservationService(JdbcTemplate jdbc,FactoryScope scope,ProductionExecutionService production){this.jdbc=jdbc;this.scope=scope;this.production=production;}
 private long factory(Object value){return scope.resolveFactory(optionalId(value));}
 private void permission(){allow(scope,"metal:warehouse-order:add","metal:warehouse-order:edit");}
 private Map<String,Object> session(long f,String session,boolean lock){
  return new LinkedHashMap<>(one(jdbc,"SELECT *,IF(status='TEMP' AND expires_at<=NOW(),'EXPIRED',status) current_status,DATE_FORMAT(expires_at,'%Y-%m-%d %H:%i:%s') expiresAt FROM metal_storage_reservation_session WHERE factory_id=? AND actor_id=? AND session_id=?"+(lock?" FOR UPDATE":""),f,SecurityUtils.getUserId(),uuid(session)));
 }
 private void editableDocument(long f,Object document){if(optionalId(document)==null)return;var doc=one(jdbc,"SELECT creator_id,status FROM metal_storage_document WHERE factory_id=? AND id=? AND deleted=0 FOR UPDATE",f,id(document));if(id(doc.get("creator_id"))!=SecurityUtils.getUserId()||!Set.of("DRAFT","RETURNED").contains(text(doc.get("status"))))throw new ServiceException("仅本人草稿或退回单据可重新预占",409);}
 private String context(Map<String,Object> doc){var fields=new LinkedHashMap<String,Object>();for(String key:List.of("type","orderId","executionId","customerId"))fields.put(key,text(doc.get(key)));if(optionalId(doc.get("batchId"))!=null)fields.put("batchId",text(doc.get("batchId")));return hash(fields);}
 private void customer(long f,long owner){if(owner!=0)one(jdbc,"SELECT customer_id FROM metal_customer WHERE factory_id=? AND customer_id=? AND del_flag='0'",f,owner);}
 private Map<String,Object> documentContext(long f,Map<String,Object> body){var doc=new LinkedHashMap<>(body);production.resolveSourceDocument(f,doc);Long order=optionalId(body.get("orderId"));if(order!=null){var row=one(jdbc,"SELECT customer_id FROM metal_production_order WHERE factory_id=? AND id=? AND deleted=0 FOR UPDATE",f,order);doc.put("customerId",text(row.get("customer_id")));}if(!OUT.contains(text(doc.get("type"))))throw fail("只有出库单据需要库存预占");if(text(doc.get("type")).equals("PRODUCTION_OUT")){if(order==null)throw fail("领料须关联生产工单");if("EXISTING".equals(text(doc.get("executionMode")))&&optionalId(doc.get("executionId"))==null)throw fail("补领料请选择加工批次");if(optionalId(doc.get("executionId"))==null&&(optionalId(doc.get("orderItemId"))==null||optionalId(doc.get("orderStepId"))==null||optionalId(doc.get("processorId"))==null))throw fail("请选择加工批次，或填写产品组、工序和加工人员以自动建批");}if(!text(doc.get("type")).equals("PRODUCTION_OUT")&&owner(doc.get("customerId"))==0)throw fail("提料出厂须指定客户");customer(f,owner(doc.get("customerId")));if(optionalId(doc.get("executionId"))!=null){var execution=one(jdbc,"SELECT order_id,batch_id,status FROM metal_production_execution WHERE factory_id=? AND id=? FOR UPDATE",f,id(doc.get("executionId")));if(order==null||id(execution.get("order_id"))!=order)throw fail("加工记录与工单不一致");Long actualBatch=optionalId(execution.get("batch_id")),suppliedBatch=optionalId(doc.get("batchId"));if(suppliedBatch!=null&&!Objects.equals(actualBatch,suppliedBatch))throw fail("生产批次与加工记录不一致");if(actualBatch!=null)doc.put("batchId",actualBatch.toString());}return doc;}
 public Map<String,Object> replace(Map<String,Object> body){
  long f=factory(body.get("factoryId"));permission();scope.lockFactories(List.of(f));String sid=uuid(body.get("sessionId"));var old=replay(f,body,"replace",sid);if(old!=null)return old;
  editableDocument(f,body.get("documentId"));var doc=documentContext(f,body);var found=jdbc.queryForList("SELECT session_id FROM metal_storage_reservation_session WHERE session_id=? FOR UPDATE",sid);Map<String,Object> current=null;
  if(!found.isEmpty()){current=session(f,sid,true);if(!Set.of("TEMP","EXPIRED","RELEASED").contains(text(current.get("current_status"))))throw fail("已提交复验的预占不能由编辑窗口更改");checkVersion(current,body);if(current.get("document_id")!=null&&!text(current.get("document_id")).equals(text(body.get("documentId"))))throw fail("预占会话所属单据不一致");}
  else if(version(body.getOrDefault("reservationVersion",0))!=0)throw fail("预占会话不存在，请重新确认物料");
  var lines=normalize(f,sid,doc,maps(body.getOrDefault("lines",List.of())));Long previousAuto=current==null?null:optionalId(current.get("auto_execution_id"));
  if(text(doc.get("type")).equals("PRODUCTION_OUT")){production.ensureExecutionForOutbound(f,doc,lines);production.validateDocument(f,doc,lines);}
  Long ensured=optionalId(doc.get("executionId"));
  Long autoExecution=Boolean.TRUE.equals(doc.get("autoCreatedExecution"))?ensured:null;
  if(autoExecution==null&&previousAuto!=null&&Objects.equals(previousAuto,ensured))autoExecution=previousAuto;
  if(autoExecution==null&&ensured!=null&&jdbc.queryForObject("SELECT COUNT(*) FROM metal_production_execution WHERE factory_id=? AND id=? AND auto_created=1",Integer.class,f,ensured)>0)autoExecution=ensured;
  int next=current==null?1:version(current.get("version"))+1;
  if(current==null)jdbc.update("INSERT INTO metal_storage_reservation_session(session_id,factory_id,actor_id,document_id,auto_execution_id,version,expires_at,context_hash,data) VALUES(?,?,?,?,?,?,DATE_ADD(NOW(),INTERVAL 30 MINUTE),?,?)",sid,f,SecurityUtils.getUserId(),optionalId(body.get("documentId")),autoExecution,next,context(doc),JSON.toJSONString(lines));
  else jdbc.update("UPDATE metal_storage_reservation_session SET document_id=?,auto_execution_id=?,version=?,status='TEMP',expires_at=DATE_ADD(NOW(),INTERVAL 30 MINUTE),context_hash=?,data=?,update_time=NOW() WHERE session_id=?",optionalId(body.get("documentId")),autoExecution,next,context(doc),JSON.toJSONString(lines),sid);
  if(previousAuto!=null&&!Objects.equals(previousAuto,autoExecution))production.cancelUnusedAutoExecution(f,previousAuto,optionalId(body.get("documentId")));
  jdbc.update("DELETE FROM metal_storage_reservation WHERE session_id=?",sid);
  for(var line:lines)for(var a:allocations(doc,line))jdbc.update("INSERT INTO metal_storage_reservation(session_id,factory_id,reservation_line_id,stock_id,owner_id,weight,quantity,stock_slice_id) VALUES(?,?,?,?,?,?,?,?)",sid,f,uuid(line.get("reservationLineId")),id(line.get("stockId")),owner(a.get("ownerId")),decimal(a.get("weight"),false),quantity(a.getOrDefault("quantity",0)).longValueExact(),optionalId(line.get("stockSliceId")));
  var out=result(session(f,sid,false));if(doc.get("batchId")!=null)out.put("batchId",text(doc.get("batchId")));if(doc.get("executionId")!=null){out.put("executionId",text(doc.get("executionId")));out.put("processorId",text(doc.get("processorId")));out.put("orderItemId",text(doc.get("orderItemId")));out.put("orderStepId",text(doc.get("orderStepId")));if(Boolean.TRUE.equals(doc.get("autoCreatedExecution")))out.put("autoCreatedExecution",true);}return remember(f,body,"replace",sid,out);
}
 private long owner(Object value){return "0".equals(text(value))?0:id(value);}
 private void checkVersion(Map<String,Object> current,Map<String,Object> body){if(version(current.get("version"))!=version(body.get("reservationVersion")))throw new ServiceException("预占版本已变化，请核实会话后重试",409);}
 public Map<String,Object> change(String sid,String action,Map<String,Object> body){
  if(!Set.of("renew","release").contains(action))throw fail("预占操作无效");long f=factory(body.get("factoryId"));permission();scope.lockFactories(List.of(f));sid=uuid(sid);var old=replay(f,body,action,sid);if(old!=null)return old;var s=session(f,sid,true);
  // Closing a window must never release a concurrently submitted document.
  if(action.equals("release")&&!text(s.get("status")).equals("TEMP"))return remember(f,body,action,sid,result(s));
  checkVersion(s,body);if(action.equals("renew")){if(!text(s.get("current_status")).equals("TEMP"))throw fail("预占已失效或已提交，请核实后重新确认物料");jdbc.update("UPDATE metal_storage_reservation_session SET expires_at=DATE_ADD(NOW(),INTERVAL 30 MINUTE),update_time=NOW() WHERE session_id=? AND status='TEMP'",sid);}
  else{
   Long auto=optionalId(s.get("auto_execution_id"));
   jdbc.update("UPDATE metal_storage_reservation_session SET status='RELEASED',version=version+1,auto_execution_id=NULL,update_time=NOW() WHERE session_id=? AND status='TEMP'",sid);
   if(auto!=null&&s.get("document_id")==null)production.cancelUnusedAutoExecution(f,auto,null);
  }
  return remember(f,body,action,sid,result(session(f,sid,false)));
}
 public Map<String,Object> status(String sid,Long requested){long f=factory(requested);permission();return result(session(f,uuid(sid),false));}
 private Map<String,Object> result(Map<String,Object> s){var out=new LinkedHashMap<String,Object>();out.put("sessionId",s.get("session_id"));out.put("version",s.get("version"));out.put("status",s.get("current_status"));out.put("expiresAt",s.get("expiresAt"));out.put("documentId",s.get("document_id")==null?null:text(s.get("document_id")));out.put("lines",JSON.parseArray(text(s.get("data")),Map.class));return out;}
 public Map<String,Object> operation(String request,Long requested){long f=factory(requested);permission();var row=one(jdbc,"SELECT CAST(result AS CHAR) result FROM metal_storage_reservation_operation WHERE factory_id=? AND actor_id=? AND request_id=?",f,SecurityUtils.getUserId(),uuid(request));var out=JSON.<Map<String,Object>>parseObject(text(row.get("result")),Map.class);var now=result(session(f,text(out.get("sessionId")),false));out.putAll(now);out.put("current",now);return out;}
 private Map<String,Object> replay(long f,Map<String,Object> body,String action,String sid){String request=uuid(body.get("requestId"));var rows=jdbc.queryForList("SELECT action,session_id,request_hash,CAST(result AS CHAR) result FROM metal_storage_reservation_operation WHERE factory_id=? AND actor_id=? AND request_id=? FOR UPDATE",f,SecurityUtils.getUserId(),request);if(rows.isEmpty())return null;var row=rows.get(0);if(!action.equals(text(row.get("action")))||!sid.equals(text(row.get("session_id")))||!hash(body).equals(text(row.get("request_hash"))))throw fail("同一请求标识的操作内容已改变");var out=JSON.<Map<String,Object>>parseObject(text(row.get("result")),Map.class);var now=session(f,sid,true);out.putAll(result(now));out.put("current",result(now));return out;}
 private Map<String,Object> remember(long f,Map<String,Object> body,String action,String sid,Map<String,Object> result){jdbc.update("INSERT INTO metal_storage_reservation_operation(factory_id,actor_id,request_id,session_id,action,request_hash,result) VALUES(?,?,?,?,?,?,?)",f,SecurityUtils.getUserId(),uuid(body.get("requestId")),sid,action,hash(body),JSON.toJSONString(result));return result;}
 BigDecimal[] held(long f,long stock,Long owner,String excluded){return held(f,stock,owner,excluded,false);}
 private BigDecimal[] held(long f,long stock,Long owner,String excluded,boolean current){var args=new ArrayList<Object>(List.of(f,stock));String filter="";if(owner!=null){filter+=" AND r.owner_id=?";args.add(owner);}if(excluded!=null){filter+=" AND r.session_id<>?";args.add(excluded);}String from=" FROM metal_storage_reservation r JOIN metal_storage_reservation_session s ON s.session_id=r.session_id AND s.factory_id=r.factory_id WHERE r.factory_id=? AND r.stock_id=? AND "+ACTIVE+filter;
  // Factory scope reads may already have opened a REPEATABLE READ snapshot before waiting for its lock.
  var rows=jdbc.queryForList(current?"SELECT r.weight,r.quantity"+from+" FOR UPDATE":"SELECT COALESCE(SUM(r.weight),0) weight,COALESCE(SUM(r.quantity),0) quantity"+from,args.toArray());BigDecimal weight=BigDecimal.ZERO,count=BigDecimal.ZERO;for(var row:rows){weight=weight.add(new BigDecimal(text(row.get("weight"))));count=count.add(new BigDecimal(text(row.get("quantity"))));}return new BigDecimal[]{weight,count};}
 public void protect(long f,long stock,Long owner,BigDecimal weight,long quantity,BigDecimal delta,long count){if(delta.signum()>=0&&count>=0)return;var h=held(f,stock,owner,null,true);if(weight.add(delta).compareTo(h[0])<0||BigDecimal.valueOf(quantity).add(BigDecimal.valueOf(count)).compareTo(h[1])<0)throw fail("本次扣减会占用其他单据已预占的库存或料权，请释放预占后重试");}
 private BigDecimal[] heldSlice(long f,long stock,long slice,Long owner,String excluded,boolean current){
  var args=new ArrayList<Object>(List.of(f,stock,slice));String filter="";
  if(owner!=null){filter+=" AND r.owner_id=?";args.add(owner);}
  if(excluded!=null){filter+=" AND r.session_id<>?";args.add(excluded);}
  // Legacy NULL reservations conservatively protect every candidate slice of that stock.
  String from=" FROM metal_storage_reservation r JOIN metal_storage_reservation_session s ON s.session_id=r.session_id AND s.factory_id=r.factory_id WHERE r.factory_id=? AND r.stock_id=? AND (r.stock_slice_id=? OR r.stock_slice_id IS NULL) AND "+ACTIVE+filter;
  var rows=jdbc.queryForList(current?"SELECT r.weight,r.quantity"+from+" FOR UPDATE":"SELECT COALESCE(SUM(r.weight),0) weight,COALESCE(SUM(r.quantity),0) quantity"+from,args.toArray());
  BigDecimal weight=BigDecimal.ZERO,count=BigDecimal.ZERO;for(var row:rows){weight=weight.add(new BigDecimal(text(row.get("weight"))));count=count.add(new BigDecimal(text(row.get("quantity"))));}
  return new BigDecimal[]{weight,count};
 }
 public void protectSlice(long f,long stock,long slice,Long owner,BigDecimal weight,long quantity,BigDecimal delta,long count){
  if(delta.signum()>=0&&count>=0)return;var held=heldSlice(f,stock,slice,owner,null,true);
  if(weight.add(delta).compareTo(held[0])<0||BigDecimal.valueOf(quantity).add(BigDecimal.valueOf(count)).compareTo(held[1])<0)throw fail("本次扣减会占用其他单据已预占的批次份额或份额料权，请释放预占后重试");
 }
 void sliceAvailability(Map<String,Object> out,long f,long stock,long slice,Long owner,String excluded,String weightKey){
  var held=heldSlice(f,stock,slice,owner,excluded,false);
  out.put("reservedWeight",held[0].toPlainString());out.put("reservedQuantity",held[1].longValueExact());
  out.put("availableWeight",new BigDecimal(text(out.get(weightKey))).subtract(held[0]).max(BigDecimal.ZERO).toPlainString());
  out.put("availableQuantity",new BigDecimal(text(out.get("quantity"))).subtract(held[1]).max(BigDecimal.ZERO).longValueExact());
 }
 private BigDecimal[] availableSlice(long f,long stock,long slice,Long owner,String sid){
  var rows=owner==null?jdbc.queryForList("SELECT weight,quantity FROM metal_storage_batch_slice WHERE factory_id=? AND stock_id=? AND id=? FOR UPDATE",f,stock,slice):jdbc.queryForList("SELECT o.weight,o.quantity FROM metal_storage_batch_slice_owner o JOIN metal_storage_batch_slice b ON b.factory_id=o.factory_id AND b.id=o.stock_slice_id WHERE b.factory_id=? AND b.stock_id=? AND b.id=? AND o.owner_id=? FOR UPDATE",f,stock,slice,owner);
  var held=heldSlice(f,stock,slice,owner,sid,true);
  return new BigDecimal[]{(rows.isEmpty()?BigDecimal.ZERO:decimal(rows.get(0).get("weight"),false)).subtract(held[0]),(rows.isEmpty()?BigDecimal.ZERO:quantity(rows.get(0).get("quantity"))).subtract(held[1])};
 }
 void availability(Map<String,Object> out,long f,long stock,Long owner,String excluded,String weightKey){var h=held(f,stock,owner,excluded);out.put("reservedWeight",h[0].toPlainString());out.put("reservedQuantity",h[1].longValueExact());out.put("availableWeight",new BigDecimal(text(out.get(weightKey))).subtract(h[0]).max(BigDecimal.ZERO).toPlainString());out.put("availableQuantity",new BigDecimal(text(out.get("quantity"))).subtract(h[1]).max(BigDecimal.ZERO).longValueExact());}
 String ownExclusion(long f,Object sid){if(text(sid).isEmpty())return null;var s=session(f,uuid(sid),false);return text(s.get("current_status")).equals("TEMP")?uuid(sid):null;}

 private List<Map<String,Object>> allocations(Map<String,Object> doc,Map<String,Object> line){if(text(doc.get("type")).equals("PRODUCTION_OUT"))return maps(line.getOrDefault("allocations",List.of()));return List.of(new LinkedHashMap<>(Map.of("ownerId",doc.get("customerId"),"weight",line.getOrDefault("sentWeight",line.get("netWeight")),"quantity",line.getOrDefault("sentQuantity",line.getOrDefault("quantity",0)))));}
 private record Share(long stock,long owner){}
 private void add(Map<Share,BigDecimal[]> demand,long stock,long owner,BigDecimal w,BigDecimal q){var sum=demand.computeIfAbsent(new Share(stock,owner),x->new BigDecimal[]{BigDecimal.ZERO,BigDecimal.ZERO});sum[0]=sum[0].add(w);sum[1]=sum[1].add(q);}
 private BigDecimal[] available(long f,long stock,Long owner,String sid){var rows=owner==null?jdbc.queryForList("SELECT net_weight weight,quantity FROM metal_storage_stock WHERE factory_id=? AND id=? FOR UPDATE",f,stock):jdbc.queryForList("SELECT weight,quantity FROM metal_storage_ownership WHERE factory_id=? AND stock_id=? AND owner_id=? FOR UPDATE",f,stock,owner);var h=held(f,stock,owner,sid,true);return new BigDecimal[]{(rows.isEmpty()?BigDecimal.ZERO:decimal(rows.get(0).get("weight"),false)).subtract(h[0]),(rows.isEmpty()?BigDecimal.ZERO:quantity(rows.get(0).get("quantity"))).subtract(h[1])};}
 private void insufficientDefault(long f,long stock,BigDecimal[] available,BigDecimal[] required){insufficient(f,stock,-1L,available,required);}
 private void insufficient(long f,long stock,Long owner,BigDecimal[] available,BigDecimal[] required){var s=one(jdbc,"SELECT s.batch_no,w.name warehouse_name FROM metal_storage_stock s JOIN metal_warehouse w ON w.warehouse_id=s.warehouse_id AND w.factory_id=s.factory_id WHERE s.factory_id=? AND s.id=?",f,stock);String who=owner==null?"实物":owner==-1?"本客户及工厂自有料权（计件须有整件可用重量）":owner==0?"工厂自有料权":text(one(jdbc,"SELECT customer_name FROM metal_customer WHERE factory_id=? AND customer_id=?",f,owner).get("customer_name"))+"料权";throw fail("仓库“"+s.get("warehouse_name")+"”批次“"+s.get("batch_no")+"”"+who+"不足：可用"+available[0].max(BigDecimal.ZERO)+"g / "+available[1].max(BigDecimal.ZERO)+"件，申请"+required[0]+"g / "+required[1]+"件，不足"+required[0].subtract(available[0]).max(BigDecimal.ZERO)+"g / "+required[1].subtract(available[1]).max(BigDecimal.ZERO)+"件");}
 private record SliceShare(long stock,long slice,long owner){}
 private void addSlice(Map<SliceShare,BigDecimal[]> demand,long stock,long slice,long owner,BigDecimal weight,BigDecimal count){
  var value=demand.computeIfAbsent(new SliceShare(stock,slice,owner),key->new BigDecimal[]{BigDecimal.ZERO,BigDecimal.ZERO});value[0]=value[0].add(weight);value[1]=value[1].add(count);
 }
 private BigDecimal[] remainingSlice(long f,long stock,long slice,long owner,String sid,Map<Share,BigDecimal[]> stockUsed,Map<SliceShare,BigDecimal[]> sliceUsed){
  var all=available(f,stock,owner,sid);var part=availableSlice(f,stock,slice,owner,sid);
  var used=stockUsed.getOrDefault(new Share(stock,owner),new BigDecimal[]{BigDecimal.ZERO,BigDecimal.ZERO});var usedPart=sliceUsed.getOrDefault(new SliceShare(stock,slice,owner),new BigDecimal[]{BigDecimal.ZERO,BigDecimal.ZERO});
  return new BigDecimal[]{all[0].subtract(used[0]).min(part[0].subtract(usedPart[0])).max(BigDecimal.ZERO),all[1].subtract(used[1]).min(part[1].subtract(usedPart[1])).max(BigDecimal.ZERO)};
 }
 private List<Map<String,Object>> normalize(long f,String sid,Map<String,Object> doc,List<Map<String,Object>> lines){
  var seen=new HashSet<String>();var physical=new HashMap<Share,BigDecimal[]>();var shares=new HashMap<Share,BigDecimal[]>();
  var slicePhysical=new HashMap<SliceShare,BigDecimal[]>();var sliceOwners=new HashMap<SliceShare,BigDecimal[]>();
  var slices=new StorageBatchSliceService(jdbc,scope,this);
  for(var line:lines){
   String local=uuid(line.get("reservationLineId"));if(!seen.add(local))throw fail("预占明细标识重复");line.put("reservationLineId",local);
   long stock=id(line.get("stockId"));var s=one(jdbc,"SELECT s.*,w.status warehouse_status,w.del_flag warehouse_deleted FROM metal_storage_stock s JOIN metal_warehouse w ON w.warehouse_id=s.warehouse_id AND w.factory_id=s.factory_id WHERE s.factory_id=? AND s.id=? FOR UPDATE",f,stock);
   if(!"0".equals(text(s.get("warehouse_status")))||!"0".equals(text(s.get("warehouse_deleted"))))throw fail("仓库已停用或删除");
   if(optionalId(line.get("warehouseId"))!=null&&id(line.get("warehouseId"))!=id(s.get("warehouse_id")))throw fail("库存与所选仓库不一致");
   String type=text(doc.get("type")),category=text(s.get("category"));
   if(type.equals("PRODUCTION_OUT")&&!Set.of("RAW","SEMI").contains(category)||!type.equals("PRODUCTION_OUT")&&!type.equals(category+"_OUT"))throw fail("库存类别与出库用途不符");
   line.put("warehouseId",text(s.get("warehouse_id")));line.put("measurementMode",s.get("measurement_mode"));boolean piece="PIECE".equals(s.get("measurement_mode"));
   BigDecimal w=decimal(line.get("netWeight"),false),q=piece?quantity(line.get("quantity")):BigDecimal.ZERO;
   if(w.signum()<=0||piece&&q.signum()<=0)throw fail("出库重量及计件数量须大于零");
   if(!piece&&!text(line.get("quantity")).isBlank()&&quantity(line.get("quantity")).signum()!=0)throw fail("按重量库存不接受有效件数");
   var selected=slices.resolve(f,stock,line,optionalId(doc.get("batchId"))!=null);long slice=id(selected.get("id"));
   line.put("stockSliceId",String.valueOf(slice));line.put("sliceVersion",version(selected.get("version")));
   line.put("sourceBatchId",selected.get("batch_id")==null?null:text(selected.get("batch_id")));
   line.put("netWeight",w.toPlainString());line.put("quantity",q.longValueExact());line.remove("sentWeight");line.remove("sentQuantity");line.put("customerId",text(doc.get("customerId")));
   add(physical,stock,0,w,q);addSlice(slicePhysical,stock,slice,0,w,q);
   if(type.equals("PRODUCTION_OUT")){
    var a=maps(line.getOrDefault("allocations",List.of()));
    if(a.isEmpty()){
     BigDecimal rw=w,rq=q;
     for(long owner:new LinkedHashSet<>(List.of(id(doc.get("customerId")),0L))){
      var av=remainingSlice(f,stock,slice,owner,sid,shares,sliceOwners);
      BigDecimal takeQ=piece?rq.min(av[1]).min(rw.signum()==0?BigDecimal.ZERO:av[0].multiply(rq).divide(rw,0,java.math.RoundingMode.DOWN)):BigDecimal.ZERO;
      BigDecimal takeW=piece?(takeQ.compareTo(rq)==0?rw:rw.multiply(takeQ).divide(rq,2,java.math.RoundingMode.DOWN)):rw.min(av[0]);
      if(takeW.signum()==0||piece&&takeQ.signum()==0)continue;
      a.add(new LinkedHashMap<>(Map.of("ownerId",String.valueOf(owner),"weight",takeW.toPlainString(),"quantity",takeQ.longValueExact())));
      rw=rw.subtract(takeW);rq=rq.subtract(takeQ);if(rw.signum()==0&&rq.signum()==0)break;
     }
     if(rw.signum()>0||rq.signum()>0)insufficientDefault(f,stock,new BigDecimal[]{w.subtract(rw),q.subtract(rq)},new BigDecimal[]{w,q});
    }
    line.put("allocations",a);
   }
   BigDecimal aw=BigDecimal.ZERO,aq=BigDecimal.ZERO;var owners=new HashSet<Long>();var normalized=allocations(doc,line);
   for(var a:normalized){
    long owner=owner(a.get("ownerId"));customer(f,owner);if(!owners.add(owner))throw fail("同一来源份额不得重复");
    BigDecimal weight=decimal(a.get("weight"),false),count=piece?quantity(a.get("quantity")):BigDecimal.ZERO;
    if(!piece&&!text(a.get("quantity")).isBlank()&&quantity(a.get("quantity")).signum()!=0)throw fail("按重量来源不接受有效件数");
    if(weight.signum()<=0||piece&&count.signum()<=0)throw fail("来源预占重量和计件数量须大于零");
    a.put("weight",weight.toPlainString());a.put("quantity",count.longValueExact());add(shares,stock,owner,weight,count);addSlice(sliceOwners,stock,slice,owner,weight,count);aw=aw.add(weight);aq=aq.add(count);
   }
   if(aw.compareTo(w)!=0||aq.compareTo(q)!=0)throw fail("来源预占合计须等于本行重量和件数");
   if(type.equals("PRODUCTION_OUT"))line.put("allocations",normalized);
  }
  for(var e:physical.entrySet()){var av=available(f,e.getKey().stock(),null,sid);if(e.getValue()[0].compareTo(av[0])>0||e.getValue()[1].compareTo(av[1])>0)insufficient(f,e.getKey().stock(),null,av,e.getValue());}
  for(var e:shares.entrySet()){var av=available(f,e.getKey().stock(),e.getKey().owner(),sid);if(e.getValue()[0].compareTo(av[0])>0||e.getValue()[1].compareTo(av[1])>0)insufficient(f,e.getKey().stock(),e.getKey().owner(),av,e.getValue());}
  for(var e:slicePhysical.entrySet()){var k=e.getKey();var av=availableSlice(f,k.stock(),k.slice(),null,sid);if(e.getValue()[0].compareTo(av[0])>0||e.getValue()[1].compareTo(av[1])>0)throw fail("所选生产批次的库存份额不足，请重新核对");}
  for(var e:sliceOwners.entrySet()){var k=e.getKey();var av=availableSlice(f,k.stock(),k.slice(),k.owner(),sid);if(e.getValue()[0].compareTo(av[0])>0||e.getValue()[1].compareTo(av[1])>0)throw fail("所选生产批次的份额料权不足，请重新核对");}
  return lines;
 }
 public void attachDraft(long f,long document,Map<String,Object> doc){if(text(doc.get("sessionId")).isEmpty())return;String sid=uuid(doc.get("sessionId"));var s=session(f,sid,true);if(text(s.get("status")).equals("BOUND")||text(s.get("status")).equals("CONSUMED"))throw fail("已提交的预占不能关联草稿");if(s.get("document_id")!=null&&id(s.get("document_id"))!=document)throw fail("预占会话已属于其他单据");jdbc.update("UPDATE metal_storage_reservation_session SET document_id=?,update_time=NOW() WHERE session_id=?",document,sid);}
 public void bind(long f,long document,Map<String,Object> doc,List<Map<String,Object>> lines){if(text(doc.get("sessionId")).isEmpty())throw fail("原单无有效库存预占，请重新选择来源、确认物料后提交复验");String sid=uuid(doc.get("sessionId"));var s=session(f,sid,true);if(!text(s.get("current_status")).equals("TEMP"))throw fail("物料预占已失效，请重新确认明细后提交");checkVersion(s,doc);if(s.get("document_id")!=null&&id(s.get("document_id"))!=document)throw fail("预占会话已属于其他单据");if(!context(documentContext(f,doc)).equals(text(s.get("context_hash"))))throw fail("工单或出库客户已变化，请重新确认物料");verifyContents(f,sid,doc,lines,true);for(var line:lines)jdbc.update("UPDATE metal_storage_reservation SET document_line_id=? WHERE session_id=? AND reservation_line_id=?",id(line.get("id")),sid,uuid(line.get("reservationLineId")));jdbc.update("UPDATE metal_storage_reservation_session SET document_id=?,status='BOUND',version=version+1,update_time=NOW() WHERE session_id=? AND status='TEMP'",document,sid);}
 public void consume(long f,long document,Map<String,Object> doc,List<Map<String,Object>> lines){var sessions=jdbc.queryForList("SELECT session_id FROM metal_storage_reservation_session WHERE factory_id=? AND document_id=? AND status='BOUND' FOR UPDATE",f,document);if(sessions.size()!=1)throw fail("原单无有效库存预占，请退回发起人重新选择来源、确认物料并提交复验");String sid=text(sessions.get(0).get("session_id"));verifyContents(f,sid,doc,lines,false);jdbc.update("UPDATE metal_storage_reservation_session SET status='CONSUMED',version=version+1,update_time=NOW() WHERE session_id=? AND status='BOUND'",sid);}
 private void verifyContents(long f,String sid,Map<String,Object> doc,List<Map<String,Object>> lines,boolean exact){
  String error=exact?"单据明细与已确认预占不一致，请重新确认物料":"实际发出超过本单已预占的份额或料权来源不完整，请退回发起人补足预占并重新提交";
  var rows=jdbc.queryForList("SELECT reservation_line_id,stock_id,stock_slice_id,owner_id,weight,quantity FROM metal_storage_reservation WHERE factory_id=? AND session_id=? ORDER BY stock_id,owner_id FOR UPDATE",f,sid);var held=new HashMap<String,Map<String,Object>>();for(var r:rows)held.put(text(r.get("reservation_line_id"))+":"+r.get("owner_id"),r);var used=new HashSet<String>();
  for(var line:lines){String local=uuid(line.get("reservationLineId"));BigDecimal totalWeight=BigDecimal.ZERO,totalQuantity=BigDecimal.ZERO;boolean piece="PIECE".equals(text(line.get("measurementMode")));
   for(var a:allocations(doc,line)){String key=local+":"+owner(a.get("ownerId"));var r=held.get(key);BigDecimal w=decimal(a.get("weight"),false),q=quantity(a.getOrDefault("quantity",0));if(w.signum()<=0||piece&&q.signum()<=0||!piece&&q.signum()!=0||!used.add(key)||r==null||id(r.get("stock_id"))!=id(line.get("stockId"))||!Objects.equals(optionalId(r.get("stock_slice_id")),optionalId(line.get("stockSliceId")))||w.compareTo(decimal(r.get("weight"),false))>0||q.compareTo(quantity(r.get("quantity")))>0||exact&&(w.compareTo(decimal(r.get("weight"),false))!=0||q.compareTo(quantity(r.get("quantity")))!=0))throw fail(error);totalWeight=totalWeight.add(w);totalQuantity=totalQuantity.add(q);}
   BigDecimal sent=decimal(line.getOrDefault("sentWeight",line.get("netWeight")),false),sentQuantity=piece?quantity(line.getOrDefault("sentQuantity",line.getOrDefault("quantity",0))):BigDecimal.ZERO;if(totalWeight.compareTo(sent)!=0||totalQuantity.compareTo(sentQuantity)!=0)throw fail(error);
  }
  if(used.size()!=held.size())throw fail(error);
 }
 public void releaseDocument(long f,long document){jdbc.update("UPDATE metal_storage_reservation_session SET status='RELEASED',version=version+1,update_time=NOW() WHERE factory_id=? AND document_id=? AND status IN ('TEMP','BOUND')",f,document);}
}
