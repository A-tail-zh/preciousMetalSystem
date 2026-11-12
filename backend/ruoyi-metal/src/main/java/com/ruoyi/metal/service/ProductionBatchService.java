package com.ruoyi.metal.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.metal.security.FactoryScope;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.ruoyi.metal.service.StorageSupport.*;

/** Production identity and ordered processing; warehouse quantities remain owned by the storage ledger. */
@Service
public class ProductionBatchService {
    public record Page(List<Map<String,Object>> rows,long total) {}
    private final JdbcTemplate jdbc;
    private final FactoryScope scope;
    private final ProductionExecutionService executions;
    private static final Set<String> LIFECYCLES=Set.of("ACTIVE","SPLIT","MERGED","COMPLETED","CANCELLED","EXCEPTION_TERMINATED");
    private static final String HEADER="SELECT CAST(id AS CHAR) id,number,CAST(factory_id AS CHAR) factoryId,CAST(order_id AS CHAR) orderId,CAST(order_item_id AS CHAR) orderItemId,planned_quantity plannedQuantity,lifecycle,origin_action originAction,CAST(origin_operation_id AS CHAR) originOperationId,version,CAST(create_by AS CHAR) creatorId,DATE_FORMAT(create_time,'%Y-%m-%dT%H:%i:%s+08:00') createdAt,DATE_FORMAT(update_time,'%Y-%m-%dT%H:%i:%s+08:00') updatedAt FROM metal_production_batch";

    public ProductionBatchService(JdbcTemplate jdbc,FactoryScope scope,ProductionExecutionService executions){this.jdbc=jdbc;this.scope=scope;this.executions=executions;}

    public Page list(Map<String,String> params){
        allow(scope,"metal:work-order:list");
        long factory=scope.resolveFactory(optionalId(params.get("factoryId")));
        int page=params.get("pageNum")==null?1:version(params.get("pageNum"));
        int size=params.get("pageSize")==null?16:version(params.get("pageSize"));
        if(page<1||size<1||size>64)throw fail("分页参数无效");
        String lifecycle=text(params.get("lifecycle"));
        if(lifecycle.isEmpty())lifecycle="ACTIVE";
        if(!LIFECYCLES.contains(lifecycle)&&!"ALL".equals(lifecycle))throw fail("批次状态无效");
        String where=" WHERE factory_id=?";
        var args=new ArrayList<Object>();args.add(factory);
        for(String field:List.of("orderId","orderItemId"))if(!text(params.get(field)).isEmpty()){
            where+=field.equals("orderId")?" AND order_id=?":" AND order_item_id=?";args.add(id(params.get(field)));
        }
        if(!"ALL".equals(lifecycle)){where+=" AND lifecycle=?";args.add(lifecycle);}
        long count=jdbc.queryForObject("SELECT COUNT(*) FROM metal_production_batch"+where,Long.class,args.toArray());
        args.add(size);args.add((long)(page-1)*size);
        return new Page(jdbc.queryForList(HEADER+where+" ORDER BY id DESC LIMIT ? OFFSET ?",args.toArray()),count);
    }

