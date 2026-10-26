package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.util.*;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.metal.catalog.WarehouseRequests.Line;
import com.ruoyi.metal.security.FactoryScope;
import org.springframework.jdbc.core.JdbcTemplate;
import com.alibaba.fastjson2.JSON;

/** Process residue becomes RAW stock; it is never a return of unprocessed SEMI pieces. */
public final class ProcessRawReturnService {
 public static final String TYPE="PROCESS_RAW_RETURN";
 private final JdbcTemplate jdbc;private final FactoryScope scope;
 public ProcessRawReturnService(JdbcTemplate jdbc,FactoryScope scope){this.jdbc=jdbc;this.scope=scope;}
 static BigDecimal d(Object v){return v==null?BigDecimal.ZERO:new BigDecimal(v.toString());}
 static long n(Object v){return v==null?0:Long.parseLong(v.toString());}
 public static void require(BigDecimal available,BigDecimal requested){
  if(available.signum()<0)throw new ServiceException("批次重量异常，请人工核对");
  if(requested==null||requested.signum()<=0||requested.scale()>6||requested.precision()-requested.scale()>14)throw new ServiceException("加工余料净重须大于0，最多6位小数和14位整数");
  if(requested.compareTo(available)>0)throw new ServiceException("加工余料剩余可还净重最多"+available.setScale(6).toPlainString()+"g");
 }
 public static void validateLine(Line line,String customer){
  require(line.netWeight(),line.netWeight());
  if(!"RAW".equals(line.category())||line.referenceId()==null||line.referenceId()<=0||line.quantity()!=null&&line.quantity()!=0)throw new ServiceException("请选择回收原材料，数量须为0");
  if(line.inventoryId()!=null||line.sourceLineId()!=null||line.releaseId()!=null||line.skipPermitId()!=null||line.skipPermitVersion()!=null||line.batchNo()!=null&&!line.batchNo().isBlank())throw new ServiceException("加工余料来源、库存和批号由系统生成，不能指定原出库行或释放记录");
  if(customer==null||customer.isBlank())throw new ServiceException("工单缺少客户归属，请先核对");
  if(line.organization()!=null&&!line.organization().isBlank()&&!customer.trim().equals(line.organization().trim()))throw new ServiceException("加工余料客户必须与生产工单一致");
  if((line.grossWeight()==null)!=(line.impurity()==null))throw new ServiceException("加工余料毛重、杂质须成对填写");
  if(line.grossWeight()!=null&&(line.grossWeight().signum()<0||line.impurity().signum()<0||line.grossWeight().scale()>6||line.impurity().scale()>6||line.grossWeight().subtract(line.impurity()).compareTo(line.netWeight())!=0))throw new ServiceException("加工余料净重须等于毛重减杂质，最多6位小数");
 }
 public Map<String,Object> context(Long factory,Long work,Long item,Long process,Long batch){
  if(factory==null||work==null||item==null||process==null)throw new ServiceException("请先选择工厂、生产工单、产品明细和工序");
  scope.check(factory);
  var rows=jdbc.queryForList("SELECT b.*,w.customer,w.status work_status FROM metal_process_batch b JOIN metal_work_order w ON w.work_order_id=b.work_order_id AND w.del_flag='0' WHERE b.factory_id=? AND b.work_order_id=? AND b.item_id=? AND b.process_id=? AND b.status='OPEN'"+(batch==null?"":" AND b.batch_id=?"),batch==null?new Object[]{factory,work,item,process}:new Object[]{factory,work,item,process,batch});
  if(rows.size()!=1)throw new ServiceException(rows.isEmpty()?"当前工序没有未结算批次":"存在多个未结算批次，请先人工核对");
  var b=rows.get(0);if(!Set.of("IN_PROGRESS","WAITING_MATERIAL").contains(b.get("work_status")))throw new ServiceException("工单已关闭或不可登记余料");
  String customer=Objects.toString(b.get("customer"),"").trim();if(customer.isEmpty())throw new ServiceException("工单缺少客户归属，请先核对");
  var totals=jdbc.queryForMap("SELECT COALESCE(SUM(CASE WHEN o.type='PRODUCTION_OUT' THEN l.net_weight ELSE 0 END),0) issued,COALESCE(SUM(CASE WHEN o.type='PRODUCTION_OUT' AND l.category='SEMI' THEN l.net_weight ELSE 0 END),0) semi_issued,COALESCE(SUM(CASE WHEN o.type IN ('SEMI_IN','FINISHED_IN') THEN l.net_weight ELSE 0 END),0) output,COALESCE(SUM(CASE WHEN o.type='RAW_RETURN' THEN l.net_weight ELSE 0 END),0) raw_returned,COALESCE(SUM(CASE WHEN o.type='SEMI_RETURN' THEN l.net_weight ELSE 0 END),0) semi_returned,COALESCE(SUM(CASE WHEN o.type='PROCESS_RAW_RETURN' THEN l.net_weight ELSE 0 END),0) process_returned FROM metal_warehouse_order o JOIN metal_warehouse_order_line l ON l.order_id=o.order_id WHERE o.batch_id=? AND o.status='POSTED'",b.get("batch_id"));
  if(d(totals.get("semi_issued")).signum()<=0||d(totals.get("output")).signum()<=0)throw new ServiceException("须先登记有效半成品领料及加工产出；未加工半成品请办理半成品还库");
  BigDecimal available=d(totals.get("issued")).subtract(d(totals.get("output"))).subtract(d(totals.get("raw_returned"))).subtract(d(totals.get("semi_returned"))).subtract(d(totals.get("process_returned")));
  if(available.signum()<0)throw new ServiceException("批次重量异常，请人工核对");
  var result=new LinkedHashMap<String,Object>();result.put("batchId",b.get("batch_id").toString());result.put("batchNo",b.get("batch_no"));result.put("version",b.get("version"));result.put("customer",customer);result.put("remainingWeight",available.setScale(6).toPlainString());
  for(var e:totals.entrySet())result.put(e.getKey(),d(e.getValue()).setScale(6).toPlainString());return result;
 }
 public void register(long line,long inventory,long batch,Map<String,Object> context){jdbc.update("INSERT INTO metal_process_raw_return_line(line_id,batch_id,inventory_id,source_snapshot) VALUES(?,?,?,?)",line,batch,inventory,JSON.toJSONString(context));}
 /** All stock-writing entry points must use this guard, not only candidate filtering. */
 public void guardStock(long stock,String targetCustomer,boolean production,boolean adjustment){
  var links=jdbc.queryForList("SELECT b.status,w.customer,o.status order_status,w.factory_id FROM metal_process_raw_return_line r JOIN metal_process_batch b ON b.batch_id=r.batch_id JOIN metal_work_order w ON w.work_order_id=b.work_order_id JOIN metal_warehouse_order_line l ON l.line_id=r.line_id JOIN metal_warehouse_order o ON o.order_id=l.order_id WHERE r.inventory_id=?",stock);
  if(links.isEmpty())return;
  var link=links.get(0);scope.check(n(link.get("factory_id")));
  if(adjustment)throw new ServiceException("加工回收原料不能普通调整或归档，请核对来源并通过受控纠错处理");
  if(!"SETTLED".equals(link.get("status"))||!"POSTED".equals(link.get("order_status")))throw new ServiceException("加工余料来源批次未结算或已冲销，暂不能领用");
  if(!production||targetCustomer==null||targetCustomer.isBlank()||!targetCustomer.trim().equals(Objects.toString(link.get("customer"),"").trim()))throw new ServiceException("加工回收原料仅可由同厂同客户工单生产领用");
 }
 public void guardAppend(long stock){if(!jdbc.queryForList("SELECT line_id FROM metal_process_raw_return_line WHERE inventory_id=?",stock).isEmpty())throw new ServiceException("加工回收库存不能合并其他入库或调整，请使用独立库存批号");}
 public Map<String,Object> provenance(long stock){
  var rows=jdbc.queryForList("SELECT w.order_no recovery_work_order_no,s.process_name recovery_process_name,b.batch_no recovery_batch_no,b.status recovery_batch_status,o.order_no recovery_order_no FROM metal_process_raw_return_line r JOIN metal_process_batch b ON b.batch_id=r.batch_id JOIN metal_work_order w ON w.work_order_id=b.work_order_id JOIN metal_work_order_step s ON s.item_id=b.item_id AND s.process_id=b.process_id JOIN metal_warehouse_order_line l ON l.line_id=r.line_id JOIN metal_warehouse_order o ON o.order_id=l.order_id WHERE r.inventory_id=?",stock);
  return rows.isEmpty()?Map.of():rows.get(0);
 }
}
