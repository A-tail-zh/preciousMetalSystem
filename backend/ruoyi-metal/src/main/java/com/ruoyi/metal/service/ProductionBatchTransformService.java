package com.ruoyi.metal.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.metal.security.FactoryScope;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.ruoyi.metal.service.StorageSupport.*;

/** Identity transfers preserve warehouse totals and original production events. */
@Service
public class ProductionBatchTransformService {
    private final JdbcTemplate jdbc;
    private final FactoryScope scope;
    private final ProductionBatchService batches;
    private final ProductionExecutionService executions;
    private final ProductionBatchMaterialService materials;
    public ProductionBatchTransformService(JdbcTemplate jdbc,FactoryScope scope,ProductionBatchService batches,ProductionExecutionService executions){
        this.jdbc=jdbc;this.scope=scope;this.batches=batches;this.executions=executions;this.materials=new ProductionBatchMaterialService(jdbc);
    }

    @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> splitPreview(Map<String,Object> body){return splitInternal(body,false);}
    @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> split(Map<String,Object> body){return splitInternal(body,true);}
    @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> mergePreview(Map<String,Object> body){return mergeInternal(body,false);}
    @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> merge(Map<String,Object> body){return mergeInternal(body,true);}

    private Map<String,Object> splitInternal(Map<String,Object> body,boolean write){
        long f=authorize(body);String request=write?uuid(body.get("requestId")):null;
        String why=reason(body.get("reason")),mode=text(body.get("mode"));
        if(!Set.of("PLAN_SPLIT","STOCK_SPLIT","PARTIAL_COMPLETION_WITH_WIP").contains(mode))throw fail("拆分模式无效");
        String fingerprint=fingerprint(body);
        if(write){scope.lockFactories(List.of(f));var prior=replay(f,request,"SPLIT",fingerprint);if(prior!=null)return prior;}
        long source=id(body.get("sourceBatchId"));var batch=source(f,source,version(body.get("sourceBatchVersion")),write);
        var records=records(f,source,write);var stock=materials.stockContext(f,source);
        var results=maps(body.get("results"));if(results.size()<2||results.size()>32)throw fail("拆分需要2至32个结果");
        ensureNoPending(f,source,write);var prefix=completed(batch,records);Map<String,Object> open=null;
        for(var row:records)if("OPEN".equals(row.get("status"))){if(open!=null)throw conflict("同批存在多个在制加工记录");open=row;}
        if("PLAN_SPLIT".equals(mode)){
            if(!stock.isEmpty()||records.stream().anyMatch(row->n(row.get("input_quantity"))!=0||money(row.get("input_weight")).signum()!=0||money(row.get("carry_in_weight")).signum()!=0)||!jdbc.queryForList("SELECT ev.id FROM metal_production_execution_event ev JOIN metal_production_execution e ON e.id=ev.execution_id WHERE e.factory_id=? AND e.batch_id=?",f,source).isEmpty())throw conflict("计划拆分仅限没有实物、在制及实际事件的批次");
        }else if("STOCK_SPLIT".equals(mode)){
            if(open!=null||stock.isEmpty())throw conflict("库存拆分须先结束加工且有已验收实物");
        }else{
            if(open==null||id(body.get("sourceExecutionId"))!=n(open.get("id"))||version(body.get("sourceExecutionVersion"))!=n(open.get("version")))throw conflict("在制加工记录或版本已变化");
            if(money(open.get("loss_weight")).signum()!=0)throw conflict("已结算损耗的记录不能移交在制");
        }
        var keys=new HashSet<String>();long plans=0;boolean nullPlan=false;var allocated=new LinkedHashMap<Long,BigDecimal>();var quantities=new LinkedHashMap<Long,Long>();
        BigDecimal wipWeight=BigDecimal.ZERO;long wipQuantity=0;var preview=new ArrayList<Map<String,Object>>();
        for(var result:results){
            String key=text(result.get("clientKey")),kind=text(result.get("kind"));if(key.isBlank()||key.length()>100||!keys.add(key))throw fail("结果标识必填且不得重复");
            Long plan=ProductionBatchService.planned(result.get("plannedQuantity"));if(plan==null)nullPlan=true;else plans=Math.addExact(plans,plan);
            var stockAllocations=maps(result.getOrDefault("stockAllocations",List.of()));var wipAllocations=maps(result.getOrDefault("wipAllocations",List.of()));
            if("PLAN_SPLIT".equals(mode)){if(!"PLAN".equals(kind)||!stockAllocations.isEmpty()||!wipAllocations.isEmpty())throw fail("计划拆分不能指定实物或在制");}
            else if("COMPLETED_STOCK".equals(kind)){
                if(stockAllocations.isEmpty()||!wipAllocations.isEmpty())throw fail("库存结果须明确实际库存份额");
                for(var a:stockAllocations){var sl=selected(stock,a);materials.validateStock(f,id(a.get("stockSliceId")),a);ensureStage(sl,prefix,open);long sid=id(a.get("stockSliceId"));allocated.merge(sid,positiveWeight(a),BigDecimal::add);quantities.merge(sid,q(a),Math::addExact);}
                requireResultPlan(plan,stockAllocations,stock);
            }else if("WIP".equals(kind)&&open!=null&&"PARTIAL_COMPLETION_WITH_WIP".equals(mode)){
                if(wipAllocations.isEmpty()||!stockAllocations.isEmpty())throw fail("在制结果须明确逐条来源分配");
                materials.validateWip(f,n(open.get("id")),wipAllocations);
                for(var a:wipAllocations){wipWeight=wipWeight.add(positiveWeight(a));wipQuantity=Math.addExact(wipQuantity,q(a));}
                id(result.get("processorId"));
                if("PIECE".equals(open.get("measurement_mode"))&&(plan==null||sumQuantity(wipAllocations)>plan))throw conflict("计件在制须保留明确计划，实际件数不得超过承接目标");
            }else throw fail("拆分结果类型与模式不一致");
            var row=new LinkedHashMap<String,Object>(result);row.put("completedStepIds","COMPLETED_STOCK".equals(kind)&&open!=null?append(prefix,text(open.get("order_step_id"))):prefix);preview.add(row);
        }
        requirePlan(batch,plans,nullPlan);
        for(var sl:stock){long sid=id(sl.get("stockSliceId"));if(money(sl.get("netWeight")).compareTo(allocated.getOrDefault(sid,BigDecimal.ZERO))!=0||n(sl.get("quantity"))!=quantities.getOrDefault(sid,0L))throw conflict("必须完整分配来源所有库存，不能留下隐式余额");}
        validateStockDistribution(f,stock,results);
        if(open!=null&&"PARTIAL_COMPLETION_WITH_WIP".equals(mode)){var allWip=new ArrayList<Map<String,Object>>();for(var result:results)allWip.addAll(maps(result.getOrDefault("wipAllocations",List.of())));materials.validateWip(f,n(open.get("id")),allWip);var remaining=materials.remaining(n(open.get("id")));if(wipWeight.compareTo(money(remaining.get("weight")))!=0||wipQuantity!=n(remaining.get("quantity")))throw conflict("在制来源分配必须完整覆盖剩余实际件重");}
        if(!write)return Map.of("allowed",true,"results",preview,"blockingReasons",List.of());
        var before=ProductionBatchReverseService.before(jdbc,f,List.of(source));before.put("materialBefore",materials.transformSnapshot(f,List.of(source)));long op=operation(f,request,"SPLIT",fingerprint,why);var output=new ArrayList<Map<String,Object>>();
        for(int i=0;i<results.size();i++){
            var result=results.get(i);var p=preview.get(i);long target=batches.createTransformed(f,batch,ProductionBatchService.planned(result.get("plannedQuantity")),"SPLIT",op,(List<String>)p.get("completedStepIds"));
            var out=identity(f,target);out.put("clientKey",result.get("clientKey"));out.put("completedStepIds",p.get("completedStepIds"));
            BigDecimal weight=BigDecimal.ZERO;long qty=0;var sliceResults=new ArrayList<Map<String,Object>>();
            for(var a:maps(result.getOrDefault("stockAllocations",List.of()))){long sid=moveStock(f,op,id(a.get("stockSliceId")),target,a);sliceResults.add(Map.of("stockSliceId",String.valueOf(sid),"stockId",text(a.get("stockId")),"netWeight",text(a.get("netWeight")),"quantity",q(a),"sliceVersion",one(jdbc,"SELECT version FROM metal_storage_batch_slice WHERE factory_id=? AND id=?",f,sid).get("version")));weight=weight.add(positiveWeight(a));qty+=q(a);}
            if(!sliceResults.isEmpty())out.put("stockSlices",sliceResults);
            if("WIP".equals(result.get("kind"))){long eid=executions.createCarryExecution(f,target,n(open.get("order_step_id")),id(result.get("processorId")));materials.transferWip(f,op,n(open.get("id")),eid,maps(result.getOrDefault("wipAllocations",List.of())));out.put("executionId",String.valueOf(eid));out.put("executionVersion",one(jdbc,"SELECT version FROM metal_production_execution WHERE factory_id=? AND id=?",f,eid).get("version"));for(var a:maps(result.getOrDefault("wipAllocations",List.of()))){weight=weight.add(positiveWeight(a));qty+=q(a);}out.put("carryInWeight",weight.toPlainString());out.put("carryInQuantity",qty);}
            edge(f,op,source,target,ProductionBatchService.planned(result.get("plannedQuantity")),weight,qty);output.add(out);
        }
        if(open!=null){if("PLAN_SPLIT".equals(mode))jdbc.update("UPDATE metal_production_execution SET status='CANCELLED',version=version+1,close_reason=?,closed_at=NOW() WHERE factory_id=? AND id=?",why,f,open.get("id"));else jdbc.update("UPDATE metal_production_execution SET status='SPLIT_TRANSFERRED',loss_weight=0,version=version+1,close_reason=?,closed_at=NOW() WHERE factory_id=? AND id=?",why,f,open.get("id"));}
        exit(f,source,"SPLIT");var out=new LinkedHashMap<String,Object>();out.put("source",identity(f,source));out.put("results",output);out.put("batchId",String.valueOf(source));out.put("reversalSnapshot",ProductionBatchReverseService.snapshot(jdbc,f,op,before));return finish(f,op,request,out);
    }