    public Map<String,Object> detail(long batchId,Long requested){
        allow(scope,"metal:work-order:query");long factory=scope.resolveFactory(requested);
        var batch=read(factory,batchId);
        var raw=one(jdbc,"SELECT * FROM metal_production_batch WHERE factory_id=? AND id=?",factory,batchId);
        var inherited=raw.get("completed_step_ids")==null?List.of():JSON.parseArray(text(raw.get("completed_step_ids")));
        batch.put("completedStepIds",inherited);
        batch.put("routeSnapshot",JSON.parseObject(text(one(jdbc,"SELECT CAST(route_snapshot AS CHAR) snapshot FROM metal_production_batch WHERE factory_id=? AND id=?",factory,batchId).get("snapshot"))));
        var records=jdbc.queryForList("SELECT CAST(e.id AS CHAR) executionId,CAST(e.batch_id AS CHAR) batchId,CAST(e.order_step_id AS CHAR) orderStepId,CAST(e.processor_id AS CHAR) processorId,e.planned_quantity plannedQuantity,e.status,e.version,CAST(e.input_weight AS CHAR) inputWeight,CAST(e.output_weight AS CHAR) outputWeight,CAST(e.returned_weight AS CHAR) returnedWeight,CAST(e.recovered_weight AS CHAR) recoveredWeight,CAST(e.loss_weight AS CHAR) lossWeight,CAST(e.carry_in_weight AS CHAR) carryInWeight,CAST(e.carry_out_weight AS CHAR) carryOutWeight,e.carry_in_quantity carryInQuantity,e.carry_out_quantity carryOutQuantity,e.output_quantity outputQuantity,s.process_name processName,s.output_category outputCategory,s.measurement_mode measurementMode FROM metal_production_execution e JOIN metal_production_order_step s ON s.id=e.order_step_id WHERE e.factory_id=? AND e.batch_id=? ORDER BY s.sequence_no,e.id",factory,batchId);
        var legacyOutputs=legacyOutputs(raw);batch.put("legacyOutputContributions",legacyOutputs);
        for(var record:records){java.math.BigDecimal historical=java.math.BigDecimal.ZERO;long historicalQuantity=0;
            for(var output:legacyOutputs)if(number(output.get("orderStepId"))==number(record.get("orderStepId"))){historical=historical.add(new java.math.BigDecimal(text(output.get("weight"))));historicalQuantity+=number(output.get("quantity"));}
            record.put("historicalOutputWeight",historical.toPlainString());record.put("historicalOutputQuantity",historicalQuantity);
            record.put("effectiveOutputWeight",historical.add(new java.math.BigDecimal(text(record.get("outputWeight")))).toPlainString());record.put("effectiveOutputQuantity",historicalQuantity+number(record.get("outputQuantity")));
        }
        batch.put("executions",records);
        boolean canExecute=scope.isAdmin()||SecurityUtils.hasPermi("metal:work-order:execute");
        var route=(Map<?,?>)batch.get("routeSnapshot");var steps=(List<?>)route.get("steps");
        var live=records.stream().filter(row->!"CANCELLED".equals(row.get("status"))).toList();
        boolean canStart=canExecute&&"ACTIVE".equals(batch.get("lifecycle"))&&inherited.size()+live.size()<steps.size()&&live.stream().allMatch(row->"CLOSED".equals(row.get("status")));
        var last=live.isEmpty()?null:live.get(live.size()-1);
        boolean partial=last!=null&&"PIECE".equals(last.get("measurementMode"))&&(batch.get("plannedQuantity")==null||number(last.get("outputQuantity"))!=number(batch.get("plannedQuantity")));
        if(partial)canStart=false;
        var actions=new ArrayList<Map<String,Object>>();
        var startAction=new LinkedHashMap<String,Object>();startAction.put("action","START_STAGE");startAction.put("allowed",canStart);startAction.put("reasonCode",canStart?null:canExecute?"INVALID_STAGE":"FORBIDDEN");startAction.put("message",canStart?"按冻结路线进入下一工序；提交时核验整批产出及占用":!canExecute?"没有生产执行权限":partial?"本批尚未完成全部计划件数，部分产出须先拆批":"仍在加工、路线已结束或批次已退出");actions.add(startAction);
        boolean supplement=canExecute&&"ACTIVE".equals(batch.get("lifecycle"))&&live.stream().anyMatch(row->"OPEN".equals(row.get("status")));
        actions.add(Map.of("action","SUPPLEMENT","allowed",supplement,"message",supplement?"补料归当前加工记录":"请先确认当前开放加工记录"));
        boolean transform=scope.isAdmin()||SecurityUtils.hasPermi("metal:production-batch:transform");
        for(String action:List.of("SPLIT","MERGE"))actions.add(Map.of("action",action,"allowed",transform&&"ACTIVE".equals(batch.get("lifecycle")),"message",transform?"提交时重新核对阶段、来源、占用及件重守恒":"没有批次拆合权限"));
        actions.add(Map.of("action","COMPLETE","allowed",canExecute&&!partial&&"ACTIVE".equals(batch.get("lifecycle"))&&inherited.size()+live.size()==steps.size()&&live.stream().allMatch(row->"CLOSED".equals(row.get("status"))),"message","提交时核验全部工序、成品及未处理单据"));
        batch.put("actions",actions);batch.put("stageSummary",records.isEmpty()?(inherited.isEmpty()?"尚未开始":"已承接前"+inherited.size()+"道完成阶段"):live.stream().anyMatch(row->"OPEN".equals(row.get("status")))?"本工序加工中":"本工序已结束");
        batch.put("nextStep",inherited.size()+live.size()<steps.size()?steps.get(inherited.size()+live.size()):null);
        batch.put("materialFlowEnabled",true);
        batch.put("phase","P3_TRANSFORM");
        return batch;
    }

