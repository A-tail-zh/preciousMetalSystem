package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ruoyi.common.exception.ServiceException;

/** Raw returns share the batch mass allowance; this is an upper bound, not material consumption allocation. */
final class RawReturnAllowance {
 private RawReturnAllowance() {}
 static BigDecimal decimal(Object value) { return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString()); }
 static BigDecimal remaining(Object issued,Object output,Object returned) { return decimal(issued).subtract(decimal(output)).subtract(decimal(returned)).setScale(6); }
 static BigDecimal limit(BigDecimal source,BigDecimal batch,Object status) { return "OPEN".equals(status) ? source.min(batch).max(BigDecimal.ZERO).setScale(6) : BigDecimal.ZERO.setScale(6); }
 static void require(BigDecimal source,BigDecimal batch,BigDecimal requested,Object status) {
  if (!"OPEN".equals(status)) throw new ServiceException("批次已结算、已冲销或不存在，不能新增原料还库");
  if (batch.signum()<0 || source.signum()<0) throw new ServiceException("历史重量异常，剩余可退净重为0，请人工核对");
  BigDecimal available=limit(source,batch,status);
  if (requested.signum()<=0 || requested.compareTo(available)>0) throw new ServiceException("剩余可退净重最多"+available.toPlainString()+"g（已扣除有效产出和已还原料）");
 }
 record Snapshot(BigDecimal remaining,String status) {}
 static Snapshot load(JdbcTemplate jdbc,Long batchId) {
  var rows=jdbc.queryForList(totalsSql(false),batchId);
  if(rows.isEmpty()) return new Snapshot(BigDecimal.ZERO.setScale(6),"");
  Map<String,Object> row=rows.get(0);
  return new Snapshot(remaining(row.get("issued_weight"),row.get("output_weight"),row.get("returned_weight")),String.valueOf(row.get("batch_status")));
 }
 static String totalsSql(boolean factory) {
  return "SELECT b.batch_id,b.status batch_status,COALESCE(SUM(CASE WHEN o.type='PRODUCTION_OUT' THEN l.net_weight ELSE 0 END),0) issued_weight,COALESCE(SUM(CASE WHEN o.type IN ('SEMI_IN','FINISHED_IN') THEN l.net_weight ELSE 0 END),0) output_weight,COALESCE(SUM(CASE WHEN o.type='RAW_RETURN' THEN l.net_weight ELSE 0 END),0) returned_weight FROM metal_process_batch b LEFT JOIN metal_warehouse_order o ON o.batch_id=b.batch_id AND o.status='POSTED' LEFT JOIN metal_warehouse_order_line l ON l.order_id=o.order_id WHERE "+(factory?"b.factory_id=?":"b.batch_id=?")+" GROUP BY b.batch_id,b.status";
 }
}
