package com.ruoyi.metal.service;
import java.math.BigDecimal;import java.util.*;
import org.springframework.stereotype.Service;import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.scheduling.annotation.Scheduled;import org.slf4j.*;
import com.alibaba.fastjson2.JSON;
/** Durable completion events: recovery works without a login session or permission impersonation. */
@Service
public class SemiAutoReleaseService {
 private static final Logger LOG=LoggerFactory.getLogger(SemiAutoReleaseService.class);
 private final JdbcTemplate jdbc;private final TransactionTemplate tx;
 public SemiAutoReleaseService(JdbcTemplate j,PlatformTransactionManager manager){jdbc=j;tx=new TransactionTemplate(manager);tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);}
 static int retryDelay(int failures){return failures>5?-1:new int[]{0,2,10,30,120,300}[failures];}
 @Scheduled(fixedDelay=2000,initialDelay=5000)
 public void recover(){try{for(var row:jdbc.queryForList("SELECT event_id FROM metal_semi_auto_release_event WHERE status IN ('PENDING','RETRY') AND next_attempt<=NOW() ORDER BY event_id LIMIT 20")){long id=SemiFlowService.num(row.get("event_id"));try{tx.executeWithoutResult(s->process(id));}catch(Exception failure){tx.executeWithoutResult(s->{var e=jdbc.queryForMap("SELECT failures,status FROM metal_semi_auto_release_event WHERE event_id=? FOR UPDATE",id);if(!Set.of("PENDING","RETRY").contains(String.valueOf(e.get("status"))))return;int count=(int)SemiFlowService.num(e.get("failures"))+1;int delay=retryDelay(count);jdbc.update("UPDATE metal_semi_auto_release_event SET failures=?,status=?,next_attempt=DATE_ADD(NOW(),INTERVAL ? SECOND),message=?,update_time=NOW() WHERE event_id=?",count,delay<0?"FAILED":"RETRY",Math.max(0,delay),"自动释放技术失败："+failure.getClass().getSimpleName(),id);});}}}catch(Exception failure){LOG.warn("SEMI_AUTO_RELEASE_RECOVERY_UNAVAILABLE {}",failure.getClass().getSimpleName());}}
 void process(long id){
  var hint=jdbc.queryForMap("SELECT factory_id,work_order_id FROM metal_semi_auto_release_event WHERE event_id=?",id);
  var factories=jdbc.queryForList("SELECT factory_id FROM metal_factory WHERE factory_id=? AND del_flag='0' FOR UPDATE",hint.get("factory_id"));
  var work=jdbc.queryForMap("SELECT * FROM metal_work_order WHERE work_order_id=? FOR UPDATE",hint.get("work_order_id"));
  var e=jdbc.queryForMap("SELECT * FROM metal_semi_auto_release_event WHERE event_id=? FOR UPDATE",id);if(!Set.of("PENDING","RETRY").contains(String.valueOf(e.get("status"))))return;
  if(factories.isEmpty()||!"0".equals(work.get("del_flag"))||!"COMPLETED".equals(work.get("status"))||SemiFlowService.num(work.get("completion_generation"))!=SemiFlowService.num(e.get("generation"))||SemiFlowService.num(work.get("factory_id"))!=SemiFlowService.num(e.get("factory_id"))){finish(id,"CANCELLED","工单完成状态、工厂或完成代次已失效");return;}
  boolean released=false,review=false;var semi=new SemiFlowService(jdbc,null);
  for(var stock:jdbc.queryForList("SELECT i.* FROM metal_inventory i JOIN metal_process_batch b ON b.batch_id=i.origin_batch_id WHERE b.work_order_id=? AND i.category='SEMI' AND i.del_flag='0' ORDER BY i.inventory_id FOR UPDATE",work.get("work_order_id"))){
   long inventory=SemiFlowService.num(stock.get("inventory_id"));String state="NO_BALANCE",message="无尚未释放余量";long available=0;BigDecimal weight=BigDecimal.ZERO;
   var existing=jdbc.queryForList("SELECT release_id,status FROM metal_semi_release WHERE auto_event_id=? AND inventory_id=?",id,inventory);if(!existing.isEmpty()){state="ALREADY_HANDLED";message="该完成事件已处理；人工撤销的记录不会重建";released|="ACTIVE".equals(existing.get(0).get("status"));}
   else{var b=semi.sourceBatch(stock);long qty=SemiFlowService.num(stock.get("quantity"));BigDecimal actual=SemiFlowService.dec(stock.get("net_weight"));var p=JSON.parseObject(SemiFlowService.text(b.get("product_snapshot")));
    if(qty==0&&actual.signum()==0){state="NO_BALANCE";message="无实物余量，无需释放";}
    else if(SemiFlowService.num(stock.get("factory_id"))!=SemiFlowService.num(e.get("factory_id"))||!"NORMAL".equals(stock.get("ownership_status"))||!"SETTLED".equals(b.get("status"))||p==null||p.getString("specification")==null||p.getString("specification").isBlank()||!p.getString("specification").trim().equals(SemiFlowService.text(stock.get("specification")).trim())||!SemiFlowRules.sameCustomer(SemiFlowService.text(stock.get("organization")),SemiFlowService.text(b.get("customer")))||SemiSourcePolicyService.hasSkipped(b)||SemiFlowService.num(stock.get("reference_id"))!=SemiFlowService.num(b.get("product_id"))||SemiFlowService.num(b.get("factory_id"))!=SemiFlowService.num(e.get("factory_id"))||!SemiSourcePolicyService.completeSavedRoute(jdbc,b)){state="REVIEW";message="来源、规格、客户、完整路线或批次状态不符合释放规则";review=true;}
    else if(qty<0||actual.signum()<0||(qty==0)!=(actual.signum()==0)){state="REVIEW";message="库存件数与净重不一致，请人工核对";review=true;}
    else{var balance=semi.inventoryBalance(stock);available=SemiFlowService.num(balance.get("unreleasedQuantity"));weight=SemiFlowService.dec(balance.get("unreleasedWeight"));if(available<0||weight.signum()<0||(available==0)!=(weight.signum()==0)){state="REVIEW";message="已有释放额度与实物库存不一致";review=true;}else if(available>0){WarehouseService.validateOwnership(stock);SemiFlowRules.release(available,weight,available,weight);new SemiReleaseCore(jdbc).releaseAuto(e,stock,available,weight);state="RELEASED";message="已自动释放";released=true;}}
   }
   jdbc.update("INSERT INTO metal_semi_auto_release_result(event_id,inventory_id,status,message,quantity,net_weight) VALUES(?,?,?,?,?,?) ON DUPLICATE KEY UPDATE status=VALUES(status),message=VALUES(message),quantity=VALUES(quantity),net_weight=VALUES(net_weight)",id,inventory,state,message,available,weight);
  }
  finish(id,review?(released?"PARTIAL":"REVIEW"):(released?"RELEASED":"NO_BALANCE"),review?"部分库存需要人工核对":released?"正常完成后剩余半成品已释放":"无需释放");
 }
 private void finish(long id,String status,String message){jdbc.update("UPDATE metal_semi_auto_release_event SET status=?,message=?,update_time=NOW() WHERE event_id=?",status,message,id);}
}
