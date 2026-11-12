package com.ruoyi.framework.audit;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ruoyi.metal.service.ProductionOrderService;

final class ProductionOrderAuditSnapshot {
    private final JdbcTemplate jdbc;
    ProductionOrderAuditSnapshot(JdbcTemplate jdbc){this.jdbc=jdbc;}
    static boolean handles(String path){return path.equals("/metal/production-order")||path.equals("/metal/production-order/")||path.matches("/metal/production-order/[0-9]+")||path.equals("/metal/production-order/batch-delete");}
    Map<String,Object> snapshot(AuditContext c,Object body,Object result){
        c.objectType="metal_production_order";
        if(body instanceof ProductionOrderService.Edit edit||body instanceof ProductionOrderService.Delete){
            Long factory=body instanceof ProductionOrderService.Edit e?e.data().factoryId():((ProductionOrderService.Delete)body).factoryId();
            if(factory==null)factory=c.actorFactory;
            if(c.actorId==null||factory==null||!Objects.equals(c.actorId,1L)&&!Objects.equals(factory,c.actorFactory))return Map.of();
            List<Long> ids=body instanceof ProductionOrderService.Delete d?d.orders().stream().map(ProductionOrderService.Target::id).toList():List.of(Long.valueOf(c.path.substring(c.path.lastIndexOf('/')+1)));
            Map<String,Object> out=new LinkedHashMap<>();
            for(Long id:ids){var rows=jdbc.queryForList("SELECT id,order_no,factory_id,status,version,deleted,update_by,update_time FROM metal_production_order WHERE id=? AND factory_id=?",id,factory);if(!rows.isEmpty())out.put(id.toString(),rows.get(0));}
            c.factories.add(factory);c.objectIds.put("productionOrderIds",ids);
            if(result instanceof Map<?,?> saved&&Boolean.TRUE.equals(saved.get("replayed")))c.replayed=true;
            return out;
        }
        if(!(body instanceof ProductionOrderService.Create input)||c.actorId==null)return Map.of();
        Long factory=input.factoryId()==null?c.actorFactory:input.factoryId();
        if(factory==null||!Objects.equals(c.actorId,1L)&&!Objects.equals(factory,c.actorFactory))return Map.of();
        c.factories.add(factory);
        var rows=jdbc.queryForList("SELECT id,order_no,factory_id,status,request_actor,create_time FROM metal_production_order WHERE factory_id=? AND request_actor=? AND request_id=?",factory,c.actorId,input.requestId());
        if(rows.isEmpty())return Map.of();var row=rows.get(0);
        c.objectIds.put("productionOrderId",row.get("id"));c.businessCode=row.get("order_no").toString();
        if(result instanceof Map<?,?> saved&&Boolean.TRUE.equals(saved.get("replayed")))c.replayed=true;
        return Map.of(row.get("id").toString(),row);
    }
}