    /** References an accepted legacy OUTPUT once; it never creates another production event. */
    List<Map<String,Object>> legacyOutputs(Map<String,Object> batch){
        if(!"MIGRATION".equals(batch.get("origin_action"))||batch.get("origin_operation_id")==null)return List.of();
        var ops=jdbc.queryForList("SELECT action,CAST(result AS CHAR) result FROM metal_production_execution_operation WHERE factory_id=? AND id=?",batch.get("factory_id"),batch.get("origin_operation_id"));
        if(ops.size()!=1||!"LEGACY_ADOPT".equals(ops.get(0).get("action")))return List.of();var result=JSON.parseObject(text(ops.get(0).get("result")));
        if(result==null||number(result.get("factoryId"))!=number(batch.get("factory_id"))||number(result.get("orderId"))!=number(batch.get("order_id"))||number(result.get("orderItemId"))!=number(batch.get("order_item_id")))return List.of();
        var components=result.getJSONArray("stockComponents");if(components==null)return List.of();var outputs=new ArrayList<Map<String,Object>>();var seen=new HashSet<Long>();
        for(Object raw:components){var component=(Map<String,Object>)raw;if(number(component.get("batchId"))!=number(batch.get("id")))continue;long event=number(component.get("productionEventId"));if(!seen.add(event))throw fail("历史产出承接证据重复");
            var facts=jdbc.queryForList("SELECT ev.weight,ev.quantity,e.order_step_id FROM metal_production_execution_event ev JOIN metal_production_execution e ON e.factory_id=ev.factory_id AND e.id=ev.execution_id JOIN metal_storage_stock st ON st.factory_id=ev.factory_id AND st.id=ev.stock_id JOIN metal_storage_batch_slice sl ON sl.factory_id=st.factory_id AND sl.stock_id=st.id JOIN metal_storage_document d ON d.factory_id=ev.factory_id AND d.id=ev.document_id AND d.status='POSTED' AND d.deleted=0 JOIN metal_storage_event se ON se.factory_id=ev.factory_id AND se.document_id=ev.document_id AND se.line_id=ev.line_id AND se.action='INBOUND' AND se.reversed_by IS NULL WHERE ev.factory_id=? AND ev.id=? AND ev.execution_id=? AND ev.stock_id=? AND ev.event_type='OUTPUT' AND ev.reversed=0 AND e.order_id=? AND e.order_item_id=? AND st.origin_document_id=ev.document_id AND st.origin_line_id=ev.line_id AND sl.id=? AND sl.batch_id=? AND sl.legacy_adoption_operation_id=?",batch.get("factory_id"),event,component.get("sourceExecutionId"),component.get("stockId"),batch.get("order_id"),batch.get("order_item_id"),component.get("stockSliceId"),batch.get("id"),batch.get("origin_operation_id"));
            if(facts.size()!=1||new java.math.BigDecimal(text(facts.get(0).get("weight"))).compareTo(new java.math.BigDecimal(text(component.get("weight"))))!=0||number(facts.get(0).get("quantity"))!=number(component.get("quantity")))throw fail("历史产出承接证据与原事件不符");
            var output=new LinkedHashMap<String,Object>(component);output.put("orderStepId",text(facts.get(0).get("order_step_id")));outputs.add(output);
        }return outputs;
    }

