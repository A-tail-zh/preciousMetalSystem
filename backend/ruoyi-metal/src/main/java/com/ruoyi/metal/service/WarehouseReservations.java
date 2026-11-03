package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ruoyi.common.exception.ServiceException;

/** Reservation reads share the caller's factory lock; no reservation is an inventory ledger. */
final class WarehouseReservations {
    private WarehouseReservations() {}
    static BigDecimal d(Object value) { return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString()); }
    static long n(Object value) { return value == null ? 0 : Long.parseLong(value.toString()); }
    static Map<String,Object> stock(JdbcTemplate jdbc,long id) {
        var rows=jdbc.queryForList("SELECT COALESCE(SUM(weight),0) weight,COALESCE(SUM(quantity),0) quantity FROM metal_stock_reservation WHERE inventory_id=? AND status='ACTIVE'",id);
        return rows.isEmpty()?Map.of("weight",BigDecimal.ZERO,"quantity",0L):rows.get(0);
    }
    static void balance(JdbcTemplate jdbc,Map<String,Object> stock,BigDecimal delta,long deltaQuantity) {
        if (delta.signum() >= 0 && deltaQuantity >= 0) return;
        var held=stock(jdbc,n(stock.get("inventory_id")));
        WarehouseFlowRules.available(d(stock.get("net_weight")).add(delta),n(stock.get("quantity"))+deltaQuantity,
                d(held.get("weight")),n(held.get("quantity")),BigDecimal.ZERO,0,
                "RAW".equals(stock.get("category"))||SemiMeasurementRules.weight(stock));
    }
    static void stockReference(JdbcTemplate jdbc,long stock) {
        if(!jdbc.queryForList("SELECT reservation_id FROM metal_stock_reservation WHERE inventory_id=? AND status='ACTIVE' LIMIT 1",stock).isEmpty())
            throw new ServiceException("库存存在待出库或待收尾占用，请先取消或完成确认");
    }
    static void releaseReference(JdbcTemplate jdbc,long release) {
        if(!jdbc.queryForList("SELECT reservation_id FROM metal_stock_reservation WHERE release_id=? AND status='ACTIVE' LIMIT 1",release).isEmpty())
            throw new ServiceException("释放额度已有待出库占用，不能撤销");
    }
    static void work(JdbcTemplate jdbc,long work) {
        if(!jdbc.queryForList("SELECT flow_id FROM metal_warehouse_flow WHERE work_order_id=? AND status IN ('WAITING','COLLECTING','RETURNED') LIMIT 1",work).isEmpty())
            throw new ServiceException("工单存在待出库、待验收或待收尾事项，请先处理");
    }
    static void batch(JdbcTemplate jdbc,long batch,long exceptFlow) {
        if(!jdbc.queryForList("SELECT flow_id FROM metal_warehouse_flow WHERE batch_id=? AND flow_id<>? AND status IN ('WAITING','COLLECTING','RETURNED') LIMIT 1",batch,exceptFlow).isEmpty())
            throw new ServiceException("批次存在未完成的领料或交接，不能结算或冲销");
    }
    static long external(JdbcTemplate jdbc,long item,long process) {
        return n(jdbc.queryForObject("SELECT COALESCE(SUM(quantity),0) FROM metal_stock_reservation WHERE item_id=? AND process_id=? AND release_id IS NOT NULL AND status='ACTIVE'",Long.class,item,process));
    }
    static Map<String,Object> release(JdbcTemplate jdbc,long id) {
        var rows=jdbc.queryForList("SELECT COALESCE(SUM(weight),0) weight,COALESCE(SUM(quantity),0) quantity FROM metal_stock_reservation WHERE release_id=? AND status='ACTIVE'",id);
        return rows.isEmpty()?Map.of("weight",BigDecimal.ZERO,"quantity",0L):rows.get(0);
    }
    static long pendingCredit(JdbcTemplate jdbc,long item,long sequence) {
        var rows=jdbc.queryForList("SELECT r.quantity,r.release_id,r.route_profile,s.sequence_no target_sequence FROM metal_stock_reservation r JOIN metal_work_order_step s ON s.item_id=r.item_id AND s.process_id=r.process_id WHERE r.item_id=? AND r.status='ACTIVE' AND r.category='SEMI'",item);
        if(rows.isEmpty())return 0;
        var modes=jdbc.queryForList("SELECT measurement_mode FROM metal_work_order_step WHERE item_id=? ORDER BY sequence_no",item).stream().map(r->String.valueOf(r.get("measurement_mode"))).toList();
        long result=0;
        for(var row:rows){
            if(row.get("release_id")!=null){if(SemiMeasurementRules.pieceCreditApplies(modes,sequence,n(row.get("target_sequence"))))result+=n(row.get("quantity"));}
            else if(row.get("route_profile")!=null){var profile=com.alibaba.fastjson2.JSON.parseObject(row.get("route_profile").toString());var skipped=profile.getList("newSkipped",Long.class);if(skipped!=null&&skipped.contains(sequence))result+=n(row.get("quantity"));}
        }
        return result;
    }
}
