package com.ruoyi.metal.service;
import java.util.*;import org.springframework.jdbc.core.JdbcTemplate;
/** Invoked inside the existing factory-serialized posting transaction. */
public final class WorkOrderCompletionService {
 private WorkOrderCompletionService(){}
 public static void transition(JdbcTemplate jdbc,long id,String next,long actor){
  var work=jdbc.queryForMap("SELECT status,auto_semi_release_eligible,completion_generation,factory_id FROM metal_work_order WHERE work_order_id=? FOR UPDATE",id);
  boolean completed="COMPLETED".equals(next)&&!"COMPLETED".equals(work.get("status"));long generation=SemiFlowService.num(work.get("completion_generation"))+(completed?1:0);
  jdbc.update("UPDATE metal_work_order SET status=?,completion_generation=?,version=version+1,update_time=NOW() WHERE work_order_id=?",next,generation,id);
  if(completed&&(Boolean.TRUE.equals(work.get("auto_semi_release_eligible"))||"1".equals(String.valueOf(work.get("auto_semi_release_eligible")))))jdbc.update("INSERT INTO metal_semi_auto_release_event(work_order_id,factory_id,generation,actor_id) VALUES(?,?,?,?)",id,work.get("factory_id"),generation,actor);
 }
}
