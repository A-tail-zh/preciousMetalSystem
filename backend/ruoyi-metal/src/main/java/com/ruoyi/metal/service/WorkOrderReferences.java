package com.ruoyi.metal.service;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ruoyi.common.exception.ServiceException;
/** Current locking reads; callers already hold the relevant factory/line/product/process lock. */
public final class WorkOrderReferences {
 private WorkOrderReferences(){}
 /**
  * 检查工厂是否存在受保护的业务引用。
  */
 public static void factory(JdbcTemplate jdbc,Long id){deny(jdbc,"SELECT work_order_id FROM metal_work_order WHERE factory_id=? AND del_flag='0' FOR UPDATE",id,"工厂被工单引用，不能删除");}
 /**
  * 检查产线是否被有效工单引用，阻止解除或转移工厂关联。
  */
 public static void lineMove(JdbcTemplate jdbc,Long id){deny(jdbc,"SELECT work_order_id FROM metal_work_order WHERE production_line_id=? AND del_flag='0' FOR UPDATE",id,"产线被工单引用，不能解除或变更工厂");}
 /**
  * 检查产线是否存在历史工单，阻止物理删除。
  */
 public static void lineDelete(JdbcTemplate jdbc,Long id){deny(jdbc,"SELECT work_order_id FROM metal_work_order WHERE production_line_id=? FOR UPDATE",id,"产线存在历史工单，不能物理删除");}
 /**
  * 检查产品是否存在受保护的业务引用。
  */
 public static void product(JdbcTemplate jdbc,Long id){deny(jdbc,"SELECT i.item_id FROM metal_work_order_item i JOIN metal_work_order w ON w.work_order_id=i.work_order_id WHERE i.product_id=? AND w.del_flag='0' FOR UPDATE",id,"产品被工单引用，不能删除");}
 /**
  * 检查工序是否存在受保护的业务引用。
  */
 public static void process(JdbcTemplate jdbc,Long id){deny(jdbc,"SELECT s.step_id FROM metal_work_order_step s JOIN metal_work_order_item i ON i.item_id=s.item_id JOIN metal_work_order w ON w.work_order_id=i.work_order_id WHERE s.process_id=? AND w.del_flag='0' FOR UPDATE",id,"工序被工单引用，不能删除");}
 /**
  * 检查产线产品关联变更是否会移除工单已使用的产品。
  */
 public static void lineProducts(JdbcTemplate jdbc,Long lineId,List<Long> selected){for(var row:jdbc.queryForList("SELECT i.product_id FROM metal_work_order_item i JOIN metal_work_order w ON w.work_order_id=i.work_order_id WHERE w.production_line_id=? AND w.del_flag='0' FOR UPDATE",lineId)){if(!selected.contains(((Number)row.get("product_id")).longValue()))throw new ServiceException("不能移除有效工单使用的产线产品");}}
 public static void person(JdbcTemplate jdbc,Long id){deny(jdbc,"SELECT work_order_id FROM metal_work_order WHERE owner_id=? OR request_actor=? LIMIT 1 FOR UPDATE",id,id,"人员存在工单负责人或提交历史引用，不能删除");deny(jdbc,"SELECT review_id FROM metal_work_order_review WHERE actor_id=? LIMIT 1 FOR UPDATE",id,"人员存在工单审批历史引用，不能删除");}
 private static void deny(JdbcTemplate jdbc,String sql,Long id,Long other,String message){if(!jdbc.queryForList(sql,id,other).isEmpty())throw new ServiceException(message);}
 private static void deny(JdbcTemplate jdbc,String sql,Long id,String message){if(!jdbc.queryForList(sql,id).isEmpty())throw new ServiceException(message);}
}
