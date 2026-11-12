package com.ruoyi.metal.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import java.nio.file.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import static com.ruoyi.metal.service.StorageSupport.*;

/** Disposable rehearsal only. No business ledger is synthesized from historical totals. */
public class ProductionBatchP5HistoryMigration {
    static String json(Object o){return JSON.toJSONString(o,JSONWriter.Feature.WriteMapNullValue,JSONWriter.Feature.MapSortField);}
    static void require(boolean b,String why){if(!b)throw new IllegalStateException(why);}
    static long n(Object o){return o==null?0:o instanceof Boolean b?(b?1:0):Long.parseLong(o.toString());}
    static Map<String,Object> scan(JdbcTemplate j,long factory){
        var candidates=new ArrayList<Object>();var ambiguous=new ArrayList<Object>();
        var columns=j.queryForList("SELECT TABLE_NAME t,COLUMN_NAME c FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name LIKE 'metal_%' AND (column_name LIKE '%execution_id' OR column_name IN ('order_id','order_item_id','work_order_id','origin_order_id','origin_step_id')) ORDER BY table_name,column_name");
        var batches=j.queryForList("SELECT * FROM metal_production_batch WHERE factory_id=? ORDER BY id",factory);
        var orders=j.queryForList("SELECT * FROM metal_production_order WHERE factory_id=? ORDER BY id",factory);
        var items=j.queryForList("SELECT i.* FROM metal_production_order_item i JOIN metal_production_order o ON o.id=i.order_id WHERE o.factory_id=? ORDER BY i.id",factory);
        var steps=j.queryForList("SELECT s.* FROM metal_production_order_step s JOIN metal_production_order_item i ON i.id=s.item_id JOIN metal_production_order o ON o.id=i.order_id WHERE o.factory_id=? ORDER BY s.item_id,s.sequence_no,s.id",factory);
        var executions=j.queryForList("SELECT * FROM metal_production_execution WHERE factory_id=? AND batch_id IS NULL ORDER BY id",factory);
        var allocated=new HashMap<Long,Long>();
        for(var b:batches)if(!Set.of("SPLIT","MERGED","CANCELLED").contains(b.get("lifecycle")))allocated.merge(n(b.get("order_item_id")),n(b.get("planned_quantity")),Math::addExact);
        for(var e:executions)if(!"CANCELLED".equals(e.get("status")))allocated.merge(n(e.get("order_item_id")),n(e.get("planned_quantity")),Math::addExact);
        for(var e:executions){
            var reasons=new ArrayList<String>();var refs=new TreeMap<String,Object>();
            boolean cancelled="CANCELLED".equals(e.get("status"));
            if(!cancelled&&!"OPEN".equals(e.get("status")))reasons.add("STATUS_REQUIRES_MANUAL_REVIEW");
            for(var entry:e.entrySet())if(entry.getKey().matches(".*(weight|quantity|amount|cost|price)$")&&!entry.getKey().equals("planned_quantity")&&entry.getValue()!=null&&new BigDecimal(entry.getValue().toString()).signum()!=0)reasons.add("NONZERO_"+entry.getKey());
            if(e.get("inherited_snapshot")!=null&&!Set.of("[]","{}","null").contains(e.get("inherited_snapshot").toString().replace(" ","")))reasons.add("INHERITED_SOURCE");
            var o=orders.stream().filter(x->n(x.get("id"))==n(e.get("order_id"))).findFirst().orElse(null);
            var i=items.stream().filter(x->n(x.get("id"))==n(e.get("order_item_id"))&&n(x.get("order_id"))==n(e.get("order_id"))).findFirst().orElse(null);
            var route=steps.stream().filter(x->n(x.get("item_id"))==n(e.get("order_item_id"))).toList();
            if(o==null||i==null||n(o.get("deleted"))!=0||n(o.get("flow_version"))<1||route.isEmpty()||i.get("product_snapshot")==null||!Set.of("CREATED","IN_PROGRESS").contains(o.get("status")))reasons.add("INCOMPLETE_ORDER_PRODUCT_ROUTE");
            if(!route.isEmpty()&&!cancelled&&n(route.get(0).get("id"))!=n(e.get("order_step_id")))reasons.add("PREVIOUS_COMPLETION_UNPROVEN");
            for(int k=0;k<route.size();k++)if(n(route.get(k).get("sequence_no"))!=k+1||n(route.get(k).get("execution_closed"))!=0)reasons.add("ROUTE_NOT_CONTIGUOUS_OR_CLOSED");
            if(route.stream().noneMatch(x->n(x.get("id"))==n(e.get("order_step_id")))||route.stream().anyMatch(x->x.get("measurement_mode")==null||x.get("output_category")==null||x.get("process_snapshot")==null))reasons.add("INCOMPLETE_ROUTE");
            for(var col:columns){String t=col.get("t").toString(),c=col.get("c").toString();
                if(Set.of("metal_production_execution","metal_production_order_item","metal_production_batch").contains(t))continue;
                Object value=c.endsWith("execution_id")?e.get("id"):e.get(Set.of("work_order_id","origin_order_id").contains(c)?"order_id":c.equals("origin_step_id")?"order_step_id":c);
                if(value==null)continue;
                var rows=j.queryForList("SELECT * FROM `"+t+"` WHERE `"+c+"`=?",value);
                if(!rows.isEmpty()){refs.put(t+"."+c,rows);reasons.add("REFERENCE_"+t+"."+c);}
            }
            long planned=n(e.get("planned_quantity")),item=n(e.get("order_item_id"));
            if(planned<1||planned>Integer.MAX_VALUE)reasons.add("PLAN_UNPROVEN");
            if(!cancelled&&i!=null&&allocated.getOrDefault(item,0L)>n(i.get("quantity")))reasons.add("PLAN_EXCEEDS_TARGET");
            var row=new LinkedHashMap<String,Object>();row.put("execution",e);row.put("reasons",reasons);row.put("references",refs);
            if(reasons.isEmpty())candidates.add(row);else ambiguous.add(row);
        }
        var blockedItems=new HashSet<Long>();
        for(Object raw:ambiguous){var e=(Map<?,?>)((Map<?,?>)raw).get("execution");if(!"CANCELLED".equals(e.get("status")))blockedItems.add(n(e.get("order_item_id")));}
        for(var it=candidates.iterator();it.hasNext();){var row=(Map<?,?>)it.next();var e=(Map<?,?>)row.get("execution");if(!"CANCELLED".equals(e.get("status"))&&blockedItems.contains(n(e.get("order_item_id")))){((List<String>)row.get("reasons")).add("UNMAPPED_SIBLING_REQUIRES_REVIEW");ambiguous.add(row);it.remove();}}
        var retired=new TreeMap<String,Object>();
        for(String t:j.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND (table_name LIKE 'metal_process_batch%' OR table_name LIKE 'metal_warehouse_common%' OR table_name='metal_batch_wip_balance') ORDER BY table_name",String.class))retired.put(t,j.queryForObject("SELECT COUNT(*) FROM `"+t+"`",Long.class));
        var result=new LinkedHashMap<String,Object>();result.put("schema",j.queryForObject("SELECT DATABASE()",String.class));result.put("factory",factory);result.put("candidates",candidates);result.put("ambiguous",ambiguous);result.put("orders",orders);result.put("items",items);result.put("steps",steps);result.put("batches",batches);result.put("retiredTablesRequireManualReview",retired);return result;
    }
    static Object apply(JdbcTemplate j,TransactionTemplate tx,long f,long actor,String request,Map<String,Object> manifest,boolean fault){
        UUID.fromString(request);String fingerprint=hash(manifest);
        return tx.execute(status->{
            require(!j.queryForList("SELECT factory_id FROM metal_factory WHERE factory_id=? AND del_flag='0' FOR UPDATE",f).isEmpty(),"Factory unavailable");
            var replay=j.queryForList("SELECT action,request_hash,CAST(result AS CHAR) result FROM metal_production_execution_operation WHERE factory_id=? AND actor_id=? AND request_id=? FOR UPDATE",f,actor,request);
            if(!replay.isEmpty()){require("MIGRATE_EMPTY_EXECUTION".equals(replay.get(0).get("action"))&&fingerprint.equals(replay.get(0).get("request_hash")),"Request content changed");return JSON.parse(replay.get(0).get("result").toString());}
            require(j.queryForObject("SELECT COUNT(*) FROM sys_user WHERE user_id=? AND del_flag='0'",Integer.class,actor)==1,"Actor unavailable");
            require(json(scan(j,f)).equals(json(manifest)),"Stale scan manifest; rescan required");
            var mappings=new ArrayList<Object>();var service=new ProductionBatchService(j,null,null);
            for(Object candidate:(List<?>)manifest.get("candidates")){
                var e=(Map<?,?>)((Map<?,?>)candidate).get("execution");long id=n(e.get("id"));
                var current=j.queryForMap("SELECT * FROM metal_production_execution WHERE factory_id=? AND id=? FOR UPDATE",f,id);
                require(current.get("batch_id")==null&&n(current.get("version"))==n(e.get("version")),"Execution changed");
                long batch=insert(j,"INSERT INTO metal_production_batch(factory_id,number,order_id,order_item_id,route_snapshot,planned_quantity,lifecycle,origin_action,completed_step_ids,create_by) VALUES(?,?,?,?,?,?,?,'MIGRATION','[]',?)",f,service.nextNumber(),e.get("order_id"),e.get("order_item_id"),json(ProductionBatchService.routeSnapshot(j,n(e.get("order_item_id")))),e.get("planned_quantity"),"CANCELLED".equals(e.get("status"))?"CANCELLED":"ACTIVE",actor);
                require(j.update("UPDATE metal_production_execution SET batch_id=?,version=version+1 WHERE factory_id=? AND id=? AND version=? AND batch_id IS NULL",batch,f,id,e.get("version"))==1,"Concurrent execution update");
                mappings.add(Map.of("executionId",String.valueOf(id),"batchId",String.valueOf(batch)));
                require(!fault,"Injected rollback after first mapping");
            }
            long op=insert(j,"INSERT INTO metal_production_execution_operation(factory_id,actor_id,request_id,action,request_hash,result) VALUES(?,?,?,'MIGRATE_EMPTY_EXECUTION',?,?)",f,actor,request,fingerprint,json(mappings));
            for(Object m:mappings)j.update("UPDATE metal_production_batch SET origin_operation_id=? WHERE factory_id=? AND id=?",op,f,((Map<?,?>)m).get("batchId"));
            return mappings;
        });
    }
    public static void main(String[] args)throws Exception{
        require(args.length>=4,"scan|apply|check-old-program schema factory manifest [actor requestId manifestHash]");
        String schema=args[1];require(schema.matches("production_batch_p5_20261112_[a-f0-9]{10}")&&!schema.endsWith("762c61d418"),"Only a new disposable P5 schema is allowed");long factory=Long.parseLong(args[2]);require(factory>0,"Factory required");
        String url=System.getenv("PRODUCTION_BATCH_P5_TEST_JDBC_URL");require(url!=null&&url.matches("jdbc:mysql://127\\.0\\.0\\.1:3323/"+schema+"(?:\\?.*)?"),"Explicit isolated URL required");
        var ds=new DriverManagerDataSource(url,System.getenv("PRODUCTION_BATCH_P5_TEST_USER"),System.getenv("PRODUCTION_BATCH_P5_TEST_PASSWORD"));var j=new JdbcTemplate(ds);
        require(j.queryForObject("SELECT @@port",Integer.class)==3323,"Wrong server");
        require(j.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version='84' AND success=1",Integer.class)==1,"V84 required");
        var tx=new TransactionTemplate(new DataSourceTransactionManager(ds));tx.setIsolationLevel(java.sql.Connection.TRANSACTION_SERIALIZABLE);
        if("scan".equals(args[0])){tx.setReadOnly(true);var m=tx.execute(s->scan(j,factory));Files.writeString(Path.of(args[3]),json(m));System.out.println(json(Map.of("scanManifestHash",hash(m),"candidates",((List<?>)m.get("candidates")).size(),"ambiguous",((List<?>)m.get("ambiguous")).size())));}
        else if("apply".equals(args[0])){require(args.length==7,"Explicit actor/requestId/manifestHash required");var m=JSON.parseObject(Files.readString(Path.of(args[3])));require(args[6].equals(hash(m))&&schema.equals(m.get("schema"))&&factory==n(m.get("factory")),"Manifest scope/hash mismatch");System.out.println(json(apply(j,tx,factory,Long.parseLong(args[4]),args[5],m,false)));}
        else if("check-old-program".equals(args[0])){require(j.queryForObject("SELECT COUNT(*) FROM metal_production_batch",Long.class)==0,"New batch writes exist: direct rollback to old program is unsafe; restore validated freeze-point backup instead");System.out.println("No new batch identity found; other deployment checks still required");}
        else throw new IllegalArgumentException("Unknown command");
    }
}
