package com.ruoyi.metal.service;

import java.util.*;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.metal.security.FactoryScope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Pending acceptance keeps its planned allocation; only a complete handover opens the next part. */
@Service
public class ContinuousBatchPlanning {
    private final JdbcTemplate jdbc;
    private final FactoryScope scope;
    public ContinuousBatchPlanning(JdbcTemplate jdbc,FactoryScope scope){this.jdbc=jdbc;this.scope=scope;}
    private static long n(Object value){return value==null?0:Long.parseLong(value.toString());}
    private static boolean yes(Object value){return Boolean.TRUE.equals(value)||"1".equals(String.valueOf(value));}
    private Map<String,Object> one(String sql,Object...args){var rows=jdbc.queryForList(sql,args);if(rows.size()!=1)throw new ServiceException("工单或工序不存在，请重新选择");return rows.get(0);}
    private void entryPermit(){if(!scope.isAdmin()&&!List.of("metal:warehouse-order:add","metal:warehouse-order:import","metal:warehouse-order:query","metal:process-batch:add").stream().anyMatch(SecurityUtils::hasPermi))throw new ServiceException("没有加工批次查询权限",403);}

    List<Map<String,Object>> open(Long item,Long process){
        return jdbc.queryForList("SELECT b.*,CASE WHEN b.handover_policy_version=2 THEN (SELECT COALESCE(SUM(COALESCE(wip.remaining_weight,l.net_weight)),0) FROM metal_warehouse_order o JOIN metal_warehouse_order_line l ON l.order_id=o.order_id LEFT JOIN metal_batch_wip_balance wip ON wip.source_line_id=l.line_id WHERE o.batch_id=b.batch_id AND o.type='PRODUCTION_OUT' AND o.status='POSTED') ELSE NULL END current_wip_weight,EXISTS(SELECT 1 FROM metal_warehouse_flow f WHERE f.batch_id=b.batch_id AND f.kind='HANDOVER' AND f.status NOT IN ('VOID','CANCELLED')) handover_started,EXISTS(SELECT 1 FROM metal_warehouse_flow f WHERE f.batch_id=b.batch_id AND f.kind='HANDOVER' AND f.status NOT IN ('VOID','CANCELLED') AND f.stage='KEEPER' AND EXISTS(SELECT 1 FROM metal_warehouse_flow_processor p WHERE p.flow_id=f.flow_id) AND NOT EXISTS(SELECT 1 FROM metal_warehouse_flow_processor p WHERE p.flow_id=f.flow_id AND p.submitted=FALSE)) handover_submitted FROM metal_process_batch b WHERE b.item_id=? AND b.process_id=? AND b.status='OPEN' ORDER BY b.batch_id",item,process);
    }
    void requireNew(Long item,Long process){
        if(open(item,process).stream().anyMatch(b->!PartialHandoverLedger.partial(b)&&!yes(b.get("handover_submitted"))))throw new ServiceException("当前工序仍有未提交完整交接的历史批次，请完成全部领料人的交接后再新开下一批");
    }
    Map<String,Object> select(Long item,Long process,Long requested,Integer version,String action){
        if(action!=null&&!Set.of("NEW","APPEND").contains(action))throw new ServiceException("请选择新开加工批次或当前批次补料");
        if("NEW".equals(action)){
            if(requested!=null)throw new ServiceException("新开批次不能同时指定已有批次");
            requireNew(item,process);return null;
        }
        if("APPEND".equals(action)&&requested==null)throw new ServiceException("补料须明确选择加工批次");
        var batches=open(item,process);
        Long selected=BatchCompletionRules.selectBatch(batches.stream().map(b->n(b.get("batch_id"))).toList(),requested);
        if(selected==null)return null;
        var batch=batches.stream().filter(b->n(b.get("batch_id"))==selected).findFirst().orElseThrow();
        if(requested!=null&&(version==null||n(batch.get("version"))!=version))throw new ServiceException("批次版本已变化，请重新核对",409);
        if(!PartialHandoverLedger.partial(batch)&&yes(batch.get("handover_submitted")))throw new ServiceException("本批已提交完整交接，不能追加领料；请选择新开加工批次");
        return batch;
    }
    static long available(long planned,long completed,long pending,long credit,long reservedCredit){return Math.max(0,planned-completed-pending-credit-reservedCredit);}
    private Map<String,Object> target(long factory,long work,long item,long process){
        scope.check(factory);
        var row=one("SELECT s.*,i.quantity item_quantity,i.product_id,i.material_id,i.planned_input_weight,COALESCE(i.material_snapshot,i.product_snapshot) product_snapshot,i.work_order_id,w.factory_id,w.status work_status,w.task_type,w.processing_policy_version FROM metal_work_order_step s JOIN metal_work_order_item i ON i.item_id=s.item_id JOIN metal_work_order w ON w.work_order_id=i.work_order_id WHERE s.item_id=? AND s.process_id=? AND w.del_flag='0'",item,process);
        if(n(row.get("factory_id"))!=factory||n(row.get("work_order_id"))!=work)throw new ServiceException("批次关联不属于当前工厂或工单",403);
        return row;
    }
    private String mode(Map<String,Object> target){
        if("RAW_PROCESSING".equals(target.get("task_type"))||"RAW".equals(target.get("output_stage"))&&!MeasurementPolicy.configurable(jdbc,target))return "WEIGHT";
        return SemiMeasurementRules.mode(target.get("measurement_mode"));
    }
    private Map<String,Object> summary(Map<String,Object> target){
        long item=n(target.get("item_id")),process=n(target.get("process_id")),seq=n(target.get("sequence_no"));
        var pending=open(item,process);long allocated=pending.stream().mapToLong(b->n(b.get("planned_quantity"))-(PartialHandoverLedger.partial(b)?n(b.get("completed_quantity")):0)).sum();
        long completed=jdbc.queryForObject("SELECT COALESCE(SUM(completed_quantity),0) FROM metal_process_batch WHERE item_id=? AND process_id=? AND (status='SETTLED' OR status='OPEN' AND handover_policy_version=2)",Long.class,item,process);
        long remaining=available(n(target.get("item_quantity")),completed,allocated,new SemiFlowService(jdbc,scope).credit(item,seq),WarehouseReservations.pendingCredit(jdbc,item,seq));
        String measurement=mode(target);boolean closed=yes(target.get("weight_stage_closed")),unfinished=pending.stream().anyMatch(b->!PartialHandoverLedger.partial(b)&&!yes(b.get("handover_submitted")));
        boolean active=Set.of("WAITING_MATERIAL","IN_PROGRESS").contains(target.get("work_status"));
        var out=new LinkedHashMap<String,Object>();out.put("factoryId",String.valueOf(target.get("factory_id")));out.put("workOrderId",String.valueOf(target.get("work_order_id")));out.put("itemId",Long.toString(item));out.put("processId",Long.toString(process));out.put("processName",target.get("process_name"));out.put("sequenceNo",seq);out.put("measurementMode",measurement);out.put("plannedQuantity","WEIGHT".equals(measurement)?null:target.get("item_quantity"));out.put("completedQuantity","WEIGHT".equals(measurement)?null:completed);out.put("pendingQuantity","WEIGHT".equals(measurement)?null:allocated);out.put("availableQuantity","WEIGHT".equals(measurement)?null:remaining);
        long acceptance=jdbc.queryForObject("SELECT COALESCE(SUM(CAST(JSON_UNQUOTE(JSON_EXTRACT(l.line_json,'$.quantity')) AS UNSIGNED)),0) FROM metal_process_batch b JOIN metal_warehouse_flow f ON f.batch_id=b.batch_id AND f.kind='HANDOVER' AND f.status IN ('WAITING','COLLECTING','RETURNED') JOIN metal_warehouse_flow_group g ON g.flow_id=f.flow_id AND g.status<>'SUPERSEDED' AND g.type IN ('RAW_PROCESS_IN','SEMI_IN','FINISHED_IN') JOIN metal_warehouse_flow_line l ON l.group_id=g.group_id WHERE b.item_id=? AND b.process_id=? AND b.status='OPEN'",Long.class,item,process);
        out.put("pendingAcceptanceQuantity","WEIGHT".equals(measurement)?null:acceptance);out.put("inProgressQuantity","WEIGHT".equals(measurement)?null:Math.max(0,allocated-acceptance));
        out.put("canCreate",active&&!closed&&!unfinished&&("WEIGHT".equals(measurement)||remaining>0));
        out.put("blockedReason",!active?"当前工单已结束或不能投产":closed?"本工序已结束投产":unfinished?"当前批次尚未提交完整交接，请完成全部领料人的交接后再新开下一批":"PIECE".equals(measurement)&&remaining==0?"工序计划额度已用完；待验收批次仍占用计划额度":"");
        var batches=new ArrayList<Map<String,Object>>();
        for(var b:pending){var value=new LinkedHashMap<String,Object>();value.put("id",String.valueOf(b.get("batch_id")));value.put("batchNo",b.get("batch_no"));value.put("version",b.get("version"));value.put("plannedQuantity","WEIGHT".equals(measurement)?null:b.get("planned_quantity"));value.put("completedQuantity",b.get("completed_quantity"));value.put("currentWipWeight",b.get("current_wip_weight"));value.put("handoverSubmitted",yes(b.get("handover_submitted")));value.put("canAppend",active&&!closed&&(PartialHandoverLedger.partial(b)||!yes(b.get("handover_submitted"))));value.put("phase",PartialHandoverLedger.partial(b)?"在制／分次交接":yes(b.get("handover_submitted"))?"待库管验收":yes(b.get("handover_started"))?"交接收集中":"待领料／加工中");batches.add(value);}
        out.put("batches",batches);return out;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> context(long factory,long work,long item,long process){
        entryPermit();var row=target(factory,work,item,process);scope.requireCatalog("process",process,factory);
        scope.requireCatalog(row.get("material_id")!=null?"material":"product",n(row.get("material_id")!=null?row.get("material_id"):row.get("product_id")),factory);
        return summary(row);
    }
    int newQuantity(long factory,long work,long item,long process,Integer requested){
        var row=target(factory,work,item,process);var plan=summary(row);
        if(!Boolean.TRUE.equals(plan.get("canCreate")))throw new ServiceException(String.valueOf(plan.get("blockedReason")));
        if("WEIGHT".equals(plan.get("measurementMode"))){
            int placeholder="RAW".equals(row.get("output_stage"))||"RAW_PROCESSING".equals(row.get("task_type"))?0:1;
            if(requested!=null&&requested!=placeholder)throw new ServiceException("重量工序不填写有效计划件数");
            return placeholder;
        }
        if(requested==null)requested=Math.toIntExact(n(plan.get("availableQuantity")));
        if(requested<1||requested>1000000||requested>n(plan.get("availableQuantity")))throw new ServiceException("本次计划件数须为正整数，且不能超过工序剩余可安排件数");
        return requested;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public List<Map<String,Object>> progress(long work){
        if(!scope.isAdmin()&&!SecurityUtils.hasPermi("metal:work-order:query"))throw new ServiceException("没有工单查询权限",403);
        var header=one("SELECT factory_id FROM metal_work_order WHERE work_order_id=? AND del_flag='0'",work);long factory=n(header.get("factory_id"));scope.check(factory);
        var rows=jdbc.queryForList("SELECT s.item_id,s.process_id FROM metal_work_order_step s JOIN metal_work_order_item i ON i.item_id=s.item_id WHERE i.work_order_id=? ORDER BY i.sequence_no,s.sequence_no",work);
        var segments=jdbc.queryForList("SELECT f.flow_id id,f.batch_id batchId,b.batch_no batchNo,b.item_id itemId,b.process_id processId,f.segment_no segmentNo,f.status,f.request_actor processorId,u.nick_name processorName,f.create_time createTime,r.loss_weight lossWeight,x.outputQuantity,x.outputWeight FROM metal_warehouse_flow f JOIN metal_process_batch b ON b.batch_id=f.batch_id LEFT JOIN sys_user u ON u.user_id=f.request_actor LEFT JOIN metal_work_report r ON (r.handover_flow_id=f.flow_id OR f.handover_policy_version=1 AND r.handover_flow_id IS NULL AND r.batch_id=b.batch_id) AND r.voided=FALSE LEFT JOIN (SELECT g.flow_id,SUM(CAST(JSON_UNQUOTE(JSON_EXTRACT(l.line_json,'$.quantity')) AS UNSIGNED)) outputQuantity,SUM(l.processor_weight) outputWeight FROM metal_warehouse_flow_group g JOIN metal_warehouse_flow sf ON sf.flow_id=g.flow_id AND sf.work_order_id=? JOIN metal_warehouse_flow_line l ON l.group_id=g.group_id WHERE g.status<>'SUPERSEDED' AND g.type IN ('RAW_PROCESS_IN','SEMI_IN','FINISHED_IN') GROUP BY g.flow_id) x ON x.flow_id=f.flow_id WHERE f.work_order_id=? AND f.kind='HANDOVER' ORDER BY f.flow_id",work,work);
        var result=new ArrayList<Map<String,Object>>();
        for(var row:rows){var t=target(factory,work,n(row.get("item_id")),n(row.get("process_id")));var value=summary(t);value.put("productName",JSON.parseObject(Objects.toString(t.get("product_snapshot"),"{}")).get("name"));
            var finished=jdbc.queryForList("SELECT batch_id,batch_no,planned_quantity,completed_quantity,status FROM metal_process_batch WHERE item_id=? AND process_id=? AND status<>'OPEN' ORDER BY batch_id",row.get("item_id"),row.get("process_id"));
            var batches=(List<Map<String,Object>>)value.get("batches");
            for(var b:finished){var itemValue=new LinkedHashMap<String,Object>();itemValue.put("id",String.valueOf(b.get("batch_id")));itemValue.put("batchNo",b.get("batch_no"));itemValue.put("plannedQuantity","WEIGHT".equals(value.get("measurementMode"))?null:b.get("planned_quantity"));itemValue.put("completedQuantity","WEIGHT".equals(value.get("measurementMode"))?null:b.get("completed_quantity"));itemValue.put("phase","SETTLED".equals(b.get("status"))?"已验收／已结算":"已冲销");batches.add(itemValue);}
            value.put("handovers",segments.stream().filter(s->n(s.get("itemId"))==n(row.get("item_id"))&&n(s.get("processId"))==n(row.get("process_id"))).map(s->{var copy=new LinkedHashMap<String,Object>(s);for(String field:List.of("id","batchId","itemId","processId","processorId"))if(copy.get(field)!=null)copy.put(field,copy.get(field).toString());return copy;}).toList());
            result.add(value);
        }
        return result;
    }
}
