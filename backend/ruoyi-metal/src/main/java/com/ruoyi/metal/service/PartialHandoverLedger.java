package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.util.*;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.metal.catalog.WarehouseFlowRequests.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Called under the factory and batch locks in the enclosing warehouse transaction. */
final class PartialHandoverLedger {
    private final JdbcTemplate jdbc;
    PartialHandoverLedger(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    static long n(Object v) { return v==null?0:Long.parseLong(v.toString()); }
    static BigDecimal d(Object v) { return v==null?BigDecimal.ZERO:new BigDecimal(v.toString()); }
    static boolean partial(Map<String,Object> batch) { return n(batch.get("handover_policy_version"))>=2
        && (!batch.containsKey("completion_policy")||WarehouseFlowRules.POLICY.equals(batch.get("completion_policy"))); }
    boolean convertible(Map<String,Object> batch) {
        long id=n(batch.get("batch_id"));
        return "OPEN".equals(batch.get("status")) && n(batch.get("metal_type_id"))>0
            && jdbc.queryForObject("SELECT COUNT(*) FROM metal_warehouse_order WHERE batch_id=? AND type='PRODUCTION_OUT' AND status='POSTED'",Long.class,id)>0
            && jdbc.queryForObject("SELECT COUNT(*) FROM metal_warehouse_flow WHERE batch_id=? AND kind='HANDOVER' AND status NOT IN ('VOID','CANCELLED')",Long.class,id)==0
            && jdbc.queryForObject("SELECT COUNT(*) FROM metal_work_report WHERE batch_id=?",Long.class,id)==0
            && jdbc.queryForObject("SELECT COUNT(*) FROM metal_batch_return_confirmation WHERE batch_id=? AND status='ACTIVE'",Long.class,id)==0
            && jdbc.queryForObject("SELECT COUNT(*) FROM metal_warehouse_order WHERE batch_id=? AND status='POSTED' AND type<>'PRODUCTION_OUT'",Long.class,id)==0
            && jdbc.queryForObject("SELECT COUNT(*) FROM metal_warehouse_order WHERE batch_id=? AND type='PRODUCTION_OUT' AND status='POSTED' AND (recipient_id IS NULL OR confirmed_by IS NULL OR recipient_id<>confirmed_by)",Long.class,id)==0;
    }
    void upgrade(Map<String,Object> batch) {
        if (partial(batch)) return;
        if (!convertible(batch)) throw new ServiceException("此历史批次已有交接、报工或来源异常，须先按原流程处理，不能自动转换");
        jdbc.update("UPDATE metal_process_batch SET handover_policy_version=2,completion_policy=? WHERE batch_id=?",WarehouseFlowRules.POLICY,batch.get("batch_id"));
        batch.put("handover_policy_version",2);
        batch.put("completion_policy",WarehouseFlowRules.POLICY);
    }
    private String key(Map<String,Object> source,Map<String,Object> batch) {
        return n(source.get("recipient_id"))+":"+n(batch.get("metal_type_id"))+":"+MaterialOwnership.of(source)+":"+n(source.get("customer_id"))+":"+InventoryMeasurement.mode(source);
    }
    List<Map<String,Object>> balances(Map<String,Object> batch,List<Map<String,Object>> sources,long actor,boolean initialize) {
        var saved=new HashMap<Long,Map<String,Object>>();
        for(var row:jdbc.queryForList("SELECT * FROM metal_batch_wip_balance WHERE batch_id=? ORDER BY source_line_id",batch.get("batch_id"))) saved.put(n(row.get("source_line_id")),row);
        var result=new ArrayList<Map<String,Object>>();
        for(var source:sources) {
            if(n(source.get("recipient_id"))!=actor || n(source.get("confirmed_by"))!=actor)continue;
            BusinessDecimal.requireCompatibleBalance(d(source.get("net_weight")),"原领料明细#"+source.get("line_id"));
            String owner=MaterialOwnership.of(source);
            if(!Set.of("CUSTOMER","FACTORY").contains(owner)||"CUSTOMER".equals(owner)&&n(source.get("customer_id"))==0)
                throw new ServiceException("原领料明细#"+source.get("line_id")+"的材料归属或客户无法核对，请先处理历史来源，不能自动转换");
            if(n(source.get("input_metal_type_id"))!=n(batch.get("metal_type_id")))
                throw new ServiceException("原领料明细#"+source.get("line_id")+"的金属与加工记录不一致或缺少依据，不能自动转换");
            String group=key(source,batch);var row=saved.get(n(source.get("line_id")));
            if(row==null && initialize) jdbc.update("INSERT INTO metal_batch_wip_balance(source_line_id,batch_id,actor_id,group_key,remaining_weight) VALUES(?,?,?,?,?)",source.get("line_id"),batch.get("batch_id"),actor,group,source.get("net_weight"));
            var value=new LinkedHashMap<String,Object>(source);
            value.put("group_key",group);value.put("remaining_weight",row==null?d(source.get("net_weight")):d(row.get("remaining_weight")));
            result.add(value);
        }
        return result;
    }
    List<Map<String,Object>> groups(List<Map<String,Object>> balances) {
        var result=new LinkedHashMap<String,Map<String,Object>>();
        for(var row:balances) {
            String key=row.get("group_key").toString();
            var group=result.computeIfAbsent(key,k->{var r=new LinkedHashMap<String,Object>();r.put("groupKey",k);r.put("materialOwnership",MaterialOwnership.of(row));r.put("customerId",row.get("customer_id"));r.put("measurementMode",InventoryMeasurement.mode(row));r.put("netWeight",BigDecimal.ZERO);r.put("sourceLineIds",new ArrayList<String>());return r;});
            group.put("netWeight",d(group.get("netWeight")).add(d(row.get("remaining_weight"))));
            ((List<String>)group.get("sourceLineIds")).add(row.get("line_id").toString());
        }
        return new ArrayList<>(result.values());
    }
    void allocate(long flow,Map<String,Object> batch,List<Map<String,Object>> sources,long actor,Handover input,BigDecimal delivered) {
        if(input.wipBalances()==null)throw new ServiceException("请称量并填写本次交接后的剩余在制净重");
        var balances=balances(batch,sources,actor,true);var declared=new HashMap<String,BigDecimal>();
        int revision=jdbc.queryForObject("SELECT revision+1 FROM metal_warehouse_flow WHERE flow_id=?",Integer.class,flow);
        for(var w:input.wipBalances())if(declared.put(w.groupKey(),w.remainingWeight())!=null)throw new ServiceException("在制分组不能重复");
        var grouped=new LinkedHashMap<String,List<Map<String,Object>>>();
        for(var row:balances)grouped.computeIfAbsent(row.get("group_key").toString(),k->new ArrayList<>()).add(row);
        if(!declared.keySet().equals(grouped.keySet()))throw new ServiceException("在制来源或归属已变化，请重新加载并逐组核对");
        var returned=new HashMap<Long,BigDecimal>();
        for(var doc:input.documents())if(Set.of("RAW_RETURN","SEMI_RETURN").contains(doc.type()))for(var line:doc.lines())returned.merge(line.sourceLineId(),line.netWeight(),BigDecimal::add);
        BigDecimal before=BigDecimal.ZERO,after=BigDecimal.ZERO;
        for(var entry:grouped.entrySet()) {
            var rows=entry.getValue();var weights=rows.stream().map(r->d(r.get("remaining_weight")).subtract(returned.getOrDefault(n(r.get("line_id")),BigDecimal.ZERO))).toList();
            if(weights.stream().anyMatch(w->w.signum()<0))throw new ServiceException("实际还料超过对应原领料明细的当前在制余量");
            var remaining=declared.get(entry.getKey());
            var shares=PartialHandoverRules.proportion(remaining,weights);
            for(int i=0;i<rows.size();i++) {
                var row=rows.get(i);BigDecimal start=d(row.get("remaining_weight")),end=shares.get(i);
                before=before.add(start);after=after.add(end);
                jdbc.update("INSERT INTO metal_handover_allocation(flow_id,allocation_version,source_line_id,before_weight,allocated_weight,after_weight,returned_weight) VALUES(?,?,?,?,?,?,?)",flow,revision,row.get("line_id"),start,start.subtract(end),end,returned.getOrDefault(n(row.get("line_id")),BigDecimal.ZERO));
                jdbc.update("UPDATE metal_batch_wip_balance SET remaining_weight=?,version=version+1 WHERE source_line_id=?",end,row.get("line_id"));
            }
        }
        if(PartialHandoverRules.consumed(before,after,delivered).signum()==0)throw new ServiceException("本次交接须实际消耗在制物料");
        var consumedByOwner=new HashMap<String,BigDecimal>();
        for(var row:jdbc.queryForList("SELECT w.group_key,SUM(a.allocated_weight-a.returned_weight) weight FROM metal_handover_allocation a JOIN metal_batch_wip_balance w ON w.source_line_id=a.source_line_id WHERE a.flow_id=? AND a.voided=FALSE GROUP BY w.group_key",flow)){
            String owner=row.get("group_key").toString().split(":")[2];consumedByOwner.merge(owner,d(row.get("weight")),BigDecimal::add);
        }
        var recovery=new HashMap<String,BigDecimal>();
        for(var doc:input.documents())if("PROCESS_RAW_RETURN".equals(doc.type()))for(var line:doc.lines())recovery.merge(line.materialOwnership(),line.netWeight(),BigDecimal::add);
        for(var e:recovery.entrySet())if(e.getValue().compareTo(consumedByOwner.getOrDefault(e.getKey(),BigDecimal.ZERO))>0)throw new ServiceException("本次加工回收余料超过该归属实际分配的投入，不能借用其他客户或厂料在制余额");
        if(!SemiMeasurementRules.weight(batch)&&balances.stream().allMatch(s->!InventoryMeasurement.weight(s))) {
            long issuedPieces=balances.stream().mapToLong(s->n(s.get("quantity"))).sum();
            long claimed=jdbc.queryForObject("SELECT COALESCE(SUM(CAST(JSON_UNQUOTE(JSON_EXTRACT(l.line_json,'$.quantity')) AS UNSIGNED)),0) FROM metal_warehouse_flow f JOIN metal_warehouse_flow_group g ON g.flow_id=f.flow_id AND g.status<>'SUPERSEDED' JOIN metal_warehouse_flow_line l ON l.group_id=g.group_id WHERE f.batch_id=? AND f.kind='HANDOVER' AND f.request_actor=? AND f.flow_id<>? AND f.status NOT IN ('VOID','CANCELLED') AND g.type IN ('SEMI_IN','FINISHED_IN','RAW_PROCESS_IN','RAW_RETURN','SEMI_RETURN')",Long.class,batch.get("batch_id"),actor,flow);
            long current=input.documents().stream().filter(doc->!"PROCESS_RAW_RETURN".equals(doc.type())).flatMap(doc->doc.lines().stream()).mapToLong(line->line.quantity()==null?0:line.quantity()).sum();
            if(claimed+current>issuedPieces)throw new ServiceException("累计交接件数超过本人实际领用件数");
        }
        jdbc.update("UPDATE metal_process_batch SET version=version+1 WHERE batch_id=?",batch.get("batch_id"));
    }
    void requireLatest(long flow) {
        if(jdbc.queryForObject("SELECT COUNT(*) FROM metal_handover_allocation a JOIN metal_handover_allocation later ON later.source_line_id=a.source_line_id AND later.flow_id>a.flow_id AND later.voided=FALSE WHERE a.flow_id=? AND a.voided=FALSE",Long.class,flow)>0)
            throw new ServiceException("后续交接已承接在制物料，请先按逆序撤回后续交接");
    }
    void release(long flow) {
        requireLatest(flow);
        for(var a:jdbc.queryForList("SELECT * FROM metal_handover_allocation WHERE flow_id=? AND voided=FALSE ORDER BY source_line_id FOR UPDATE",flow))
            jdbc.update("UPDATE metal_batch_wip_balance SET remaining_weight=remaining_weight+?,version=version+1 WHERE source_line_id=?",a.get("allocated_weight"),a.get("source_line_id"));
        jdbc.update("UPDATE metal_handover_allocation SET voided=TRUE WHERE flow_id=?",flow);
    }
}
