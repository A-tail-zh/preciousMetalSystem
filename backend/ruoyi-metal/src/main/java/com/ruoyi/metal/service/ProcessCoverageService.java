package com.ruoyi.metal.service;
import org.springframework.jdbc.core.JdbcTemplate;
/** Quantity guard shared by posting, returns, import and completion; factory transaction owns serialization. */
final class ProcessCoverageService {
 static void check(JdbcTemplate jdbc,long item,SemiFlowService semi){long plan=jdbc.queryForObject("SELECT quantity FROM metal_work_order_item WHERE item_id=?",Long.class,item);for(var s:jdbc.queryForList("SELECT sequence_no FROM metal_work_order_step WHERE item_id=?",item)){long seq=SemiFlowService.num(s.get("sequence_no"));long local=jdbc.queryForObject("SELECT COALESCE(SUM(CASE WHEN status='SETTLED' THEN completed_quantity ELSE planned_quantity END),0) FROM metal_process_batch WHERE item_id=? AND sequence_no=? AND status IN ('OPEN','SETTLED')",Long.class,item,seq);RouteExecutionRules.capacity(plan,local,0,semi.credit(item,seq),0);}}
}
