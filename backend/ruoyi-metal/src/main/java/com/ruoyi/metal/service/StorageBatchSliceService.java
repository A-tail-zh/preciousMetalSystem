package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.util.*;
import com.ruoyi.metal.security.FactoryScope;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.ruoyi.metal.service.StorageLedgerService.*;

/** Production identity partitions the existing stock/owner ledger; it never posts stock or debt itself. */
public final class StorageBatchSliceService {
    private final JdbcTemplate jdbc;
    private final StorageReservationService reservations;
    public StorageBatchSliceService(JdbcTemplate jdbc,FactoryScope scope,StorageReservationService reservations){this.jdbc=jdbc;this.reservations=reservations;}
    public StorageBatchSliceService(JdbcTemplate jdbc,FactoryScope scope){this(jdbc,scope,new StorageReservationService(jdbc,scope));}

    public Map<String,Object> resolve(long factory,long stock,Map<String,Object> input,boolean requireExplicit){
        Object supplied=input.get("stockSliceId");
        if(requireExplicit&&(supplied==null||text(supplied).isBlank()))throw fail("请选择具体生产批次库存份额");
        var rows=supplied==null||text(supplied).isBlank()
            ?jdbc.queryForList("SELECT * FROM metal_storage_batch_slice WHERE factory_id=? AND stock_id=? ORDER BY id FOR UPDATE",factory,stock)
            :jdbc.queryForList("SELECT * FROM metal_storage_batch_slice WHERE factory_id=? AND stock_id=? AND id=? FOR UPDATE",factory,stock,id(supplied));
        if(rows.size()!=1)throw fail("库存有多个批次份额或份额已失效，请重新选择具体份额");
        var slice=new LinkedHashMap<>(rows.get(0));
        if(input.get("sliceVersion")!=null&&id(input.get("sliceVersion"))!=id(slice.get("version")))throw fail("库存份额版本已变化，请刷新重新核对");
        if(requireExplicit&&input.get("sliceVersion")==null)throw fail("请提供库存份额版本");
        if(input.containsKey("sourceBatchId")&&!Objects.equals(nullableId(input.get("sourceBatchId")),nullableId(slice.get("batch_id"))))throw fail("库存份额与来源生产批次不一致");
        if(slice.get("batch_id")!=null){
            var batches=jdbc.queryForList("SELECT lifecycle FROM metal_production_batch WHERE factory_id=? AND id=? FOR UPDATE",factory,id(slice.get("batch_id")));
            if(batches.size()!=1||!Set.of("ACTIVE","COMPLETED").contains(text(batches.get(0).get("lifecycle"))))throw fail("来源生产批次已退出流转");
        }
        return slice;
    }
    private static Long nullableId(Object value){return value==null||text(value).isBlank()?null:id(value);}

