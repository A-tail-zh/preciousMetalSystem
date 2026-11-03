package com.ruoyi.metal.service;

import java.util.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.metal.catalog.AcidWashRequests.*;
import com.ruoyi.metal.catalog.WarehouseRequests.*;
import com.ruoyi.metal.security.FactoryScope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
public class AcidWashService {
 private final JdbcTemplate jdbc;private final FactoryScope scope;private final WarehouseService warehouse;
 public AcidWashService(JdbcTemplate jdbc,FactoryScope scope,WarehouseService warehouse){this.jdbc=jdbc;this.scope=scope;this.warehouse=warehouse;}
 private static long n(Object x){return x==null?0:Long.parseLong(x.toString());}
 private static BigDecimal d(Object x){return x==null?BigDecimal.ZERO:new BigDecimal(x.toString());}
 private void permit(String permission){if(!scope.isAdmin()&&!SecurityUtils.hasPermi(permission))throw new ServiceException("没有此操作权限",403);}
 private void read(){if(!scope.isAdmin()&&!SecurityUtils.hasPermi("metal:warehouse-order:query")&&!SecurityUtils.hasPermi("metal:warehouse-order:add"))throw new ServiceException("没有酸洗查询权限",403);}
 private static void uuid(String id){try{if(!UUID.fromString(id).toString().equals(id))throw new IllegalArgumentException();}catch(Exception e){throw new ServiceException("酸洗请求编号须为UUID");}}
 private static String hash(Object value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JSON.toJSONString(value).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 private Map<String,Object> one(String sql,Object...args){var rows=jdbc.queryForList(sql,args);if(rows.size()!=1)throw new ServiceException("酸洗批次或来源记录不存在");return rows.get(0);}
 private Map<String,Object> batch(long id,boolean lock){var row=one("SELECT * FROM metal_acid_wash_batch WHERE acid_wash_id=?",id);scope.check(n(row.get("factory_id")));if(lock){scope.lockFactories(List.of(n(row.get("factory_id"))));row=one("SELECT * FROM metal_acid_wash_batch WHERE acid_wash_id=? FOR UPDATE",id);}return row;}
 private static void version(Map<String,Object> row,Integer version){if(version==null||n(row.get("version"))!=version)throw new ServiceException("酸洗批次版本已变化，请刷新后重试",409);}
 private static List<Group> groups(List<Group> groups,boolean optional){
  if(groups==null||(!optional&&groups.isEmpty()))throw new ServiceException("请添加酸洗物料明细");
  var warehouses=new HashSet<Long>();int count=0;
  for(var g:groups){if(g==null||g.warehouseId()==null||!warehouses.add(g.warehouseId())||g.lines()==null||g.lines().isEmpty())throw new ServiceException("仓库不能重复，每仓必须有物料明细");count+=g.lines().size();}
  if(count>1000)throw new ServiceException("酸洗一次提交最多1000条明细");
  return groups.stream().sorted(Comparator.comparing(Group::warehouseId)).map(g->new Group(g.warehouseId(),WarehousePrecision.lines(g.lines()))).toList();
 }
 private static BigDecimal weight(List<Group> groups){return groups.stream().flatMap(g->g.lines().stream()).map(Line::netWeight).reduce(BigDecimal.ZERO,BigDecimal::add);}
 private Order order(String request,long factory,Group g,String type,LocalDate date,List<Long> people,Long keeper,String reason){return new Order(request,factory,g.warehouseId(),type,date,null,people,keeper,null,null,null,null,null,null,false,null,reason,g.lines(),null,null);}
 private void post(String request,long id,long factory,List<Group> groups,String type,LocalDate date,List<Long> people,Long keeper,String reason){int index=0;for(var g:groups){String child=UUID.nameUUIDFromBytes(("acid:"+request+":"+type+":"+index++).getBytes(StandardCharsets.UTF_8)).toString();warehouse.postAcidChild(order(child,factory,g,type,date,people,keeper,reason),id);}}

 @Transactional(readOnly=true)
 public Map<String,Object> options(long factory){
  permit("metal:warehouse-order:add");scope.check(factory);
  var stocks=jdbc.queryForList("SELECT CAST(i.inventory_id AS CHAR) id,CAST(i.warehouse_id AS CHAR) warehouseId,h.name warehouseName,i.code,i.name,i.specification,CAST(i.net_weight AS CHAR) netWeight,CAST(COALESCE(held.weight,0) AS CHAR) reservedWeight,CAST(i.net_weight-COALESCE(held.weight,0) AS CHAR) availableWeight,CAST(i.customer_id AS CHAR) customerId,CASE WHEN i.material_ownership='FACTORY' THEN '工厂自有' ELSE c.company_name END customerCompanyName FROM metal_inventory i JOIN metal_material m ON m.material_id=i.reference_id JOIN metal_warehouse h ON h.warehouse_id=i.warehouse_id LEFT JOIN metal_customer c ON c.customer_id=i.customer_id LEFT JOIN (SELECT inventory_id,SUM(weight) weight FROM metal_stock_reservation WHERE status='ACTIVE' GROUP BY inventory_id) held ON held.inventory_id=i.inventory_id WHERE i.factory_id=? AND i.category='RAW' AND i.raw_material_kind='UNPROCESSED' AND i.del_flag='0' AND i.ownership_status='NORMAL' AND i.net_weight-COALESCE(held.weight,0)>0 AND m.del_flag='0' AND m.metal_type_id=1 AND h.del_flag='0' AND h.status='0' ORDER BY i.warehouse_id,i.inventory_id",factory);
  var processes=jdbc.queryForList("SELECT CAST(p.process_id AS CHAR) id,p.process_name name,p.version FROM metal_process p JOIN metal_process_type t ON t.type_id=p.process_type WHERE t.type_name='酸洗' AND p.del_flag='0' ORDER BY p.process_id");
  var materials=jdbc.queryForList("SELECT CAST(m.material_id AS CHAR) id,m.material_code code,CONCAT(t.name,m.material_type) name,m.specification FROM metal_material m JOIN metal_type t ON t.metal_type_id=m.metal_type_id WHERE m.del_flag='0' AND m.metal_type_id=1 AND m.material_type<>'粉料' ORDER BY m.material_id");
  return Map.of("stocks",stocks,"processes",processes,"materials",materials);
 }
 @Transactional(readOnly=true)
 public Map<String,Object> list(long factory,int page,int size){read();scope.check(factory);if(page<1||size<1||size>100)throw new ServiceException("分页参数无效");return Map.of("rows",jdbc.queryForList("SELECT CAST(acid_wash_id AS CHAR) id,batch_no batchNo,JSON_UNQUOTE(JSON_EXTRACT(process_snapshot,'$.process_name')) processName,status,version,CAST(issued_weight AS CHAR) issuedWeight,CAST(output_weight AS CHAR) outputWeight,CAST(loss_weight AS CHAR) lossWeight,create_time createTime FROM metal_acid_wash_batch WHERE factory_id=? ORDER BY acid_wash_id DESC LIMIT ? OFFSET ?",factory,size,(long)(page-1)*size),"total",jdbc.queryForObject("SELECT COUNT(*) FROM metal_acid_wash_batch WHERE factory_id=?",Long.class,factory));}
 @Transactional(readOnly=true)
 public Map<String,Object> detail(long id){
  read();var b=batch(id,false);var out=new LinkedHashMap<String,Object>();out.put("id",Long.toString(id));out.put("factoryId",b.get("factory_id").toString());out.put("customerId",b.get("customer_id")==null?null:b.get("customer_id").toString());out.put("materialOwnership",b.get("material_ownership"));out.put("process",JSON.parseObject(b.get("process_snapshot").toString()));
  for(String key:List.of("status","version"))out.put(key,b.get(key));out.put("batchNo",b.get("batch_no"));
  out.put("issuedWeight",d(b.get("issued_weight")).toPlainString());out.put("outputWeight",d(b.get("output_weight")).toPlainString());out.put("returnedWeight",d(b.get("returned_weight")).toPlainString());out.put("lossWeight",b.get("loss_weight")==null?null:d(b.get("loss_weight")).toPlainString());
  out.put("orders",jdbc.queryForList("SELECT CAST(order_id AS CHAR) id,order_no orderNo,warehouse_name warehouseName,type,status FROM metal_warehouse_order WHERE acid_wash_id=? ORDER BY order_id",id));
  out.put("sources",jdbc.queryForList("SELECT CAST(l.line_id AS CHAR) id,CAST(l.inventory_id AS CHAR) inventoryId,CAST(o.warehouse_id AS CHAR) warehouseId,o.warehouse_name warehouseName,l.code,l.name,l.specification,CAST(l.net_weight AS CHAR) netWeight FROM metal_warehouse_order_line l JOIN metal_warehouse_order o ON o.order_id=l.order_id WHERE o.acid_wash_id=? AND o.type='ACID_OUT' AND o.status='POSTED' ORDER BY l.line_id",id));return out;
 }

 public String start(Start input){
  permit("metal:warehouse-order:add");uuid(input.requestId());scope.lockFactories(List.of(input.factoryId()));String fingerprint=hash(input);
  var old=jdbc.queryForList("SELECT * FROM metal_acid_wash_batch WHERE request_id=? FOR UPDATE",input.requestId());
  if(!old.isEmpty()){var row=old.get(0);if(n(row.get("request_actor"))!=SecurityUtils.getUserId()||!fingerprint.equals(row.get("request_hash")))throw new ServiceException("相同酸洗请求的内容或操作人不一致",409);return row.get("acid_wash_id").toString();}
  throw new ServiceException("新酸洗任务请通过普通工单安排酸洗工序；历史批次仍可续办或冲销",410);
 }

 public String finish(long id,Finish input){
  permit("metal:warehouse-order:add");permit("metal:process-batch:settle");uuid(input.requestId());var b=batch(id,true);String fingerprint=hash(List.of(id,input));
  var prior=jdbc.queryForList("SELECT * FROM metal_acid_wash_batch WHERE finish_request_id=?",input.requestId());if(!prior.isEmpty()){var old=prior.get(0);if(n(old.get("acid_wash_id"))!=id||n(old.get("finish_actor"))!=SecurityUtils.getUserId()||!fingerprint.equals(old.get("finish_hash")))throw new ServiceException("相同酸洗产出请求内容或操作人不一致",409);return Long.toString(id);}
  if(!"OPEN".equals(b.get("status")))throw new ServiceException("酸洗批次已结算或已冲销");version(b,input.version());if(!Boolean.TRUE.equals(input.physicalChecked()))throw new ServiceException("请核对全部酸洗实物与余料");
  var outputs=groups(input.outputs(),false);var returns=groups(input.returns(),true);
  if(outputs.stream().mapToInt(g->g.lines().size()).sum()+returns.stream().mapToInt(g->g.lines().size()).sum()>1000)throw new ServiceException("酸洗产出与还料合计最多1000条明细");
  RawMaterialKinds.mass(d(b.get("issued_weight")),weight(outputs),weight(returns));long factory=n(b.get("factory_id"));
  var all=new ArrayList<Line>();outputs.forEach(g->all.addAll(g.lines()));returns.forEach(g->all.addAll(g.lines()));warehouse.lockPostingSources(order(input.requestId(),factory,new Group(outputs.get(0).warehouseId(),all),"ACID_IN",input.orderDate(),input.operatorIds(),input.keeperId(),input.reason()));
  for(var g:outputs)for(var line:g.lines()){
   if(!"RAW".equals(line.category())||line.referenceId()==null||line.inventoryId()!=null||line.sourceLineId()!=null||line.batchNo()!=null&&!line.batchNo().isBlank())throw new ServiceException("酸洗产出请选择目标物料编码，来源和批次号由系统生成");
   var material=one("SELECT material_type,metal_type_id FROM metal_material WHERE material_id=? AND del_flag='0' FOR UPDATE",line.referenceId());MaterialTypes.requireProduction(n(material.get("metal_type_id")));if("粉料".equals(material.get("material_type")))throw new ServiceException("金属料产出不能选择粉料物料编码");
  }
  for(var g:returns)for(var line:g.lines()){
   var source=one("SELECT l.line_id,o.acid_wash_id,o.warehouse_id FROM metal_warehouse_order_line l JOIN metal_warehouse_order o ON o.order_id=l.order_id WHERE l.line_id=? AND o.type='ACID_OUT' AND o.status='POSTED'",line.sourceLineId());
   if(n(source.get("acid_wash_id"))!=id||n(source.get("warehouse_id"))!=g.warehouseId())throw new ServiceException("余料必须退回本酸洗批次的原出库仓库",403);
  }
  post(input.requestId(),id,factory,outputs,"ACID_IN",input.orderDate(),input.operatorIds(),input.keeperId(),input.reason());post(input.requestId(),id,factory,returns,"ACID_RETURN",input.orderDate(),input.operatorIds(),input.keeperId(),input.reason());
  jdbc.update("UPDATE metal_acid_wash_batch SET finish_request_id=?,finish_hash=?,finish_actor=?,status='SETTLED',output_weight=?,returned_weight=?,loss_weight=?,settled_time=NOW(),version=version+1,reason=? WHERE acid_wash_id=?",input.requestId(),fingerprint,SecurityUtils.getUserId(),weight(outputs),weight(returns),d(b.get("issued_weight")).subtract(weight(outputs)).subtract(weight(returns)),input.reason(),id);return Long.toString(id);
 }
 public void reverse(long id,Action input){permit("metal:warehouse-order:reverse");var b=batch(id,true);version(b,input.version());if("VOID".equals(b.get("status")))throw new ServiceException("酸洗批次已冲销");if(input.reason()==null||input.reason().isBlank())throw new ServiceException("请填写酸洗冲销原因");warehouse.reverseAcidDocuments(id,input.reason());jdbc.update("UPDATE metal_acid_wash_batch SET status='VOID',version=version+1 WHERE acid_wash_id=?",id);}
}
