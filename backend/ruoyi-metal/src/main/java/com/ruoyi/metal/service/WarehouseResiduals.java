package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ruoyi.common.exception.ServiceException;

/** Attributes returned raw stock without creating a second physical balance. */
final class WarehouseResiduals {
 private WarehouseResiduals(){}
 static long n(Object v){return v==null?0:Long.parseLong(v.toString());}
 static BigDecimal d(Object v){return v==null?BigDecimal.ZERO:new BigDecimal(v.toString());}
 static void apply(JdbcTemplate jdbc,Map<String,Object> stock,BigDecimal delta,long document,long line,Long reversal){
  if(!"RAW".equals(stock.get("category"))||delta.signum()==0)return;
  if(reversal!=null){
   if(delta.signum()>0){
    for(var use:jdbc.queryForList("SELECT u.*,r.initial_weight,r.remaining_weight FROM metal_work_order_residual_use u JOIN metal_work_order_residual r ON r.residual_id=u.residual_id WHERE u.line_id=? AND u.reversed=FALSE FOR UPDATE",line)){
     // Undo later customer decisions first, while allowing a closeout to undo its own withdrawal.
     if(!jdbc.queryForList("SELECT c.closeout_id FROM metal_customer_residual_closeout_line l JOIN metal_customer_residual_closeout c ON c.closeout_id=l.closeout_id JOIN metal_warehouse_flow f ON f.closeout_id=c.closeout_id JOIN metal_warehouse_order o ON o.warehouse_flow_id=f.flow_id WHERE l.residual_id=? AND c.status<>'VOID' AND f.status IN ('WAITING','COLLECTING','RETURNED','POSTED') AND o.order_id>? AND c.closeout_id<>COALESCE((SELECT own_flow.closeout_id FROM metal_warehouse_order own_order LEFT JOIN metal_warehouse_flow own_flow ON own_flow.flow_id=own_order.warehouse_flow_id WHERE own_order.order_id=?),0) LIMIT 1",use.get("residual_id"),document,document).isEmpty())throw new ServiceException("余料已有后续客户收尾，请先撤回收尾再冲销领料");
     jdbc.update("UPDATE metal_work_order_residual SET remaining_weight=remaining_weight+?,version=version+1 WHERE residual_id=?",use.get("weight"),use.get("residual_id"));
     jdbc.update("UPDATE metal_work_order_residual_use SET reversed=TRUE WHERE use_id=?",use.get("use_id"));
    }
   }else{
    for(var credit:jdbc.queryForList("SELECT * FROM metal_work_order_residual WHERE source_line_id=? FOR UPDATE",line)){
     if(d(credit.get("initial_weight")).compareTo(d(credit.get("remaining_weight")))!=0||!"OPEN".equals(credit.get("status")))throw new ServiceException("本次余料已再领用或办理客户收尾，不能冲销");
     if(!jdbc.queryForList("SELECT c.closeout_id FROM metal_customer_residual_closeout_line l JOIN metal_customer_residual_closeout c ON c.closeout_id=l.closeout_id WHERE l.residual_id=? AND c.status<>'VOID'",credit.get("residual_id")).isEmpty())throw new ServiceException("余料已有待确认收尾，请先处理");
     jdbc.update("UPDATE metal_work_order_residual SET remaining_weight=0,version=version+1 WHERE residual_id=?",credit.get("residual_id"));
    }
   }
   return;
  }
  var headers=jdbc.queryForList("SELECT o.work_order_id,o.type,b.completion_policy FROM metal_warehouse_order o LEFT JOIN metal_process_batch b ON b.batch_id=o.batch_id WHERE o.order_id=?",document);
  if(headers.isEmpty())return;var header=headers.get(0);
  if(delta.signum()>0){
   if(WarehouseFlowRules.POLICY.equals(header.get("completion_policy"))&&Set.of("RAW_RETURN","PROCESS_RAW_RETURN").contains(header.get("type")))
    jdbc.update("INSERT INTO metal_work_order_residual(work_order_id,inventory_id,source_line_id,material_ownership,customer_id,initial_weight,remaining_weight) VALUES(?,?,?,?,?,?,?)",header.get("work_order_id"),stock.get("inventory_id"),line,MaterialOwnership.of(stock),stock.get("customer_id"),delta,delta);
   return;
  }
  if(delta.signum()==0)return;
  var lots=jdbc.queryForList("SELECT r.* FROM metal_work_order_residual r JOIN metal_warehouse_order_line l ON l.line_id=r.source_line_id JOIN metal_warehouse_order o ON o.order_id=l.order_id WHERE r.inventory_id=? AND r.remaining_weight>0 AND r.status IN ('OPEN','STORED') AND o.status='POSTED' ORDER BY CASE WHEN r.work_order_id=? THEN 0 ELSE 1 END,r.residual_id FOR UPDATE",stock.get("inventory_id"),header.get("work_order_id"));
  BigDecimal needed=delta.negate(),attributed=lots.stream().map(r->d(r.get("remaining_weight"))).reduce(BigDecimal.ZERO,BigDecimal::add);
  BigDecimal unattributed=d(stock.get("net_weight")).subtract(attributed).max(BigDecimal.ZERO);
  // Same-order reissues consume its returned stock first; unrelated withdrawals use free stock first.
  for(var lot:lots){
   if(needed.signum()==0)break;
   if(!Objects.equals(lot.get("work_order_id"),header.get("work_order_id"))&&unattributed.signum()>0){BigDecimal take=needed.min(unattributed);needed=needed.subtract(take);unattributed=unattributed.subtract(take);}
   BigDecimal take=needed.min(d(lot.get("remaining_weight")));if(take.signum()==0)continue;
   if(!jdbc.queryForList("SELECT c.closeout_id FROM metal_customer_residual_closeout_line l JOIN metal_customer_residual_closeout c ON c.closeout_id=l.closeout_id WHERE l.residual_id=? AND c.status IN ('WAITING','PARTIAL')",lot.get("residual_id")).isEmpty()&&!Set.of("EXCHANGE_OUT","OWNERSHIP_OUT").contains(header.get("type")))throw new ServiceException("客户余料已有待确认收尾，不能再次领用或调整");
   jdbc.update("UPDATE metal_work_order_residual SET remaining_weight=remaining_weight-?,version=version+1 WHERE residual_id=?",take,lot.get("residual_id"));
   jdbc.update("INSERT INTO metal_work_order_residual_use(residual_id,line_id,weight) VALUES(?,?,?)",lot.get("residual_id"),line,take);needed=needed.subtract(take);
  }
 }
}
