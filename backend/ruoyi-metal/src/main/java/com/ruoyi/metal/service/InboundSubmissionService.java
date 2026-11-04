package com.ruoyi.metal.service;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.metal.catalog.InboundSubmissionRequests.*;
import com.ruoyi.metal.catalog.WarehouseRequests.Order;
import com.ruoyi.metal.catalog.WarehouseRequests.Action;
import com.ruoyi.metal.security.FactoryScope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service
@Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
public class InboundSubmissionService {
 private final JdbcTemplate jdbc;private final FactoryScope scope;private final WarehouseService warehouse;
 public InboundSubmissionService(JdbcTemplate jdbc,FactoryScope scope,WarehouseService warehouse){this.jdbc=jdbc;this.scope=scope;this.warehouse=warehouse;}
 private void permit(String p){if(!scope.isAdmin()&&!SecurityUtils.hasPermi(p))throw new ServiceException("没有此操作权限",403);}
 private static Long number(Object x){return x==null?null:Long.valueOf(x.toString());}
 private static String hash(Request r){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(r.toString().getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 private Order order(Request r,Group g,Long batch,Integer version){People p=g.peopleOverride()==null?new People(r.operatorIds(),r.keeperId(),r.inspectorId(),r.reviewerId()):g.peopleOverride();return new Order(InboundSubmissionRules.childRequest(r.requestId(),g.warehouseId()),r.factoryId(),g.warehouseId(),r.type(),r.orderDate(),null,p.operatorIds(),p.keeperId(),null,p.inspectorId(),p.reviewerId(),r.workOrderId(),batch,version,false,null,null,g.lines(),r.itemId(),r.processId());}
 public Map<String,Object> submit(Request r){
  permit("metal:warehouse-order:add");InboundSubmissionRules.validate(r);scope.check(r.factoryId());scope.lockFactories(List.of(r.factoryId()));
  warehouse.reserveOrderNumber(r.requestId(),"IN");
  if(jdbc.queryForObject("SELECT COUNT(*) FROM metal_warehouse_order WHERE request_id=?",Long.class,r.requestId())>0)throw new ServiceException("请求编号已用于单仓入库，不能更改内容或切换为多仓重试",409);
  String fingerprint=hash(r);var previous=jdbc.queryForList("SELECT * FROM metal_inbound_submission WHERE request_id=? FOR UPDATE",r.requestId());
  if(!previous.isEmpty()){var old=previous.get(0);if(!Objects.equals(number(old.get("request_actor")),SecurityUtils.getUserId())||!fingerprint.equals(old.get("request_hash")))throw new ServiceException("请求编号已使用，提交内容或操作人不一致",409);return result(number(old.get("submission_id")),true);}
  var groups=r.groups().stream().sorted(Comparator.comparing(Group::warehouseId)).toList();
  var bound=warehouse.bindInboundSubmission(order(r,groups.get(0),r.batchId(),r.batchVersion()));Long batchId=bound.batchId();
  if(batchId!=null){var batch=jdbc.queryForMap("SELECT * FROM metal_process_batch WHERE batch_id=? FOR UPDATE",batchId);if(bound.batchVersion()==null||!Objects.equals(number(batch.get("version")),bound.batchVersion().longValue()))throw new ServiceException("批次版本已变化，请刷新后重试",409);if(Boolean.TRUE.equals(r.settle())&&BatchCompletionRules.POLICY.equals(batch.get("completion_policy")))throw new ServiceException("请在全部产出保存后统一确认还料或无余料");}
  var key=new GeneratedKeyHolder();jdbc.update(c->{var ps=c.prepareStatement("INSERT INTO metal_inbound_submission(request_id,request_actor,request_hash,factory_id,type,order_date,work_order_id,item_id,process_id,batch_id) VALUES(?,?,?,?,?,?,?,?,?,?)",java.sql.Statement.RETURN_GENERATED_KEYS);Object[] args={r.requestId(),SecurityUtils.getUserId(),fingerprint,r.factoryId(),r.type(),r.orderDate(),r.workOrderId(),r.itemId(),r.processId(),batchId};for(int i=0;i<args.length;i++)ps.setObject(i+1,args[i]);return ps;},key);long id=key.getKey().longValue();
  for(var group:groups){Integer version=batchId==null?null:jdbc.queryForObject("SELECT version FROM metal_process_batch WHERE batch_id=?",Integer.class,batchId);try{warehouse.postInboundChild(order(r,group,batchId,version),id);}catch(ServiceException e){throw new InboundGroupException(r.groups().indexOf(group),"仓库"+group.warehouseId()+"："+e.getMessage());}}
  if(Boolean.TRUE.equals(r.settle())){permit("metal:process-batch:settle");if(batchId==null)throw new ServiceException("结算必须关联加工批次");warehouse.settle(batchId,new Action(jdbc.queryForObject("SELECT version FROM metal_process_batch WHERE batch_id=?",Integer.class,batchId),null,r.completedQuantity()));}
  return result(id,false);
 }
 @Transactional(readOnly=true,isolation=Isolation.READ_COMMITTED)
 public boolean hasRequest(String request){return !jdbc.queryForList("SELECT submission_id FROM metal_inbound_submission WHERE request_id=?",request).isEmpty();}
 public Map<String,Object> detail(long id){permit("metal:warehouse-order:query");return result(id);}
 private Map<String,Object> result(long id,boolean reused){var out=result(id);out.put("reused",reused);return out;}
 private Map<String,Object> result(long id){var roots=jdbc.queryForList("SELECT * FROM metal_inbound_submission WHERE submission_id=?",id);if(roots.isEmpty())throw new ServiceException("入库提交记录不存在");var root=roots.get(0);scope.check(number(root.get("factory_id")));var out=new LinkedHashMap<String,Object>();out.put("submissionId",Long.toString(id));out.put("orders",jdbc.queryForList("SELECT CAST(order_id AS CHAR) id,order_no orderNo,CAST(warehouse_id AS CHAR) warehouseId,warehouse_name warehouseName,status FROM metal_warehouse_order WHERE inbound_submission_id=? ORDER BY warehouse_id",id));out.put("batchId",root.get("batch_id")==null?null:root.get("batch_id").toString());if(root.get("batch_id")!=null){var batch=jdbc.queryForMap("SELECT completion_policy,status FROM metal_process_batch WHERE batch_id=?",root.get("batch_id"));out.put("completionPolicy",batch.get("completion_policy"));out.put("nextAction",BatchCompletionRules.POLICY.equals(batch.get("completion_policy"))&&"OPEN".equals(batch.get("status"))?"CONFIRM_RETURN":"NONE");}else out.put("nextAction","NONE");return out;}
}