    long createForReceipt(long event,long factory,long stock,Long batch){
        if(batch!=null){var rows=jdbc.queryForList("SELECT id FROM metal_production_batch WHERE factory_id=? AND id=? AND lifecycle='ACTIVE' FOR UPDATE",factory,batch);if(rows.size()!=1)throw fail("产出生产批次已失效或不属于本厂");}
        long slice=StorageSupport.insert(jdbc,"INSERT INTO metal_storage_batch_slice(factory_id,stock_id,batch_id,slice_kind) VALUES(?,?,?,?)",factory,stock,batch,batch==null?"UNASSIGNED":"BATCH");
        bind(event,factory,stock,slice);return slice;
    }
    void bind(long event,long factory,long stock,Map<String,Object> input){bind(event,factory,stock,id(resolve(factory,stock,input,false).get("id")));}
    void bind(long event,long factory,long stock,long slice){
        if(jdbc.queryForList("SELECT id FROM metal_storage_batch_slice WHERE factory_id=? AND stock_id=? AND id=? FOR UPDATE",factory,stock,slice).size()!=1)throw fail("库存份额不属于本次库存");
        if(jdbc.update("UPDATE metal_storage_event SET stock_slice_id=? WHERE factory_id=? AND id=?",slice,factory,event)!=1)throw fail("库存份额关联的过账事件不存在");
    }
    Long eventSlice(long event,long factory){
        var rows=jdbc.queryForList("SELECT stock_slice_id FROM metal_storage_event WHERE factory_id=? AND id=? FOR UPDATE",factory,event);
        if(rows.isEmpty())return null;
        return nullableId(rows.get(0).get("stock_slice_id"));
    }
    void change(long event,long factory,long stock,long slice,long owner,BigDecimal weight,long quantity,String reason){
        var rows=jdbc.queryForList("SELECT * FROM metal_storage_batch_slice WHERE factory_id=? AND stock_id=? AND id=? FOR UPDATE",factory,stock,slice);
        if(rows.size()!=1)throw fail("库存份额已失效");var current=rows.get(0);
        jdbc.update("INSERT IGNORE INTO metal_storage_batch_slice_owner(factory_id,stock_slice_id,owner_id) VALUES(?,?,?)",factory,slice,owner);
        var own=jdbc.queryForList("SELECT weight,quantity FROM metal_storage_batch_slice_owner WHERE factory_id=? AND stock_slice_id=? AND owner_id=? FOR UPDATE",factory,slice,owner).get(0);
        reservations.protectSlice(factory,stock,slice,null,dec(current.get("weight")),id(current.get("quantity")),weight,quantity);
        reservations.protectSlice(factory,stock,slice,owner,dec(own.get("weight")),id(own.get("quantity")),weight,quantity);
        if(dec(current.get("weight")).add(weight).signum()<0||id(current.get("quantity"))+quantity<0||dec(own.get("weight")).add(weight).signum()<0||id(own.get("quantity"))+quantity<0)throw fail("所选批次份额的实物或料权不足");
        if(!"REVERSAL".equals(reason))new ProductionBatchMaterialService(jdbc).consumeSlice(factory,event,slice,owner,weight,quantity,reason);
        jdbc.update("UPDATE metal_storage_batch_slice SET weight=weight+?,quantity=quantity+?,version=version+1,update_time=NOW() WHERE factory_id=? AND id=?",weight,quantity,factory,slice);
        jdbc.update("UPDATE metal_storage_batch_slice_owner SET weight=weight+?,quantity=quantity+? WHERE factory_id=? AND stock_slice_id=? AND owner_id=?",weight,quantity,factory,slice,owner);
        jdbc.update("INSERT INTO metal_storage_batch_slice_entry(event_id,factory_id,stock_id,stock_slice_id,owner_id,weight_delta,quantity_delta,reason) VALUES(?,?,?,?,?,?,?,?)",event,factory,stock,slice,owner,weight,quantity,reason);
    }
    void mirrorOwnership(long event,long factory,long stock,long owner,BigDecimal weight,long quantity,String reason){
        Long slice=eventSlice(event,factory);if(slice==null)throw fail("本次过账缺少明确库存批次份额");
        change(event,factory,stock,slice,owner,weight,quantity,reason);
    }
    void reconcile(long factory,long stock){
        var st=jdbc.queryForList("SELECT net_weight,quantity FROM metal_storage_stock WHERE factory_id=? AND id=? FOR UPDATE",factory,stock);
        if(st.size()!=1)throw fail("库存不存在");
        BigDecimal total=BigDecimal.ZERO;long count=0;var partition=new HashMap<Long,BigDecimal[]>();
        for(var slice:jdbc.queryForList("SELECT id,weight,quantity FROM metal_storage_batch_slice WHERE factory_id=? AND stock_id=? ORDER BY id FOR UPDATE",factory,stock)){
            BigDecimal sw=BigDecimal.ZERO;long sq=0;
            for(var owner:jdbc.queryForList("SELECT owner_id,weight,quantity FROM metal_storage_batch_slice_owner WHERE factory_id=? AND stock_slice_id=? ORDER BY owner_id FOR UPDATE",factory,id(slice.get("id")))){
                sw=sw.add(dec(owner.get("weight")));sq+=id(owner.get("quantity"));
                var part=partition.computeIfAbsent(id(owner.get("owner_id")),k->new BigDecimal[]{BigDecimal.ZERO,BigDecimal.ZERO});part[0]=part[0].add(dec(owner.get("weight")));part[1]=part[1].add(BigDecimal.valueOf(id(owner.get("quantity"))));
            }
            if(sw.compareTo(dec(slice.get("weight")))!=0||sq!=id(slice.get("quantity")))throw fail("批次份额与分区料权不一致，本次未过账");
            total=total.add(sw);count+=sq;
        }
        if(total.compareTo(dec(st.get(0).get("net_weight")))!=0||count!=id(st.get(0).get("quantity")))throw fail("批次份额与实物库存不一致，本次未过账");
        for(var owner:jdbc.queryForList("SELECT owner_id,weight,quantity FROM metal_storage_ownership WHERE factory_id=? AND stock_id=? ORDER BY owner_id FOR UPDATE",factory,stock)){
            var part=partition.remove(id(owner.get("owner_id")));if(part==null)part=new BigDecimal[]{BigDecimal.ZERO,BigDecimal.ZERO};
            if(part[0].compareTo(dec(owner.get("weight")))!=0||part[1].compareTo(BigDecimal.valueOf(id(owner.get("quantity"))))!=0)throw fail("批次料权分区与库存料权不一致，本次未过账");
        }
        if(partition.values().stream().anyMatch(p->p[0].signum()!=0||p[1].signum()!=0))throw fail("批次料权分区存在未匹配余额");
    }
    void reverse(long original,long reversal,long factory){
        var entries=jdbc.queryForList("SELECT * FROM metal_storage_batch_slice_entry WHERE factory_id=? AND event_id=? ORDER BY id DESC FOR UPDATE",factory,original);
        if(entries.isEmpty()){
            // Pre-V82 events have no partition entries; only a unique current partition can be restored safely.
            for(var old:jdbc.queryForList("SELECT * FROM metal_storage_entry WHERE factory_id=? AND event_id=? AND ledger='OWNERSHIP' ORDER BY id DESC",factory,original)){
                long stock=id(old.get("stock_id"));long slice=id(resolve(factory,stock,Map.of(),false).get("id"));
                change(reversal,factory,stock,slice,id(old.get("owner_id")),dec(old.get("weight_delta")).negate(),-Long.parseLong(text(old.get("quantity_delta"))),"REVERSAL");
            }
        }else for(var old:entries)change(reversal,factory,id(old.get("stock_id")),id(old.get("stock_slice_id")),id(old.get("owner_id")),dec(old.get("weight_delta")).negate(),-Long.parseLong(text(old.get("quantity_delta"))),"REVERSAL");
    }
    List<Map<String,Object>> options(long factory,long stock,String excluded){
        var result=new ArrayList<Map<String,Object>>();
        for(var row:jdbc.queryForList("SELECT * FROM metal_storage_batch_slice WHERE factory_id=? AND stock_id=? AND (weight>0 OR quantity>0) ORDER BY id",factory,stock)){
            var out=api(row);out.put("stockSliceId",text(row.get("id")));out.put("sourceBatchId",row.get("batch_id")==null?null:text(row.get("batch_id")));out.put("sliceVersion",row.get("version"));out.put("balanceWeight",out.get("weight"));out.put("balanceQuantity",row.get("quantity"));
            reservations.sliceAvailability(out,factory,stock,id(row.get("id")),null,excluded,"weight");
            var owners=new ArrayList<Map<String,Object>>();
            for(var owner:jdbc.queryForList("SELECT * FROM metal_storage_batch_slice_owner WHERE factory_id=? AND stock_slice_id=? AND (weight>0 OR quantity>0) ORDER BY owner_id",factory,id(row.get("id")))){
                var own=api(owner);reservations.sliceAvailability(own,factory,stock,id(row.get("id")),id(owner.get("owner_id")),excluded,"weight");owners.add(own);
            }
            out.put("owners",owners);result.add(out);
        }
        return result;
    }
}


