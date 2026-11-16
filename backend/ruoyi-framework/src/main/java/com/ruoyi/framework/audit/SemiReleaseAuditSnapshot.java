package com.ruoyi.framework.audit;

import java.util.*;
import com.alibaba.fastjson2.JSON;
import org.springframework.jdbc.core.JdbcTemplate;

/** Explicit identities for release routes; their numeric path components are never warehouse IDs. */
final class SemiReleaseAuditSnapshot {
 private final JdbcTemplate jdbc;
 SemiReleaseAuditSnapshot(JdbcTemplate jdbc){this.jdbc=jdbc;}
 static boolean handles(String path){return path.matches("/metal/warehouse/inventory/[0-9]+/semi-release")||path.matches("/metal/warehouse/semi-release/[0-9]+/cancel");}
 private Map<String,Object> row(String table,String key,long id,AuditContext c){
  if(!Objects.equals(c.actorId,1L)&&c.actorFactory==null)return null;
  var args=new ArrayList<Object>();args.add(id);String sql="SELECT * FROM "+table+" WHERE "+key+"=?";
  if(!Objects.equals(c.actorId,1L)){sql+=" AND factory_id=?";args.add(c.actorFactory);}
  var rows=jdbc.queryForList(sql,args.toArray());return rows.size()==1?new LinkedHashMap<>(rows.get(0)):null;
 }
 private static Long id(Object value){if(value instanceof Long l)return l>0?l:null;if(value instanceof Integer i)return i>0?i.longValue():null;if(value instanceof String s&&s.matches("[0-9]{1,19}")){try{long parsed=Long.parseLong(s);return parsed>0?parsed:null;}catch(NumberFormatException overflow){return null;}}return null;}
 Map<String,Object> snapshot(AuditContext c,Object body,Object result){
  c.objectType="metal_semi_release";boolean create=c.path.endsWith("/semi-release");long pathId=Long.parseLong(c.path.split("/")[4]);
  Map<String,Object> release=create?null:row("metal_semi_release","release_id",pathId,c);
  if(!create&&release==null)return Map.of();
  var stock=row("metal_inventory","inventory_id",create?pathId:((Number)release.get("inventory_id")).longValue(),c);
  if(stock==null)return Map.of();
  long factory=((Number)stock.get("factory_id")).longValue();
  if(release!=null&&!Objects.equals(((Number)release.get("factory_id")).longValue(),factory))return Map.of();
  if(create){
   Long saved=id(result);
   if(saved!=null)release=row("metal_semi_release","release_id",saved,c);
   else if(result==null&&body!=null){
    try{var input=JSON.parseObject(JSON.toJSONString(body));String request=input.getString("requestId");
     if(request!=null){var rows=jdbc.queryForList("SELECT * FROM metal_semi_release WHERE request_id=? AND inventory_id=? AND factory_id=?",request,pathId,factory);if(rows.size()==1){release=new LinkedHashMap<>(rows.get(0));c.replayed=true;}}
    }catch(com.alibaba.fastjson2.JSONException ignored){}
   }
   if(release!=null&&(!Objects.equals(((Number)release.get("inventory_id")).longValue(),pathId)||!Objects.equals(((Number)release.get("factory_id")).longValue(),factory)))return Map.of();
  }
  c.factories.add(factory);c.objectIds.put("inventoryId",stock.get("inventory_id"));
  if(release==null)return Map.of("sourceInventory",AuditRedactor.redact(stock));
  c.businessCode=release.get("release_id").toString();c.objectIds.put("releaseId",release.get("release_id"));
  var value=new LinkedHashMap<>(release);value.put("sourceInventory",stock);
  return Map.of(c.businessCode,AuditRedactor.redact(value));
 }
}
