package com.ruoyi.metal.service;

import java.math.*;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.metal.security.FactoryScope;

/** Read-only weight balance, scoped to a saved work-order item and its process snapshots. */
@Service
@Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
public class ProcessConsumptionService {
    private final JdbcTemplate jdbc;
    private final FactoryScope scope;
    public ProcessConsumptionService(JdbcTemplate jdbc,FactoryScope scope){this.jdbc=jdbc;this.scope=scope;}
    private static BigDecimal dec(Object v){return v==null?BigDecimal.ZERO:new BigDecimal(v.toString());}
    private static long count(Object v){return v==null?0:new BigDecimal(v.toString()).longValueExact();}
    private static String weight(Object v){return dec(v).setScale(6,RoundingMode.HALF_UP).toPlainString();}
    private static boolean yes(Object v){return Boolean.TRUE.equals(v)||"1".equals(String.valueOf(v));}
    private Map<String,Object> one(String sql,Object... args){var rows=jdbc.queryForList(sql,args);if(rows.size()!=1)throw new ServiceException("查询对象不存在或不属于当前生产上下文");return rows.get(0);}
    private void context(Long work,Long item,Long process,Long batch,Long order){
        var parent=one("SELECT factory_id FROM metal_work_order WHERE work_order_id=? AND del_flag='0'",work);
        scope.check(count(parent.get("factory_id")));
        one("SELECT item_id FROM metal_work_order_item WHERE item_id=? AND work_order_id=?",item,work);
        if(process!=null)one("SELECT step_id FROM metal_work_order_step WHERE item_id=? AND process_id=?",item,process);
        if(batch!=null)one("SELECT batch_id FROM metal_process_batch WHERE batch_id=? AND work_order_id=? AND item_id=? AND process_id=?",batch,work,item,process);
        if(order!=null)one("SELECT order_id FROM metal_warehouse_order WHERE order_id=? AND batch_id=? AND factory_id=?",order,batch,parent.get("factory_id"));
    }
    private static Map<String,Object> clean(Map<String,Object> input){
        var out=new LinkedHashMap<String,Object>();
        input.forEach((key,value)->out.put(key,value==null?null:value instanceof BigDecimal?(key.toLowerCase(Locale.ROOT).endsWith("quantity")?count(value):weight(value)):key.endsWith("Id")?String.valueOf(value):value));return out;
    }
    private static List<Map<String,Object>> cleanRows(List<Map<String,Object>> rows){return rows.stream().map(ProcessConsumptionService::clean).toList();}
    private static Map<String,Object> result(Long work,Long item,List<Map<String,Object>> rows){var out=new LinkedHashMap<String,Object>();out.put("workOrderId",String.valueOf(work));out.put("itemId",String.valueOf(item));out.put("readAt",LocalDateTime.now().toString());out.put("rows",rows);return out;}
    private static void page(int n,int size){if(n<1||size<1||size>100)throw new ServiceException("分页参数不合法（每页1至100条）");}
    private static Map<String,Object> paged(Long work,Long item,List<Map<String,Object>> rows,long total,int n,int size){var out=result(work,item,rows);out.put("total",total);out.put("pageNum",n);out.put("pageSize",size);return out;}
    // Aggregate lines before joining batches and the UNIQUE(batch_id) report, so neither join multiplies weights.
    private static final String BATCH_SQL="""
        SELECT b.batch_id batchId,b.batch_no batchNo,b.item_id itemId,b.process_id processId,b.sequence_no sequenceNo,
        b.status,b.version batchVersion,b.planned_quantity plannedQuantity,b.completed_quantity batchCompletedQuantity,
        COALESCE(x.rawIssuedWeight,0) rawIssuedWeight,COALESCE(x.semiIssuedWeight,0) semiIssuedWeight,
        COALESCE(x.rawReturnedWeight,0) rawReturnedWeight,COALESCE(x.outputWeight,0) outputWeight,
        COALESCE(x.outputQuantity,0) outputQuantity,COALESCE(x.documentCount,0) documentCount,
        r.report_id reportId,r.issued_weight reportIssuedWeight,r.returned_weight reportReturnedWeight,
        r.loss_weight reportLossWeight,r.average_loss reportAverageLoss,r.completed_quantity reportCompletedQuantity,
        r.status reportStatus,r.voided reportVoided,r.create_time settledTime
        FROM metal_process_batch b LEFT JOIN (
          SELECT o.batch_id,
          SUM(CASE WHEN o.type='PRODUCTION_OUT' AND l.category='RAW' THEN l.net_weight ELSE 0 END) rawIssuedWeight,
          SUM(CASE WHEN o.type='PRODUCTION_OUT' AND l.category='SEMI' THEN l.net_weight ELSE 0 END) semiIssuedWeight,
          SUM(CASE WHEN o.type='RAW_RETURN' THEN l.net_weight ELSE 0 END) rawReturnedWeight,
          SUM(CASE WHEN o.type IN ('SEMI_IN','FINISHED_IN') THEN l.net_weight ELSE 0 END) outputWeight,
          SUM(CASE WHEN o.type IN ('SEMI_IN','FINISHED_IN') THEN l.quantity ELSE 0 END) outputQuantity,
          COUNT(DISTINCT o.order_id) documentCount
          FROM metal_warehouse_order o JOIN metal_process_batch sourceBatch ON sourceBatch.batch_id=o.batch_id
          JOIN metal_warehouse_order_line l ON l.order_id=o.order_id
          WHERE o.status='POSTED' AND sourceBatch.work_order_id=? AND sourceBatch.item_id=? GROUP BY o.batch_id
        ) x ON x.batch_id=b.batch_id LEFT JOIN metal_work_report r ON r.batch_id=b.batch_id
        WHERE b.work_order_id=? AND b.item_id=?
        """;
    private static String batchScope(String value){if(value==null)return "ALL";if(!Set.of("ALL","SETTLED").contains(value))throw new ServiceException("批次范围仅支持ALL或SETTLED");return value;}
    public Map<String,Object> steps(Long work,Long item){return steps(work,item,null,"ALL");}
    public Map<String,Object> steps(Long work,Long item,Long process,String requestedScope){
        String selectedScope=batchScope(requestedScope);
        context(work,item,process,null,null);
        var steps=cleanRows(jdbc.queryForList("SELECT process_id processId,sequence_no sequenceNo,process_name processName FROM metal_work_order_step WHERE item_id=?"+(process==null?"":" AND process_id=?")+" ORDER BY sequence_no,step_id",process==null?new Object[]{item}:new Object[]{item,process}));
        return stepsResult(work,item,steps,process,selectedScope);
    }
    private Map<String,Object> stepsResult(Long work,Long item,List<Map<String,Object>> steps,Long process,String selectedScope){
        var grouped=new LinkedHashMap<String,List<Map<String,Object>>>();
        String filter=(process==null?"":" AND b.process_id=?")+("SETTLED".equals(selectedScope)?" AND b.status='SETTLED'":"");
        Object[] args=process==null?new Object[]{work,item,work,item}:new Object[]{work,item,work,item,process};
        for(var raw:jdbc.queryForList(BATCH_SQL+filter,args)){var b=calculateBatch(clean(raw));grouped.computeIfAbsent(String.valueOf(b.get("processId")),k->new ArrayList<>()).add(b);}
        var rows=new ArrayList<Map<String,Object>>();
        for(var step:steps){var row=new LinkedHashMap<>(step);row.putAll(summarize(grouped.getOrDefault(String.valueOf(step.get("processId")),List.of())));rows.add(row);grouped.remove(String.valueOf(step.get("processId")));}
        var out=result(work,item,rows);out.put("batchScope",selectedScope);if(process!=null)out.put("processId",String.valueOf(process));out.put("warnings",grouped.isEmpty()?List.of():List.of("存在缺少工序快照的历史批次，请人工核对；未推测其工序归属"));return out;
    }
    public Map<String,Object> batches(Long work,Long item,Long process,boolean voided,int n,int size){return batches(work,item,process,voided,n,size,"ALL");}
    public Map<String,Object> batches(Long work,Long item,Long process,boolean voided,int n,int size,String requestedScope){
        String selectedScope=batchScope(requestedScope);
        page(n,size);context(work,item,process,null,null);
        String filter=" AND b.process_id=?"+("SETTLED".equals(selectedScope)?" AND b.status='SETTLED'":voided?"":" AND b.status<>'VOID'");
        Long total=jdbc.queryForObject("SELECT COUNT(*) FROM metal_process_batch b WHERE b.work_order_id=? AND b.item_id=?"+filter,Long.class,work,item,process);
        var rows=jdbc.queryForList(BATCH_SQL+filter+" ORDER BY b.batch_id DESC LIMIT ? OFFSET ?",work,item,work,item,process,size,((long)n-1)*size).stream().map(ProcessConsumptionService::clean).map(ProcessConsumptionService::calculateBatch).toList();
        var out=paged(work,item,rows,total==null?0:total,n,size);out.put("processId",String.valueOf(process));out.put("batchScope",selectedScope);return out;
    }
    public Map<String,Object> orders(Long work,Long item,Long process,Long batch,int n,int size){
        page(n,size);context(work,item,process,batch,null);
        Long total=jdbc.queryForObject("SELECT COUNT(*) FROM metal_warehouse_order WHERE batch_id=?",Long.class,batch);
        var rows=cleanRows(jdbc.queryForList("SELECT o.order_id orderId,o.order_no orderNo,o.order_date orderDate,o.type,o.status,o.operator_name operatorName,o.warehouse_name warehouseName,COALESCE(SUM(l.net_weight),0) netWeight,COALESCE(SUM(l.quantity),0) quantity FROM metal_warehouse_order o LEFT JOIN metal_warehouse_order_line l ON l.order_id=o.order_id WHERE o.batch_id=? GROUP BY o.order_id ORDER BY o.order_id LIMIT ? OFFSET ?",batch,size,((long)n-1)*size));
        rows.forEach(r->r.put("included","POSTED".equals(r.get("status"))));
        var out=paged(work,item,rows,total==null?0:total,n,size);out.put("batchId",String.valueOf(batch));out.put("processId",String.valueOf(process));return out;
    }
    public Map<String,Object> lines(Long work,Long item,Long process,Long batch,Long order,int n,int size){
        page(n,size);context(work,item,process,batch,order);
        Long total=jdbc.queryForObject("SELECT COUNT(*) FROM metal_warehouse_order_line WHERE order_id=?",Long.class,order);
        var rows=cleanRows(jdbc.queryForList("SELECT l.line_id lineId,l.inventory_id inventoryId,l.sequence_no sequenceNo,l.code,l.name,l.category,l.specification,l.batch_no batchNo,l.gross_weight grossWeight,l.impurity,l.net_weight netWeight,l.quantity,l.organization,l.purpose,l.remark,source.order_no sourceOrderNo FROM metal_warehouse_order_line l LEFT JOIN metal_warehouse_order_line original ON original.line_id=l.source_line_id LEFT JOIN metal_warehouse_order source ON source.order_id=original.order_id WHERE l.order_id=? ORDER BY l.sequence_no LIMIT ? OFFSET ?",order,size,((long)n-1)*size));
        var out=paged(work,item,rows,total==null?0:total,n,size);out.put("orderId",String.valueOf(order));out.put("batchId",String.valueOf(batch));out.put("processId",String.valueOf(process));return out;
    }
    static Map<String,Object> calculateBatch(Map<String,Object> b){
        BigDecimal raw=dec(b.get("rawIssuedWeight")),semi=dec(b.get("semiIssuedWeight")),returned=dec(b.get("rawReturnedWeight")),output=dec(b.get("outputWeight"));
        BigDecimal issued=raw.add(semi),net=issued.subtract(returned),difference=net.subtract(output);
        for(String key:List.of("rawIssuedWeight","semiIssuedWeight","rawReturnedWeight","outputWeight"))b.put(key,weight(b.get(key)));
        b.put("issuedWeight",weight(issued));b.put("netInputWeight",weight(net));b.put("balanceDifferenceWeight",weight(difference));
        b.put("settledLossWeight",null);b.put("provisionalDifferenceWeight",null);b.put("settledCompletedQuantity",null);b.put("averageLoss",null);
        String state=String.valueOf(b.get("status"));
        var warnings=new ArrayList<String>();
        if("OPEN".equals(state))b.put("provisionalDifferenceWeight",weight(difference));
        if("SETTLED".equals(state)){
            if(b.get("reportId")==null||yes(b.get("reportVoided"))){state="REVIEW";warnings.add("已结算批次缺少有效报工");}
            else{
                b.put("settledLossWeight",b.get("reportLossWeight")==null?null:weight(b.get("reportLossWeight")));b.put("settledCompletedQuantity",b.get("reportCompletedQuantity")==null?null:count(b.get("reportCompletedQuantity")));b.put("averageLoss",b.get("reportAverageLoss")==null?null:weight(b.get("reportAverageLoss")));
                if(b.get("reportLossWeight")==null||b.get("reportIssuedWeight")==null||b.get("reportReturnedWeight")==null||b.get("reportCompletedQuantity")==null||b.get("reportAverageLoss")==null||(count(b.get("reportCompletedQuantity"))>0&&dec(b.get("reportAverageLoss")).compareTo(dec(b.get("reportLossWeight")).divide(BigDecimal.valueOf(count(b.get("reportCompletedQuantity"))),6,RoundingMode.HALF_UP))!=0)||dec(b.get("reportLossWeight")).compareTo(difference)!=0||dec(b.get("reportIssuedWeight")).compareTo(issued)!=0||dec(b.get("reportReturnedWeight")).compareTo(returned.add(output))!=0||count(b.get("reportCompletedQuantity"))!=count(b.get("outputQuantity"))||count(b.get("reportCompletedQuantity"))<=0){state="REVIEW";warnings.add("报工快照与当前有效单据不一致，请人工核对");}
            }
        }
        if(!"VOID".equals(state)&&(difference.signum()<0||net.signum()<0)){state="REVIEW";warnings.add("历史重量异常，请人工核对");}
        if("SETTLED".equals(b.get("status"))&&count(b.get("documentCount"))==0){state="REVIEW";warnings.add("缺少有效单据依据");}
        b.put("state",state);b.put("warnings",warnings);return b;
    }
    static Map<String,Object> summarize(List<Map<String,Object>> batches){
        var out=new LinkedHashMap<String,Object>();
        var active=batches.stream().filter(b->!"VOID".equals(b.get("status"))).toList();
        for(String key:List.of("rawIssuedWeight","semiIssuedWeight","issuedWeight","rawReturnedWeight","outputWeight","netInputWeight"))out.put(key,weight(active.stream().map(b->dec(b.get(key))).reduce(BigDecimal.ZERO,BigDecimal::add)));
        long open=active.stream().filter(b->"OPEN".equals(b.get("status"))).count(),settled=active.stream().filter(b->"SETTLED".equals(b.get("status"))).count();
        boolean missing=active.stream().anyMatch(b->"SETTLED".equals(b.get("status"))&&(b.get("settledLossWeight")==null||b.get("settledCompletedQuantity")==null));
        BigDecimal loss=active.stream().map(b->dec(b.get("settledLossWeight"))).reduce(BigDecimal.ZERO,BigDecimal::add);
        long completed=active.stream().mapToLong(b->count(b.get("settledCompletedQuantity"))).sum();
        out.put("reportStatus",settled==0||missing?null:active.stream().anyMatch(b->"SETTLED".equals(b.get("status"))&&"ABNORMAL".equals(b.get("reportStatus")))?"ABNORMAL":"NORMAL");
        out.put("batchCount",active.size());out.put("voidBatchCount",batches.size()-active.size());out.put("openBatchCount",open);out.put("settledBatchCount",settled);
        out.put("outputQuantity",active.stream().mapToLong(b->count(b.get("outputQuantity"))).sum());out.put("settledCompletedQuantity",missing?null:completed);
        out.put("settledLossWeight",missing?null:weight(loss));out.put("provisionalDifferenceWeight",weight(active.stream().map(b->dec(b.get("provisionalDifferenceWeight"))).reduce(BigDecimal.ZERO,BigDecimal::add)));
        out.put("averageLoss",!missing&&completed>0?loss.divide(BigDecimal.valueOf(completed),6,RoundingMode.HALF_UP).toPlainString():null);
        out.put("state",active.stream().anyMatch(b->"REVIEW".equals(b.get("state")))?"REVIEW":active.isEmpty()?(batches.isEmpty()?"NOT_STARTED":"NO_ACTIVE"):open>0?(settled>0?"PARTIAL":"OPEN"):"SETTLED");
        out.put("warnings",active.stream().flatMap(b->((List<?>)b.getOrDefault("warnings",List.of())).stream()).distinct().toList());return out;
    }
}
