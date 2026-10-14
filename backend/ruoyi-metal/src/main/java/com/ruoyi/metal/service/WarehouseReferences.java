package com.ruoyi.metal.service;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ruoyi.common.exception.ServiceException;
/**
 * 检查基础资料的仓储引用，保护库存、单据和历史业务记录。
 */
public final class WarehouseReferences {
 private WarehouseReferences(){}
 /**
  * 检查工厂是否存在受保护的业务引用。
  */
 public static void factory(JdbcTemplate j,Long id){deny(j,"SELECT quote_id FROM metal_saved_quotation WHERE factory_id=? LIMIT 1",id);deny(j,"SELECT warehouse_id FROM metal_warehouse WHERE factory_id=? LIMIT 1",id);}
 /**
  * 检查产品是否存在受保护的业务引用。
  */
 public static void product(JdbcTemplate j,Long id){deny(j,"SELECT inventory_id FROM metal_inventory WHERE reference_id=? AND category<>'RAW' LIMIT 1",id);deny(j,"SELECT rule_id FROM metal_warning_rule WHERE product_id=? AND del_flag='0' LIMIT 1",id);}
 /**
  * 检查物料是否存在库存引用。
  */
 public static void material(JdbcTemplate j,Long id){deny(j,"SELECT inventory_id FROM metal_inventory WHERE reference_id=? AND category='RAW' LIMIT 1",id);}
 /**
  * 检查工序是否存在受保护的业务引用。
  */
 public static void process(JdbcTemplate j,Long id){deny(j,"SELECT batch_id FROM metal_process_batch WHERE process_id=? LIMIT 1",id);deny(j,"SELECT rule_id FROM metal_warning_rule WHERE process_id=? AND del_flag='0' LIMIT 1",id);}
 /**
  * 检查工作人员是否存在仓储单据等业务引用。
  */
 public static void person(JdbcTemplate j,Long id){deny(j,"SELECT order_id FROM metal_warehouse_order WHERE JSON_CONTAINS(operator_ids,JSON_QUOTE(CAST(? AS CHAR))) LIMIT 1",id);if(!j.queryForList("SELECT order_id FROM metal_warehouse_order WHERE operator_id=? OR keeper_id=? OR recipient_id=? OR inspector_id=? OR reviewer_id=? LIMIT 1",id,id,id,id,id).isEmpty())throw new ServiceException("人员有仓储历史引用，不能删除");deny(j,"SELECT warning_id FROM metal_warning_record WHERE handler_id=? LIMIT 1",id);deny(j,"SELECT history_id FROM metal_warning_history WHERE actor_id=? LIMIT 1",id);deny(j,"SELECT batch_id FROM metal_process_batch WHERE request_actor=? LIMIT 1",id);deny(j,"SELECT order_id FROM metal_warehouse_order WHERE request_actor=? LIMIT 1",id);}
 private static void deny(JdbcTemplate j,String sql,Long id){if(!j.queryForList(sql,id).isEmpty())throw new ServiceException("基础资料存在仓储或预警引用，不能删除");}
}
