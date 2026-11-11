package com.ruoyi.metal.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** One-time conversion; callers must run migrate in a transaction with writes paused. */
public final class ProcessTypeMigration {
 private ProcessTypeMigration() {}
 public static List<Map<String,Object>> inspect(JdbcTemplate jdbc){
  try(var input=ProcessTypeMigration.class.getResourceAsStream("/process-type-merge-preflight.sql")){
   if(input==null)throw new IllegalStateException("缺少工序类型迁移预检SQL");
   return jdbc.queryForList(new String(input.readAllBytes(),StandardCharsets.UTF_8));
  }catch(IOException e){throw new IllegalStateException("无法读取工序类型迁移预检SQL",e);}
 }
 public static void migrate(JdbcTemplate jdbc){
  var blocked=inspect(jdbc).stream().filter(row->row.get("issue")!=null).toList();
  if(!blocked.isEmpty())throw new IllegalStateException("工序类型迁移已阻断，共"+blocked.size()+"条："+blocked.stream().limit(20).map(r->"工厂"+r.get("factoryId")+"/工序"+r.get("processId")+"："+r.get("issue")).toList()+"。请先运行process-type-merge-preflight.sql核对。");
  for(var entry:Map.of("ACID_WASH","酸洗","SMELTING","融定").entrySet()){
   String code=entry.getKey(),name=entry.getValue();
   jdbc.update("UPDATE metal_process_type t JOIN metal_factory f ON f.factory_id=t.factory_id AND f.del_flag='0' SET t.builtin_code=?,t.version=t.version+1,t.update_by='process-type-migration',t.update_time=NOW() WHERE t.type_name=? AND t.builtin_code IS NULL",code,name);
   jdbc.update("INSERT INTO metal_process_type(factory_id,type_name,builtin_code,create_by,update_by) SELECT f.factory_id,?,?, 'process-type-migration','process-type-migration' FROM metal_factory f WHERE f.del_flag='0' AND NOT EXISTS(SELECT 1 FROM metal_process_type t WHERE t.factory_id=f.factory_id AND t.builtin_code=?)",name,code,code);
   jdbc.update("UPDATE metal_process p JOIN metal_factory f ON f.factory_id=p.factory_id AND f.del_flag='0' JOIN metal_process_type t ON t.factory_id=p.factory_id AND t.builtin_code=? SET p.process_type=t.type_id,p.version=p.version+1,p.update_by='process-type-migration',p.update_time=NOW() WHERE p.semantic_code=? AND p.process_type<>t.type_id",code,code);
  }
  int inconsistent=jdbc.queryForObject("SELECT COUNT(*) FROM metal_process p JOIN metal_factory f ON f.factory_id=p.factory_id AND f.del_flag='0' JOIN metal_process_type t ON t.type_id=p.process_type WHERE p.semantic_code<>COALESCE(t.builtin_code,'NORMAL') OR p.factory_id<>t.factory_id",Integer.class);
  if(inconsistent!=0)throw new IllegalStateException("迁移后工序类型与处理规则不一致，已回滚");
 }
}
