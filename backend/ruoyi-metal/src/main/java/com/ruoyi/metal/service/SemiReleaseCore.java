package com.ruoyi.metal.service;
import java.util.*;import java.math.BigDecimal;import com.alibaba.fastjson2.JSON;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.jdbc.support.*;
import com.ruoyi.common.exception.ServiceException;
/** Release writes only. Caller holds factory/work/inventory locks and has verified authority. */
final class SemiReleaseCore {
 private final JdbcTemplate jdbc;SemiReleaseCore(JdbcTemplate jdbc){this.jdbc=jdbc;}
 long releaseAuto(Map<String,Object> event,Map<String,Object> stock,long quantity,BigDecimal weight){
  var old=jdbc.queryForList("SELECT release_id FROM metal_semi_release WHERE auto_event_id=? AND inventory_id=?",event.get("event_id"),stock.get("inventory_id"));if(!old.isEmpty())return SemiFlowService.num(old.get(0).get("release_id"));
  String request=UUID.nameUUIDFromBytes(("AUTO:"+event.get("event_id")+":"+stock.get("inventory_id")).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();String reason="正常完成后自动释放剩余半成品";
  return write(stock,event.get("work_order_id"),quantity,weight,reason,event.get("actor_id"),request,SemiSourcePolicyService.hash(List.of(event.get("event_id"),stock.get("inventory_id"),quantity,weight)),event.get("event_id"));
 }
 long releaseManual(Map<String,Object> stock,Map<String,Object> source,long quantity,BigDecimal weight,String reason,long actor,String request,String fingerprint){return write(stock,source.get("work_order_id"),quantity,weight,reason,actor,request,fingerprint,null);}
 private long write(Map<String,Object> stock,Object work,long quantity,BigDecimal weight,String reason,Object actor,String request,String fp,Object event){
  var remaining=new SemiFlowService(jdbc,null).inventoryBalance(stock);SemiFlowRules.release(SemiFlowService.num(remaining.get("unreleasedQuantity")),SemiFlowService.dec(remaining.get("unreleasedWeight")),quantity,weight);
  KeyHolder h=new GeneratedKeyHolder();jdbc.update(c->{var p=c.prepareStatement("INSERT INTO metal_semi_release(inventory_id,factory_id,work_order_id,request_id,request_hash,actor_id,quantity,net_weight,reason,release_mode,auto_event_id) VALUES(?,?,?,?,?,?,?,?,?,?,?)",java.sql.Statement.RETURN_GENERATED_KEYS);Object[] args={stock.get("inventory_id"),stock.get("factory_id"),work,request,fp,actor,quantity,weight,reason,event==null?"MANUAL":"AUTO",event};for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);return p;},h);
  long id=h.getKey().longValue();jdbc.update("UPDATE metal_inventory SET version=version+1 WHERE inventory_id=?",stock.get("inventory_id"));jdbc.update("INSERT INTO metal_semi_flow_event(release_id,action,actor_id,reason) VALUES(?,?,?,?)",id,event==null?"RELEASE":"AUTO_RELEASE",actor,reason);return id;
 }
}
