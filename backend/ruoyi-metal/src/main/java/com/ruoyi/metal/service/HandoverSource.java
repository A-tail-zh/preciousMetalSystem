package com.ruoyi.metal.service;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

final class HandoverSource {
    private HandoverSource() {}
    static boolean outboundReady(JdbcTemplate jdbc,Map<String,Object> stock,Map<String,Object> batch,String type) {
        return ("PRODUCTION_OUT".equals(type)||"SETTLED".equals(batch.get("status")))&&ready(jdbc,stock,batch);
    }
    static boolean canEnter(JdbcTemplate jdbc,Map<String,Object> stock,Map<String,Object> batch,Map<String,Object> target) {
        return ready(jdbc,stock,batch)&&("SETTLED".equals(batch.get("status"))
            ||PartialHandoverLedger.n(batch.get("work_order_id"))==PartialHandoverLedger.n(target.get("work_order_id"))
              &&PartialHandoverLedger.n(batch.get("item_id"))==PartialHandoverLedger.n(target.get("item_id")));
    }
    static void enrich(JdbcTemplate jdbc,List<Map<String,Object>> stocks) {
        var ids=new TreeSet<Long>();
        for(var stock:stocks)if(stock.get("origin_handover_flow_id")!=null&&stock.get("inventory_id")!=null&&!stock.containsKey("source_ready"))ids.add(PartialHandoverLedger.n(stock.get("inventory_id")));
        if(ids.isEmpty())return;
        var ready=new HashSet<Long>();
        for(var row:jdbc.queryForList("SELECT i.inventory_id FROM metal_inventory i JOIN metal_process_batch b ON b.batch_id=i.origin_batch_id AND b.status<>'VOID' AND b.handover_policy_version=2 JOIN metal_warehouse_flow f ON f.flow_id=i.origin_handover_flow_id AND f.batch_id=b.batch_id AND f.kind='HANDOVER' AND f.handover_policy_version=2 AND f.status='POSTED' WHERE i.inventory_id IN ("+String.join(",",Collections.nCopies(ids.size(),"?"))+") AND EXISTS(SELECT 1 FROM metal_warehouse_order o JOIN metal_warehouse_order_line l ON l.order_id=o.order_id WHERE o.warehouse_flow_id=f.flow_id AND o.status='POSTED' AND o.type IN ('SEMI_IN','FINISHED_IN','RAW_PROCESS_IN') AND l.inventory_id=i.inventory_id)",ids.toArray()))ready.add(PartialHandoverLedger.n(row.get("inventory_id")));
        for(var stock:stocks)if(ids.contains(PartialHandoverLedger.n(stock.get("inventory_id"))))stock.put("source_ready",ready.contains(PartialHandoverLedger.n(stock.get("inventory_id"))));
    }
    static boolean ready(JdbcTemplate jdbc,Map<String,Object> stock,Map<String,Object> batch) {
        if(!PartialHandoverLedger.partial(batch))return stock.get("origin_handover_flow_id")==null&&"SETTLED".equals(batch.get("status"));
        if("VOID".equals(batch.get("status")) || stock.get("origin_handover_flow_id")==null)return false;
        if(stock.containsKey("source_ready"))return Boolean.TRUE.equals(stock.get("source_ready"));
        return jdbc.queryForObject("SELECT COUNT(*) FROM metal_warehouse_flow f WHERE f.flow_id=? AND f.batch_id=? AND f.kind='HANDOVER' AND f.handover_policy_version=2 AND f.status='POSTED' AND EXISTS(SELECT 1 FROM metal_warehouse_order o JOIN metal_warehouse_order_line l ON l.order_id=o.order_id WHERE o.warehouse_flow_id=f.flow_id AND o.status='POSTED' AND o.type IN ('SEMI_IN','FINISHED_IN','RAW_PROCESS_IN') AND l.inventory_id=?)",Long.class,stock.get("origin_handover_flow_id"),stock.get("origin_batch_id"),stock.get("inventory_id"))>0;
    }
}