    public Map<String,Object> context(long batchId,Long requested,String intent){
        if(!Set.of("START_STAGE","SUPPLEMENT","COMPLETE","SPLIT","MERGE").contains(text(intent)))throw fail("批次操作上下文无效");
        var batch=detail(batchId,requested);long factory=id(batch.get("factoryId"));
        batch.put("intent",intent);
        batch.put("stockSlices",new ProductionBatchMaterialService(jdbc).stockContext(factory,batchId));
        var wip=new ArrayList<Map<String,Object>>();
        for(var e:jdbc.queryForList("SELECT id FROM metal_production_execution WHERE factory_id=? AND batch_id=? AND status='OPEN'",factory,batchId))wip.addAll(new ProductionBatchMaterialService(jdbc).wipContext(factory,id(e.get("id"))));
        batch.put("wipSources",wip);
        batch.put("pendingDocuments",jdbc.queryForList("SELECT CAST(d.id AS CHAR) id,d.number,d.status,CAST(d.execution_id AS CHAR) executionId FROM metal_storage_document d JOIN metal_production_execution e ON e.id=d.execution_id AND e.factory_id=d.factory_id WHERE d.factory_id=? AND e.batch_id=? AND d.deleted=0 AND d.status IN ('DRAFT','PENDING_REVIEW','RETURNED') ORDER BY d.id",factory,batchId));
        return batch;
    }

    @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> create(Map<String,Object> body){
        allow(scope,"metal:work-order:execute");
        return createChecked(body);
    }
    private Map<String,Object> createChecked(Map<String,Object> body){
        fields(body,Set.of("factoryId","requestId","orderId","orderItemId","orderVersion","plannedQuantity"));
        long factory=scope.resolveFactory(optionalId(body.get("factoryId")));
        long orderId=id(body.get("orderId")),itemId=id(body.get("orderItemId"));
        int orderVersion=version(body.get("orderVersion"));
        Long planned=planned(body.get("plannedQuantity"));
        String request=uuid(body.get("requestId"));
        var normalized=new LinkedHashMap<String,Object>();normalized.put("factoryId",String.valueOf(factory));normalized.put("orderId",String.valueOf(orderId));normalized.put("orderItemId",String.valueOf(itemId));normalized.put("orderVersion",orderVersion);normalized.put("plannedQuantity",planned);
        String fingerprint=hash(normalized);
        scope.lockFactories(List.of(factory));
        var replay=replay(factory,request,"CREATE_BATCH",fingerprint);if(replay!=null)return replay;
        var order=one(jdbc,"SELECT id,version,status,flow_version,deleted FROM metal_production_order WHERE factory_id=? AND id=? FOR UPDATE",factory,orderId);
        if(number(order.get("deleted"))!=0)throw new ServiceException("工单不存在或不属于当前工厂",404);
        if(number(order.get("version"))!=orderVersion)throw new ServiceException("工单版本已变化，请刷新核对",409);
        if(number(order.get("flow_version"))<1)throw new ServiceException("历史工单未配置真实生产路线，不能创建生产批次",409);
        if(!Set.of("CREATED","IN_PROGRESS").contains(text(order.get("status"))))throw new ServiceException("工单已经完工，不能创建生产批次",409);
        var item=one(jdbc,"SELECT id,quantity FROM metal_production_order_item WHERE order_id=? AND id=? FOR UPDATE",orderId,itemId);
        // Factory-scope checks may open an older RR snapshot before waiting for the factory lock.
        if(jdbc.queryForList("SELECT * FROM metal_production_execution WHERE factory_id=? AND order_item_id=? AND batch_id IS NULL AND status<>'CANCELLED' ORDER BY id FOR UPDATE",factory,itemId).stream().anyMatch(e->ProductionExecutionService.legacyAdoption(jdbc,e)==null))throw new ServiceException("产品组存在未映射的历史加工记录，请先核实来源后再创建统一批次",409);
        long allocated=0;
        for(var allocation:jdbc.queryForList("SELECT planned_quantity FROM metal_production_batch WHERE factory_id=? AND order_item_id=? AND lifecycle NOT IN ('SPLIT','MERGED','CANCELLED') ORDER BY id FOR UPDATE",factory,itemId))allocated=Math.addExact(allocated,number(allocation.get("planned_quantity")));
        if(planned!=null&&planned>number(item.get("quantity"))-allocated)throw new ServiceException("本批计划超过产品组剩余未分配目标",409);
        var route=routeSnapshot(jdbc,itemId);
        var now=LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        long batchId=insert(jdbc,"INSERT INTO metal_production_batch(factory_id,number,order_id,order_item_id,route_snapshot,planned_quantity,create_by,create_time,update_time) VALUES(?,?,?,?,?,?,?,?,?)",factory,nextNumber(),orderId,itemId,JSON.toJSONString(route,JSONWriter.Feature.WriteMapNullValue),planned,SecurityUtils.getUserId(),now,now);
        var result=new LinkedHashMap<String,Object>();result.put("batchId",String.valueOf(batchId));result.put("batchVersion",0);result.put("version",0);result.put("number",read(factory,batchId).get("number"));result.put("lifecycle","ACTIVE");
        result=remember(factory,request,"CREATE_BATCH",fingerprint,result);
        jdbc.update("UPDATE metal_production_batch SET origin_operation_id=? WHERE factory_id=? AND id=?",id(result.get("operationId")),factory,batchId);
        return result;
    }