    private Map<String,Object> mergeInternal(Map<String,Object> body,boolean write){
        long f=authorize(body);String request=write?uuid(body.get("requestId")):null,why=reason(body.get("reason")),mode=text(body.get("mode"));
        if(!Set.of("FULL_MERGE","PARTIAL_MERGE").contains(mode))throw fail("合并模式无效");String fp=fingerprint(body);
        if(write){scope.lockFactories(List.of(f));var prior=replay(f,request,"MERGE",fp);if(prior!=null)return prior;}
        var inputs=maps(body.get("sources"));if(inputs.size()<2||inputs.size()>32)throw fail("合并需要2至32个不同批次");
        inputs=new ArrayList<>(inputs);inputs.sort(Comparator.comparingLong(row->id(row.get("batchId"))));
        var seen=new HashSet<Long>();var sources=new ArrayList<Map<String,Object>>();var remains=new ArrayList<Map<String,Object>>();Map<String,Object> first=null;List<String> stage=null;long mergedPlan=0;boolean hasNull=false;
        for(var input:inputs){long bid=id(input.get("batchId"));if(!seen.add(bid))throw fail("合并来源不得重复");var batch=source(f,bid,version(input.get("version")),write);var records=records(f,bid,write);ensureNoPending(f,bid,write);
            if(records.stream().anyMatch(row->"OPEN".equals(row.get("status"))))throw conflict("合并不接受加工中的在制，请先完成并验收");
            var prefix=completed(batch,records);if(prefix.isEmpty())throw conflict("合并须有已验收的完成阶段");
            if(first==null){first=batch;stage=prefix;}else if(n(first.get("order_id"))!=n(batch.get("order_id"))||n(first.get("order_item_id"))!=n(batch.get("order_item_id"))||!hash(JSON.parseObject(text(first.get("route_snapshot")))).equals(hash(JSON.parseObject(text(batch.get("route_snapshot")))))||!stage.equals(prefix))throw conflict("合并来源必须为同工单产品、冻结路线及完成阶段");
            var stock=materials.stockContext(f,bid);if(stock.isEmpty())throw conflict("合并来源没有已验收可用实物");var allocations=maps(input.getOrDefault("stockAllocations",List.of()));if(allocations.isEmpty())throw fail("请指定合并参与份额");
            var unique=new HashSet<Long>();BigDecimal selectedWeight=BigDecimal.ZERO,total=BigDecimal.ZERO;long selectedQ=0,totalQ=0;
            for(var sl:stock){ensureStage(sl,prefix,null);total=total.add(money(sl.get("netWeight")));totalQ+=n(sl.get("quantity"));}
            for(var a:allocations){selected(stock,a);long sid=id(a.get("stockSliceId"));if(!unique.add(sid))throw fail("同一来源份额不得重复");materials.validateStock(f,sid,a);selectedWeight=selectedWeight.add(positiveWeight(a));selectedQ+=q(a);}
            boolean full=selectedWeight.compareTo(total)==0&&selectedQ==totalQ;
            if("FULL_MERGE".equals(mode)&&!full)throw conflict("全量合并必须覆盖来源全部余额");
            Long plan=ProductionBatchService.planned(batch.get("planned_quantity")),part=plan;
            if(!full){if(!"PARTIAL_MERGE".equals(mode))throw conflict("部分参与须选择部分合并");part=ProductionBatchService.planned(input.get("plannedQuantityForMerge"));if(plan==null&&part!=null||plan!=null&&(part==null||part>=plan))throw conflict("部分合并须明确参与计划，不能按实际件数推算目标");
                var residual=new LinkedHashMap<String,Object>();residual.put("sourceBatchId",String.valueOf(bid));residual.put("plannedQuantity",plan==null?null:plan-part);residual.put("netWeight",total.subtract(selectedWeight).toPlainString());residual.put("quantity",totalQ-selectedQ);remains.add(residual);
            }else if(input.get("plannedQuantityForMerge")!=null&&!Objects.equals(part,ProductionBatchService.planned(input.get("plannedQuantityForMerge"))))throw conflict("全量参与须承接来源全部生产计划");
            if(part==null)hasNull=true;else mergedPlan=Math.addExact(mergedPlan,part);var entry=new LinkedHashMap<String,Object>();entry.put("batch",batch);entry.put("allocations",allocations);entry.put("full",full);entry.put("plan",part);entry.put("weight",selectedWeight);entry.put("quantity",selectedQ);sources.add(entry);
        }
        if(hasNull&&mergedPlan>0)throw conflict("有目标与无目标批次不能隐式混合生产计划");
        if(!write){var out=new LinkedHashMap<String,Object>();out.put("allowed",true);out.put("sources",inputs);out.put("completedStepIds",stage);out.put("plannedQuantity",hasNull?null:mergedPlan);out.put("remainingResults",remains);out.put("blockingReasons",List.of());return out;}
        var before=ProductionBatchReverseService.before(jdbc,f,inputs.stream().map(row->id(row.get("batchId"))).toList());before.put("materialBefore",materials.transformSnapshot(f,inputs.stream().map(row->id(row.get("batchId"))).toList()));long op=operation(f,request,"MERGE",fp,why),target=batches.createTransformed(f,first,hasNull?null:mergedPlan,"MERGE",op,stage);var results=new ArrayList<Map<String,Object>>();var contributions=new ArrayList<Map<String,Object>>();
        for(var entry:sources){var batch=(Map<String,Object>)entry.get("batch");long bid=n(batch.get("id"));Long plan=(Long)entry.get("plan");
            if(Boolean.TRUE.equals(entry.get("full"))){for(var a:maps(entry.get("allocations")))moveStock(f,op,id(a.get("stockSliceId")),target,a);edge(f,op,bid,target,plan,(BigDecimal)entry.get("weight"),n(entry.get("quantity")));}
            else{long participant=batches.createTransformed(f,batch,plan,"SPLIT",op,stage);for(var a:maps(entry.get("allocations")))moveStock(f,op,id(a.get("stockSliceId")),participant,a);edge(f,op,bid,participant,plan,(BigDecimal)entry.get("weight"),n(entry.get("quantity")));for(var sl:materials.stockContext(f,participant))moveStock(f,op,id(sl.get("stockSliceId")),target,whole(sl));edge(f,op,participant,target,plan,(BigDecimal)entry.get("weight"),n(entry.get("quantity")));exit(f,participant,"MERGED");var participation=identity(f,participant);participation.put("kind","PARTICIPANT");results.add(participation);}contributions.add(Map.of("sourceBatchId",String.valueOf(bid),"netWeight",entry.get("weight").toString(),"quantity",entry.get("quantity")));
            if(!Boolean.TRUE.equals(entry.get("full"))){Long original=ProductionBatchService.planned(batch.get("planned_quantity"));long residual=batches.createTransformed(f,batch,original==null?null:original-plan,"SPLIT",op,stage);BigDecimal weight=BigDecimal.ZERO;long qty=0;
                for(var sl:materials.stockContext(f,bid)){var a=whole(sl);moveStock(f,op,id(sl.get("stockSliceId")),residual,a);weight=weight.add(money(sl.get("netWeight")));qty+=n(sl.get("quantity"));}
                edge(f,op,bid,residual,original==null?null:original-plan,weight,qty);var r=identity(f,residual);r.put("sourceBatchId",String.valueOf(bid));r.put("kind","REMAINDER");results.add(r);exit(f,bid,"SPLIT");
            }else exit(f,bid,"MERGED");
        }
        var merged=identity(f,target);merged.put("kind","MERGED");merged.put("stockSlices",materials.stockContext(f,target));results.add(0,merged);var out=new LinkedHashMap<String,Object>();out.put("batchId",String.valueOf(target));out.put("targetBatchId",String.valueOf(target));out.put("results",results);out.put("sourceContributions",contributions);out.put("remainingResults",results.stream().filter(r->"REMAINDER".equals(r.get("kind"))).toList());out.put("reversalSnapshot",ProductionBatchReverseService.snapshot(jdbc,f,op,before));return finish(f,op,request,out);
    }

