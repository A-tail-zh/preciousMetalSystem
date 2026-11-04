package com.ruoyi.metal.service;
import java.util.*;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;
import com.ruoyi.common.exception.ServiceException;import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.metal.catalog.OutboundSubmissionRequests.*;import com.ruoyi.metal.catalog.WarehouseRequests.Order;
import com.ruoyi.metal.security.FactoryScope;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.jdbc.support.GeneratedKeyHolder;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.*;
@Service @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
public class OutboundSubmissionService {
 private final JdbcTemplate jdbc;private final FactoryScope scope;private final WarehouseService warehouse;private final OutboundDraftService drafts;
 public OutboundSubmissionService(JdbcTemplate jdbc,FactoryScope scope,WarehouseService warehouse,OutboundDraftService drafts){this.jdbc=jdbc;this.scope=scope;this.warehouse=warehouse;this.drafts=drafts;}
 private void permit(String p){if(!scope.isAdmin()&&!SecurityUtils.hasPermi(p))throw new ServiceException("没有此操作权限",403);}
 private static Long n(Object x){return x==null?null:Long.valueOf(x.toString());}
 private static String hash(Request r){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(r.toString().getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 private Order order(Request r,Group g,Long batch,Integer version){People p=g.peopleOverride()==null?new People(r.operatorIds(),r.recipientId(),r.keeperId(),r.reviewerId()):g.peopleOverride();return new Order(OutboundSubmissionRules.childRequest(r.requestId(),g.warehouseId()),r.factoryId(),g.warehouseId(),"PRODUCTION_OUT",r.orderDate(),null,p.operatorIds()==null?List.of():p.operatorIds(),p.keeperId(),p.recipientId(),null,p.reviewerId(),r.workOrderId(),batch,version,false,null,null,g.lines(),r.itemId(),r.processId());}
 public Map<String,Object> submit(Request r){
  permit("metal:warehouse-order:add");OutboundSubmissionRules.validate(r,false);scope.check(r.factoryId());scope.lockFactories(List.of(r.factoryId()));warehouse.reserveOrderNumber(r.requestId(),"OUT");
  if(jdbc.queryForObject("SELECT COUNT(*) FROM metal_warehouse_order WHERE request_id=?",Long.class,r.requestId())>0)throw new ServiceException("请求编号已用于单仓出库，不能更改内容或切换为多仓重试",409);
  var old=jdbc.queryForList("SELECT * FROM metal_outbound_submission WHERE request_id=? FOR UPDATE",r.requestId());String fingerprint=hash(r);
  if(!old.isEmpty()){var p=old.get(0);if(!Objects.equals(n(p.get("request_actor")),SecurityUtils.getUserId())||!fingerprint.equals(p.get("request_hash")))throw new ServiceException("请求编号已使用，提交内容或操作人不一致",409);return result(n(p.get("submission_id")),true);}
  var groups=r.groups().stream().sorted(Comparator.comparing(Group::warehouseId)).toList();var all=r.groups().stream().flatMap(g->g.lines().stream()).toList();
  var lockInput=new Group(groups.get(0).warehouseId(),null,all);warehouse.lockPostingSources(order(r,lockInput,r.batchId(),r.batchVersion()));
  var bound=warehouse.bindInboundSubmission(order(r,groups.get(0),r.batchId(),r.batchVersion()));long batch=bound.batchId();var actual=jdbc.queryForMap("SELECT * FROM metal_process_batch WHERE batch_id=? FOR UPDATE",batch);
  if(bound.batchVersion()==null||n(actual.get("version"))!=bound.batchVersion().longValue())throw new ServiceException("批次版本已变化，请刷新后重试",409);
  for(long id:all.stream().map(x->x.inventoryId()).filter(Objects::nonNull).distinct().sorted().toList()){var stocks=jdbc.queryForList("SELECT inventory_id,factory_id FROM metal_inventory WHERE inventory_id=? AND del_flag='0' FOR UPDATE",id);if(stocks.size()!=1||!r.factoryId().equals(n(stocks.get(0).get("factory_id"))))throw new ServiceException("库存不存在或不属于本厂",403);}
  for(long id:all.stream().map(x->x.releaseId()).filter(Objects::nonNull).distinct().sorted().toList())jdbc.queryForList("SELECT release_id FROM metal_semi_release WHERE release_id=? FOR UPDATE",id);
  Request current=new Request(r.requestId(),r.factoryId(),r.type(),r.orderDate(),r.operatorIds(),r.recipientId(),r.keeperId(),r.reviewerId(),r.workOrderId(),r.itemId(),r.processId(),batch,bound.batchVersion(),r.groups());drafts.requireValid(drafts.inspect(current));
  var key=new GeneratedKeyHolder();jdbc.update(c->{var ps=c.prepareStatement("INSERT INTO metal_outbound_submission(request_id,request_actor,request_hash,factory_id,type,order_date,work_order_id,item_id,process_id,batch_id) VALUES(?,?,?,?,?,?,?,?,?,?)",java.sql.Statement.RETURN_GENERATED_KEYS);Object[] args={r.requestId(),SecurityUtils.getUserId(),fingerprint,r.factoryId(),r.type(),r.orderDate(),r.workOrderId(),r.itemId(),r.processId(),batch};for(int i=0;i<args.length;i++)ps.setObject(i+1,args[i]);return ps;},key);long id=key.getKey().longValue();
  for(var g:groups)try{int version=jdbc.queryForObject("SELECT version FROM metal_process_batch WHERE batch_id=?",Integer.class,batch);warehouse.postOutboundChild(order(r,g,batch,version),id);}catch(ServiceException e){String field=e.getMessage().contains("人员")?"peopleOverride":"lines";throw new OutboundFieldException(List.of(Map.of("field","groups["+r.groups().indexOf(g)+"]."+field,"message","仓库"+g.warehouseId()+"："+e.getMessage(),"code","INVALID_GROUP")));}
  return result(id,false);
 }
 @Transactional(readOnly=true,isolation=Isolation.READ_COMMITTED)
 public boolean hasRequest(String request){return !jdbc.queryForList("SELECT submission_id FROM metal_outbound_submission WHERE request_id=?",request).isEmpty();}
 public Map<String,Object> detail(long id){permit("metal:warehouse-order:query");return result(id);}
 private Map<String,Object> result(long id,boolean reused){var out=result(id);out.put("reused",reused);return out;}
 private Map<String,Object> result(long id){var rows=jdbc.queryForList("SELECT * FROM metal_outbound_submission WHERE submission_id=?",id);if(rows.isEmpty())throw new ServiceException("出库提交记录不存在");var root=rows.get(0);scope.check(n(root.get("factory_id")));var out=new LinkedHashMap<String,Object>();out.put("submissionId",Long.toString(id));out.put("batchId",root.get("batch_id").toString());var batch=jdbc.queryForMap("SELECT batch_no,version,status FROM metal_process_batch WHERE batch_id=?",root.get("batch_id"));out.put("batchNo",batch.get("batch_no"));out.put("batchVersion",batch.get("version"));out.put("batchStatus",batch.get("status"));out.put("orders",jdbc.queryForList("SELECT CAST(order_id AS CHAR) id,order_no orderNo,CAST(warehouse_id AS CHAR) warehouseId,warehouse_name warehouseName,status FROM metal_warehouse_order WHERE outbound_submission_id=? ORDER BY warehouse_id",id));return out;}
}