    // Actual issue sources validate the stage; no finished-piece target is inferred from raw weight.
    Map<String,Object> createForOutbound(long factory,Map<String,Object> doc){
        allow(scope,"metal:warehouse-order:add");
        long orderId=id(doc.get("orderId")),itemId=id(doc.get("orderItemId")),step=id(doc.get("orderStepId"));
        var order=one(jdbc,"SELECT version FROM metal_production_order WHERE factory_id=? AND id=? AND deleted=0 FOR UPDATE",factory,orderId);
        one(jdbc,"SELECT id FROM metal_production_order_item WHERE id=? AND order_id=? FOR UPDATE",itemId,orderId);
        var route=routeSnapshot(jdbc,itemId);
        var steps=(List<Map<String,Object>>)route.get("steps");
        int index=-1;for(int i=0;i<steps.size();i++)if(number(steps.get(i).get("id"))==step)index=i;
        if(index<0)throw fail("目标工序不属于所选产品组");
        var input=new LinkedHashMap<String,Object>();input.put("factoryId",String.valueOf(factory));input.put("requestId",doc.get("requestId"));input.put("orderId",String.valueOf(orderId));input.put("orderItemId",String.valueOf(itemId));input.put("orderVersion",order.get("version"));
        var result=createChecked(input);long batch=id(result.get("batchId"));
        var completed=steps.subList(0,index).stream().map(s->text(s.get("id"))).toList();
        jdbc.update("UPDATE metal_production_batch SET completed_step_ids=? WHERE factory_id=? AND id=?",JSON.toJSONString(completed),factory,batch);
        long execution=executions.createCarryExecution(factory,batch,step,id(doc.get("processorId")));
        jdbc.update("UPDATE metal_production_execution SET auto_created=1 WHERE factory_id=? AND id=?",factory,execution);
        jdbc.update("UPDATE metal_production_batch SET version=version+1 WHERE factory_id=? AND id=?",factory,batch);
        result=new LinkedHashMap<>(result);result.put("executionId",String.valueOf(execution));return result;
    }

    @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> start(long batchId,Map<String,Object> body){
        allow(scope,"metal:work-order:execute");
        fields(body,Set.of("factoryId","requestId","batchVersion","intent","orderStepId","processorId"));
        long factory=scope.resolveFactory(optionalId(body.get("factoryId")));
        int expected=version(body.get("batchVersion"));String request=uuid(body.get("requestId"));
        if(!"START_STAGE".equals(text(body.get("intent"))))throw fail("请选择开始本工序，补料不创建新的加工记录");
        long step=id(body.get("orderStepId")),processor=id(body.get("processorId"));
        String fingerprint=hash(Map.of("batchId",String.valueOf(batchId),"batchVersion",expected,"orderStepId",String.valueOf(step),"processorId",String.valueOf(processor),"factoryId",String.valueOf(factory),"intent","START_STAGE"));
        scope.lockFactories(List.of(factory));
        var replay=replay(factory,request,"START_STAGE",fingerprint);if(replay!=null)return replay;
        var batch=one(jdbc,"SELECT version FROM metal_production_batch WHERE factory_id=? AND id=? FOR UPDATE",factory,batchId);
        if(number(batch.get("version"))!=expected)throw new ServiceException("批次版本已变化，请刷新核对",409);
        long executionId=executions.createForBatch(factory,batchId,Map.of("orderStepId",String.valueOf(step),"processorId",String.valueOf(processor)));
        jdbc.update("UPDATE metal_production_batch SET version=version+1,update_time=? WHERE factory_id=? AND id=?",LocalDateTime.now(ZoneId.of("Asia/Shanghai")),factory,batchId);
        var result=new LinkedHashMap<String,Object>();result.put("batchId",String.valueOf(batchId));result.put("batchVersion",expected+1);result.put("executionId",String.valueOf(executionId));result.put("executionVersion",0);
        return remember(factory,request,"START_STAGE",fingerprint,result);
    }