    private void validateStockDistribution(long f,List<Map<String,Object>> stock,List<Map<String,Object>> results){
        for(var sl:stock){
            var available=maps(sl.getOrDefault("sourceAllocations",List.of()));var weights=new LinkedHashMap<Long,BigDecimal>();var quantities=new LinkedHashMap<Long,Long>();
            for(var result:results)for(var a:maps(result.getOrDefault("stockAllocations",List.of())))if(id(a.get("stockSliceId"))==id(sl.get("stockSliceId"))){
                var parts=maps(a.getOrDefault("sourceAllocations",List.of()));
                if(parts.isEmpty()){
                    if(available.size()==1){var part=new LinkedHashMap<String,Object>(available.get(0));part.put("netWeight",a.get("netWeight"));part.put("quantity",a.get("quantity"));parts=List.of(part);}
                    else parts=available;
                }
                for(var part:parts){long source=id(part.get("sourceAllocationId"));weights.merge(source,positiveWeight(part),BigDecimal::add);quantities.merge(source,q(part),Math::addExact);}
            }
            for(var source:available){long id=id(source.get("sourceAllocationId"));if(money(source.get("netWeight")).compareTo(weights.getOrDefault(id,BigDecimal.ZERO))!=0||n(source.get("quantity"))!=quantities.getOrDefault(id,0L))throw conflict("拆分逐来源分配未守恒，不能重复分配同一来源");weights.remove(id);}
            if(!weights.isEmpty())throw conflict("拆分包含不属于原份额的来源");
        }
    }
    private long moveStock(long f,long operation,long slice,long target,Map<String,Object> allocation){
        var current=one(jdbc,"SELECT version FROM metal_storage_batch_slice WHERE factory_id=? AND id=? FOR UPDATE",f,slice);
        var actual=new LinkedHashMap<String,Object>(allocation);actual.put("sliceVersion",current.get("version"));
        return materials.transferStock(f,operation,slice,target,actual);
    }
    private long authorize(Map<String,Object> body){allow(scope,"metal:production-batch:transform");return scope.resolveFactory(optionalId(body.get("factoryId")));}
    private Map<String,Object> source(long f,long bid,int expected,boolean lock){var batch=one(jdbc,"SELECT * FROM metal_production_batch WHERE factory_id=? AND id=?"+(lock?" FOR UPDATE":""),f,bid);if(n(batch.get("version"))!=expected)throw conflict("批次版本已变化，请刷新核对");if(!"ACTIVE".equals(batch.get("lifecycle")))throw conflict("来源批次已退出流转");var order=one(jdbc,"SELECT status,deleted FROM metal_production_order WHERE factory_id=? AND id=?"+(lock?" FOR UPDATE":""),f,batch.get("order_id"));if(n(order.get("deleted"))!=0||!Set.of("CREATED","IN_PROGRESS").contains(text(order.get("status"))))throw conflict("工单已删除或完工");if(!hash(ProductionBatchService.routeSnapshot(jdbc,n(batch.get("order_item_id")))).equals(hash(JSON.parseObject(text(batch.get("route_snapshot"))))))throw conflict("冻结路线或产品已变化，请先核对来源批次");return batch;}
    private List<Map<String,Object>> records(long f,long bid,boolean lock){return jdbc.queryForList("SELECT e.*,s.measurement_mode,s.sequence_no FROM metal_production_execution e JOIN metal_production_order_step s ON s.id=e.order_step_id WHERE e.factory_id=? AND e.batch_id=? AND e.status<>'CANCELLED' ORDER BY s.sequence_no,e.id"+(lock?" FOR UPDATE":""),f,bid);}
    private void ensureNoPending(long f,long bid,boolean lock){if(!jdbc.queryForList("SELECT d.id FROM metal_storage_document d JOIN metal_production_execution e ON e.factory_id=d.factory_id AND e.id=d.execution_id WHERE e.factory_id=? AND e.batch_id=? AND d.deleted=0 AND d.status IN ('DRAFT','PENDING_REVIEW','RETURNED') ORDER BY d.id"+(lock?" FOR UPDATE":""),f,bid).isEmpty())throw conflict("本批有未处理单据，不能拆合");if(!jdbc.queryForList("SELECT r.id FROM metal_storage_reservation r JOIN metal_storage_reservation_session s ON s.factory_id=r.factory_id AND s.session_id=r.session_id LEFT JOIN metal_storage_batch_slice sl ON sl.factory_id=r.factory_id AND sl.stock_id=r.stock_id WHERE r.factory_id=? AND sl.batch_id=? AND (r.stock_slice_id=sl.id OR r.stock_slice_id IS NULL) AND (s.status='BOUND' OR (s.status='TEMP' AND s.expires_at>NOW())) ORDER BY r.id"+(lock?" FOR UPDATE":""),f,bid).isEmpty())throw conflict("来源库存有有效预占，须先释放或撤回");}
    static List<String> completed(Map<String,Object> batch,List<Map<String,Object>> records){var route=JSON.parseObject(text(batch.get("route_snapshot")));var steps=(List<?>)route.get("steps");var prefix=new ArrayList<String>();if(batch.get("completed_step_ids")!=null)for(Object id:JSON.parseArray(text(batch.get("completed_step_ids"))))prefix.add(text(id));for(int i=0;i<prefix.size();i++)if(i>=steps.size()||!prefix.get(i).equals(text(((Map<?,?>)steps.get(i)).get("id"))))throw conflict("继承阶段与冻结路线不符");for(var record:records){if("OPEN".equals(record.get("status")))break;if(!"CLOSED".equals(record.get("status")))throw conflict("批次包含已移交的异常活动记录");int i=prefix.size();if(i>=steps.size()||!text(record.get("order_step_id")).equals(text(((Map<?,?>)steps.get(i)).get("id"))))throw conflict("完成阶段与冻结路线顺序不符");prefix.add(text(record.get("order_step_id")));}return prefix;}
    private void ensureStage(Map<String,Object> sl,List<String> prefix,Map<String,Object> open){String expected=open==null?(prefix.isEmpty()?"":prefix.get(prefix.size()-1)):text(open.get("order_step_id"));if(!expected.equals(text(sl.get("originStepId"))))throw conflict("来源库存含不同完成阶段，不能隐式继承");}
    private Map<String,Object> selected(List<Map<String,Object>> stock,Map<String,Object> a){long sid=id(a.get("stockSliceId"));var sl=stock.stream().filter(row->id(row.get("stockSliceId"))==sid).findFirst().orElseThrow(()->conflict("份额不属于来源批次"));if(id(a.get("stockId"))!=id(sl.get("stockId"))||version(a.get("stockVersion"))!=n(sl.get("stockVersion"))||version(a.get("sliceVersion"))!=n(sl.get("sliceVersion")))throw conflict("库存或份额版本已变化");if(!"CONFIRMED_BALANCE".equals(a.get("allocationBasis")))throw fail("当前仅支持可核验的确认余额分配");positiveWeight(a);q(a);return sl;}
    private static Map<String,Object> whole(Map<String,Object> sl){var a=new LinkedHashMap<String,Object>(sl);a.put("allocationBasis","CONFIRMED_BALANCE");return a;}
    private void requireResultPlan(Long plan,List<Map<String,Object>> allocations,List<Map<String,Object>> stock){if(allocations.stream().anyMatch(a->"PIECE".equals(selected(stock,a).get("measurementMode"))))requirePlannedCapacity(plan,sumQuantity(allocations));}
    static void requirePlannedCapacity(Long plan,long actual){if(plan==null||actual>plan)throw conflict("计件结果须保留明确计划，实际件数不得超过承接目标");}
    private static void requirePlan(Map<String,Object> batch,long sum,boolean hasNull){Long source=ProductionBatchService.planned(batch.get("planned_quantity"));if(source==null?sum!=0:hasNull||sum!=source)throw conflict("所有结果计划须完整承接来源计划，不得新增或丢失目标");}
    private static List<String> append(List<String> prefix,String step){var result=new ArrayList<>(prefix);result.add(step);return result;}
    private static long sumQuantity(List<Map<String,Object>> a){long q=0;for(var row:a)q=Math.addExact(q,q(row));return q;}
    private static long q(Map<String,Object> a){return quantity(a.getOrDefault("quantity",0)).longValueExact();}
    private static BigDecimal positiveWeight(Map<String,Object> a){BigDecimal value=decimal(a.get("netWeight"),false);if(value.signum()<=0)throw fail("分配净重须大于零");return value;}
    private static BigDecimal money(Object value){return value==null?BigDecimal.ZERO:new BigDecimal(value.toString());}
    private static long n(Object value){return value==null?0:Long.parseLong(value.toString());}
    private static ServiceException conflict(String message){return new ServiceException(message,409);}
    private static String fingerprint(Map<String,Object> body){var normalized=new TreeMap<String,Object>(body);normalized.remove("requestId");return hash(normalized);}
    private long operation(long f,String req,String action,String fp,String why){return insert(jdbc,"INSERT INTO metal_production_execution_operation(factory_id,actor_id,request_id,action,request_hash,result,reason) VALUES(?,?,?,?,?,'{}',?)",f,SecurityUtils.getUserId(),req,action,fp,why);}
    private Map<String,Object> replay(long f,String request,String action,String fp){var rows=jdbc.queryForList("SELECT action,request_hash,CAST(result AS CHAR) result FROM metal_production_execution_operation WHERE factory_id=? AND actor_id=? AND request_id=? FOR UPDATE",f,SecurityUtils.getUserId(),request);if(rows.isEmpty())return null;var row=rows.get(0);if(!action.equals(row.get("action"))||!fp.equals(row.get("request_hash")))throw conflict("相同请求号的操作内容已变化，请核实原结果");return JSON.parseObject(text(row.get("result")));}
    private Map<String,Object> finish(long f,long op,String request,LinkedHashMap<String,Object> out){out.put("operationId",String.valueOf(op));out.put("requestId",request);out.put("committed",true);jdbc.update("UPDATE metal_production_execution_operation SET result=? WHERE factory_id=? AND id=?",JSON.toJSONString(out,JSONWriter.Feature.WriteMapNullValue),f,op);return out;}
    private Map<String,Object> identity(long f,long bid){return new LinkedHashMap<>(one(jdbc,"SELECT CAST(id AS CHAR) batchId,number,planned_quantity plannedQuantity,lifecycle,version FROM metal_production_batch WHERE factory_id=? AND id=?",f,bid));}
    private void exit(long f,long bid,String state){jdbc.update("UPDATE metal_production_batch SET lifecycle=?,version=version+1,update_time=NOW() WHERE factory_id=? AND id=?",state,f,bid);}
    private void edge(long f,long op,long source,long target,Long plan,BigDecimal weight,long quantity){
        var batch=one(jdbc,"SELECT CAST(route_snapshot AS CHAR) route,CAST(completed_step_ids AS CHAR) completed FROM metal_production_batch WHERE factory_id=? AND id=?",f,target);
        var prefix=JSON.parseArray(text(batch.get("completed")));var steps=JSON.parseObject(text(batch.get("route"))).getJSONArray("steps");
        String step=prefix==null||prefix.isEmpty()?steps.getJSONObject(0).getString("id"):text(prefix.get(prefix.size()-1));
        var current=jdbc.queryForList("SELECT order_step_id FROM metal_production_execution WHERE factory_id=? AND batch_id=? AND status='OPEN'",f,target);if(!current.isEmpty())step=text(current.get(0).get("order_step_id"));
        String mode=text(one(jdbc,"SELECT measurement_mode FROM metal_production_order_step WHERE id=?",id(step)).get("measurement_mode"));
        jdbc.update("INSERT INTO metal_production_batch_edge(factory_id,operation_id,source_batch_id,target_batch_id,planned_quantity_part,weight,quantity,measurement_mode,allocation_basis) VALUES(?,?,?,?,?,?,?,?, 'CONFIRMED_BALANCE')",f,op,source,target,plan,weight,"WEIGHT".equals(mode)?null:quantity,mode);
    }
}
