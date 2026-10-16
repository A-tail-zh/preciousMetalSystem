package com.ruoyi.metal.service;
import java.util.*;import org.springframework.stereotype.Component;import com.ruoyi.metal.catalog.*;import com.ruoyi.metal.security.FactoryScope;import com.ruoyi.common.exception.ServiceException;
/** Shared parameterized predicates for list and export; no client SQL accepted. */
@Component public class BusinessQueries {
 private final FactoryScope scope;public BusinessQueries(FactoryScope scope){this.scope=scope;}
 public record Parts(String sql,List<Object> args){}
 private static class Build {String sql="1=1";List<Object> args=new ArrayList<>();void add(String clause,Object value){if(value!=null){sql+=" AND "+clause;args.add(value);}}Parts result(){return new Parts(sql,List.copyOf(args));}}
 private Build base(ListQuery q,String alias){q.validate();var b=new Build();Long own=scope.currentFactory();if(own!=null&&q.getFactoryId()!=null&&!own.equals(q.getFactoryId()))throw new ServiceException("不能查询其他工厂",403);b.add(alias+".factory_id=?",own==null?q.getFactoryId():own);return b;}
 public Parts staff(ListQuery q){var b=base(q,"u");b.sql+=" AND u.del_flag='0'";b.add("u.create_time>=?",q.getStartTime());b.add("u.create_time<?",q.getEndExclusive());b.add("EXISTS (SELECT 1 FROM sys_user_role ur WHERE ur.user_id=u.user_id AND ur.role_id=?)",q.getRoleId());return b.result();}
 public Parts orders(OrderListQuery q){var b=base(q,"t");b.add("t.kind=?",q.getFilter());b.add("t.type=?",q.getType());b.add("t.order_no LIKE ? ESCAPE '!'",ListQuery.pattern(q.getOrderNo()));b.add("EXISTS (SELECT 1 FROM metal_work_order wo WHERE wo.work_order_id=t.work_order_id AND wo.order_no LIKE ? ESCAPE '!')",ListQuery.pattern(q.getWorkOrderNo()));b.add("t.order_date>=?",q.getStartTime());b.add("t.order_date<?",q.getEndExclusive());b.add("t.warehouse_id=?",q.getWarehouseId());if(q.getOperatorId()!=null){b.sql+=" AND (JSON_CONTAINS(COALESCE(t.operator_ids,JSON_ARRAY()),JSON_QUOTE(?)) OR (t.operator_ids IS NULL AND t.operator_id=?))";b.args.add(q.getOperatorId().toString());b.args.add(q.getOperatorId());}b.add("t.recipient_id=?",q.getRecipientId());b.add("t.keeper_id=?",q.getKeeperId());return b.result();}
}