    @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> complete(long batchId,Map<String,Object> body){
        allow(scope,"metal:work-order:execute");fields(body,Set.of("factoryId","requestId","version","reason"));
        long factory=scope.resolveFactory(optionalId(body.get("factoryId")));int expected=version(body.get("version"));
        String request=uuid(body.get("requestId")),why=reason(body.get("reason"));
        String fingerprint=hash(Map.of("factoryId",String.valueOf(factory),"batchId",String.valueOf(batchId),"version",expected,"reason",why));
        scope.lockFactories(List.of(factory));var previous=replay(factory,request,"COMPLETE_BATCH",fingerprint);if(previous!=null)return previous;
        var batch=one(jdbc,"SELECT * FROM metal_production_batch WHERE factory_id=? AND id=? FOR UPDATE",factory,batchId);
        if(number(batch.get("version"))!=expected)throw new ServiceException("批次版本已变化，请刷新核对",409);
        if(!"ACTIVE".equals(batch.get("lifecycle")))throw new ServiceException("生产批次已退出流转",409);
        var order=one(jdbc,"SELECT deleted,status FROM metal_production_order WHERE factory_id=? AND id=? FOR UPDATE",factory,batch.get("order_id"));
        if(number(order.get("deleted"))!=0||!Set.of("CREATED","IN_PROGRESS").contains(text(order.get("status"))))throw new ServiceException("工单已删除或已经结束",409);
        var route=routeSnapshot(jdbc,id(batch.get("order_item_id")));
        if(!hash(route).equals(hash(JSON.parseObject(text(batch.get("route_snapshot"))))))throw new ServiceException("冻结路线已变化，请核对",409);
        var records=jdbc.queryForList("SELECT e.*,s.output_category,s.measurement_mode FROM metal_production_execution e JOIN metal_production_order_step s ON s.id=e.order_step_id WHERE e.factory_id=? AND e.batch_id=? AND e.status<>'CANCELLED' ORDER BY s.sequence_no,e.id FOR UPDATE",factory,batchId);
        var steps=(List<?>)route.get("steps");
        if(ProductionBatchTransformService.completed(batch,records).size()!=steps.size())throw new ServiceException("本批必需工序尚未全部完成",409);
        Long planned=planned(batch.get("planned_quantity"));
        var material=new ProductionBatchMaterialService(jdbc);
        for(var record:records)if(material.enabled()){
            var remaining=material.remaining(id(record.get("id")));
            if(new java.math.BigDecimal(text(remaining.get("weight"))).signum()!=0||number(remaining.get("quantity"))!=0)throw new ServiceException("本批仍有未处置的实际来源余额，不能完工",409);
        }
        if(!records.isEmpty()){
            var last=records.get(records.size()-1);
            var actual=one(jdbc,"SELECT COALESCE(SUM(ev.weight),0) weight,COALESCE(SUM(ev.quantity),0) quantity FROM metal_production_execution_event ev JOIN metal_storage_document d ON d.factory_id=ev.factory_id AND d.id=ev.document_id JOIN metal_storage_event se ON se.factory_id=ev.factory_id AND se.document_id=ev.document_id AND se.line_id=ev.line_id AND se.action='INBOUND' WHERE ev.factory_id=? AND ev.execution_id=? AND ev.event_type='OUTPUT' AND ev.reversed=0 AND d.status='POSTED' AND d.deleted=0 AND se.reversed_by IS NULL",factory,last.get("id"));
            if(!"PRODUCT".equals(last.get("output_category"))||new java.math.BigDecimal(text(actual.get("weight"))).signum()<=0||planned!=null&&number(actual.get("quantity"))!=planned)throw new ServiceException("本批成品实际验收未达到计划，请核对差额或异常处置",409);
        }else{
            // Delivery does not undo accepted production; use immutable received contributions, not on-hand stock.
            var stock=jdbc.queryForList("SELECT sl.id,st.category,(SELECT COALESCE(SUM(ms.initial_quantity),0) FROM metal_production_material_source ms WHERE ms.factory_id=sl.factory_id AND ms.stock_slice_id=sl.id) received_quantity FROM metal_storage_batch_slice sl JOIN metal_storage_stock st ON st.factory_id=sl.factory_id AND st.id=sl.stock_id WHERE sl.factory_id=? AND sl.batch_id=? ORDER BY sl.id",factory,batchId);long quantity=0;
            for(var sl:stock){if(!"PRODUCT".equals(sl.get("category")))throw new ServiceException("本批尚未形成有效成品",409);material.accepted(factory,id(sl.get("id")));quantity+=number(sl.get("received_quantity"));}
            if(stock.isEmpty()||planned!=null&&quantity!=planned)throw new ServiceException("本批成品未达到计划",409);
        }
        if(!jdbc.queryForList("SELECT d.id FROM metal_storage_document d JOIN metal_production_execution e ON e.id=d.execution_id AND e.factory_id=d.factory_id WHERE d.factory_id=? AND e.batch_id=? AND d.deleted=0 AND d.status IN ('DRAFT','PENDING_REVIEW','RETURNED') ORDER BY d.id FOR UPDATE",factory,batchId).isEmpty())throw new ServiceException("本批仍有未处理仓储单据",409);
        jdbc.update("UPDATE metal_production_batch SET lifecycle='COMPLETED',version=version+1,update_time=? WHERE factory_id=? AND id=?",LocalDateTime.now(ZoneId.of("Asia/Shanghai")),factory,batchId);
        var result=new LinkedHashMap<String,Object>();result.put("batchId",String.valueOf(batchId));result.put("batchVersion",expected+1);result.put("lifecycle","COMPLETED");
        return remember(factory,request,"COMPLETE_BATCH",fingerprint,result);
    }

    public Map<String,Object> operation(String requestId,Long requested){
        allow(scope,"metal:work-order:execute","metal:production-batch:reverse");long factory=scope.resolveFactory(requested);String request=uuid(requestId);
        var rows=jdbc.queryForList("SELECT CAST(id AS CHAR) id,action,CAST(result AS CHAR) result FROM metal_production_execution_operation WHERE factory_id=? AND actor_id=? AND request_id=? AND action IN ('CREATE_BATCH','START_STAGE','COMPLETE_BATCH','SPLIT','MERGE','REVERSE_TRANSFORM')",factory,SecurityUtils.getUserId(),request);
        if(rows.isEmpty())return Map.of("found",false,"committed",false,"requestId",request);
        var row=rows.get(0);var result=JSON.parseObject(text(row.get("result")));
        var out=new LinkedHashMap<String,Object>();out.put("found",true);out.put("committed",true);out.put("requestId",request);out.put("operationId",row.get("id"));out.put("action",row.get("action"));out.put("result",result);
        long batchId=id(result.get("batchId"));out.put("current",read(factory,batchId));return out;
    }

    private Map<String,Object> read(long factory,long batchId){return new LinkedHashMap<>(one(jdbc,HEADER+" WHERE factory_id=? AND id=?",factory,batchId));}

    private Map<String,Object> replay(long factory,String request,String action,String fingerprint){
        var rows=jdbc.queryForList("SELECT action,request_hash,CAST(result AS CHAR) result FROM metal_production_execution_operation WHERE factory_id=? AND actor_id=? AND request_id=? FOR UPDATE",factory,SecurityUtils.getUserId(),request);
        if(rows.isEmpty())return null;
        var row=rows.get(0);if(!action.equals(text(row.get("action")))||!fingerprint.equals(text(row.get("request_hash"))))throw new ServiceException("相同请求标识的操作内容已变化，请核实原结果",409);
        return JSON.parseObject(text(row.get("result")));
    }

    private LinkedHashMap<String,Object> remember(long factory,String request,String action,String fingerprint,LinkedHashMap<String,Object> result){
        result.put("requestId",request);result.put("committed",true);
        long operationId=insert(jdbc,"INSERT INTO metal_production_execution_operation(factory_id,actor_id,request_id,action,request_hash,result) VALUES(?,?,?,?,?,?)",factory,SecurityUtils.getUserId(),request,action,fingerprint,JSON.toJSONString(result,JSONWriter.Feature.WriteMapNullValue));
        result.put("operationId",String.valueOf(operationId));
        jdbc.update("UPDATE metal_production_execution_operation SET result=? WHERE factory_id=? AND id=?",JSON.toJSONString(result,JSONWriter.Feature.WriteMapNullValue),factory,operationId);
        return result;
    }

    String nextNumber(){
        LocalDate day=LocalDate.now(ZoneId.of("Asia/Shanghai"));
        jdbc.update("INSERT IGNORE INTO metal_storage_sequence(business_date,prefix,value) VALUES(?,'PB',1)",day);
        int next=jdbc.queryForObject("SELECT value FROM metal_storage_sequence WHERE business_date=? AND prefix='PB' FOR UPDATE",Integer.class,day);
        if(next>=999999)throw new ServiceException("当日生产批次编号已用尽",409);
        jdbc.update("UPDATE metal_storage_sequence SET value=value+1 WHERE business_date=? AND prefix='PB'",day);
        return "PB"+day.format(DateTimeFormatter.BASIC_ISO_DATE)+String.format(Locale.ROOT,"%06d",next);
    }

    long createTransformed(long factory,Map<String,Object> source,Long planned,String action,long operation,List<String> completed){
        return insert(jdbc,"INSERT INTO metal_production_batch(factory_id,number,order_id,order_item_id,route_snapshot,planned_quantity,origin_action,origin_operation_id,completed_step_ids,create_by) VALUES(?,?,?,?,?,?,?,?,?,?)",factory,nextNumber(),source.get("order_id"),source.get("order_item_id"),text(source.get("route_snapshot")),planned,action,operation,JSON.toJSONString(completed),SecurityUtils.getUserId());
    }

    static Map<String,Object> routeSnapshot(JdbcTemplate jdbc,long itemId){
        var item=one(jdbc,ProductionBatchMaterialService.currentRead("SELECT product_id,CAST(product_snapshot AS CHAR) product_snapshot FROM metal_production_order_item WHERE id=? FOR UPDATE"),itemId);
        var route=new LinkedHashMap<String,Object>();route.put("productId",String.valueOf(item.get("product_id")));route.put("productSnapshot",JSON.parseObject(text(item.get("product_snapshot"))));
        var steps=new ArrayList<Map<String,Object>>();
        for(var row:jdbc.queryForList(ProductionBatchMaterialService.currentRead("SELECT id,process_id,process_name,sequence_no,CAST(process_snapshot AS CHAR) process_snapshot,semantic_code,output_category,output_form,output_material_id,measurement_mode FROM metal_production_order_step WHERE item_id=? ORDER BY sequence_no,id FOR UPDATE"),itemId)){
            var step=new LinkedHashMap<String,Object>();step.put("id",String.valueOf(row.get("id")));step.put("processId",String.valueOf(row.get("process_id")));step.put("processName",row.get("process_name"));step.put("sequenceNo",number(row.get("sequence_no")));step.put("processSnapshot",JSON.parseObject(text(row.get("process_snapshot"))));
            for(var field:Map.of("semantic_code","semanticCode","output_category","outputCategory","output_form","outputForm","measurement_mode","measurementMode").entrySet())step.put(field.getValue(),row.get(field.getKey()));
            step.put("outputMaterialId",row.get("output_material_id")==null?null:String.valueOf(row.get("output_material_id")));steps.add(step);
        }
        if(steps.isEmpty())throw new ServiceException("产品组未配置冻结生产路线",409);
        route.put("steps",steps);return route;
    }

    static Long planned(Object value){if(value==null)return null;long planned=id(value);if(planned>Integer.MAX_VALUE)throw fail("本批计划件数超出范围");return planned;}
    private static long number(Object value){return value==null?0:Long.parseLong(value.toString());}
    private static void fields(Map<String,Object> body,Set<String> allowed){if(body==null||!allowed.containsAll(body.keySet()))throw fail("请求包含不支持的字段");}
}


