package com.ruoyi.metal.service;

import com.alibaba.fastjson2.JSON;
import java.nio.file.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import static com.ruoyi.metal.service.ProductionBatchP5HistoryMigration.*;

/** Assert-based integration check; run only after the V80 historical fixture was upgraded to V84. */
public class ProductionBatchP5HistoryMigrationCheck {
    static String snapshot(JdbcTemplate j){var all=new TreeMap<String,Object>();for(String t:List.of("metal_storage_stock","metal_storage_ownership","metal_storage_entry","metal_storage_event","metal_storage_debt","metal_storage_document","metal_storage_reservation","metal_production_execution_event","metal_production_material_source","metal_production_material_allocation","metal_production_wip_transfer"))all.put(t,j.queryForList("SELECT * FROM "+t+" ORDER BY 1"));return json(all);}
    static void rejected(Runnable work){boolean failed=false;try{work.run();}catch(RuntimeException ex){failed=true;}require(failed,"Expected rejection");}
    public static void main(String[] args)throws Exception{
        String url=System.getenv("PRODUCTION_BATCH_P5_TEST_JDBC_URL");require(url!=null&&url.matches("jdbc:mysql://127\\.0\\.0\\.1:3323/production_batch_p5_20261112_[a-f0-9]{10}(?:\\?.*)?")&&!url.contains("762c61d418"),"Unsafe rehearsal URL");
        var ds=new DriverManagerDataSource(url,System.getenv("PRODUCTION_BATCH_P5_TEST_USER"),System.getenv("PRODUCTION_BATCH_P5_TEST_PASSWORD"));var j=new JdbcTemplate(ds);require(j.queryForObject("SELECT @@port",Integer.class)==3323,"Wrong port");
        var tx=new TransactionTemplate(new DataSourceTransactionManager(ds));tx.setIsolationLevel(java.sql.Connection.TRANSACTION_SERIALIZABLE);
        j.update("INSERT INTO metal_production_order(id,order_no,factory_id,production_line_id,owner_id,processing_type,order_date,factory_name,line_name,owner_name,request_id,request_actor,request_hash,create_by,creator_name,flow_version) VALUES(7502,'P5-EMPTY',7001,7101,1,'DIRECT_CUSTOM',CURRENT_DATE,'history','history','test',?,1,REPEAT('b',64),'test','test',1)",UUID.randomUUID().toString());
        j.update("INSERT INTO metal_production_order_item(id,order_id,sequence_no,product_id,quantity,product_snapshot) VALUES(7602,7502,1,7201,30,JSON_OBJECT('name','empty'))");
        j.update("INSERT INTO metal_production_order_step(id,item_id,sequence_no,process_id,process_name,process_snapshot,semantic_code,output_category,output_form,measurement_mode) VALUES(76021,7602,1,7401,'empty','{}','NORMAL','SEMI','SEMI','PIECE')");
        for(long id:List.of(7702L,7703L))j.update("INSERT INTO metal_production_execution(id,factory_id,order_id,order_item_id,order_step_id,processor_id,planned_quantity,inherited_snapshot,create_by,status) VALUES(?,7001,7502,7602,76021,1,10,'[]',1,?)",id,id==7702?"OPEN":"CANCELLED");
        String ledger=snapshot(j);var manifest=scan(j,7001);require(((List<?>)manifest.get("candidates")).size()==2,"Expected OPEN + CANCELLED candidates: "+json(manifest));require(((List<?>)manifest.get("ambiguous")).size()==1,"History must stay ambiguous");
        require(ledger.equals(snapshot(j)),"Dry run changed ledger");require(j.queryForObject("SELECT COUNT(*) FROM metal_production_batch",Long.class)==0,"Dry run wrote batch");
        var stale=JSON.parseObject(json(manifest));j.update("UPDATE metal_production_execution SET version=version+1 WHERE id=7702");rejected(()->apply(j,tx,7001,1,UUID.randomUUID().toString(),stale,false));
        var fresh=scan(j,7001);rejected(()->apply(j,tx,7001,1,UUID.randomUUID().toString(),fresh,true));require(j.queryForObject("SELECT COUNT(*) FROM metal_production_batch",Long.class)==0,"Fault left partial batch");require(j.queryForObject("SELECT COUNT(*) FROM metal_production_execution WHERE batch_id IS NOT NULL",Long.class)==0,"Fault left partial link");require(j.queryForObject("SELECT COUNT(*) FROM metal_production_execution_operation WHERE action='MIGRATE_EMPTY_EXECUTION'",Long.class)==0,"Fault left audit");
        String request=UUID.randomUUID().toString();Object mapped=apply(j,tx,7001,1,request,fresh,false);require(json(mapped).equals(json(apply(j,tx,7001,1,request,fresh,false))),"Replay differs");
        require(j.queryForObject("SELECT COUNT(*) FROM metal_production_batch WHERE origin_action='MIGRATION'",Long.class)==2,"Must create one batch per execution");require(j.queryForObject("SELECT COUNT(*) FROM metal_production_batch WHERE JSON_LENGTH(completed_step_ids)<>0",Long.class)==0,"Invented completion");require(j.queryForObject("SELECT batch_id FROM metal_production_execution WHERE id=7701",Long.class)==null,"Complex history was mapped");require(ledger.equals(snapshot(j)),"Mapping changed business ledger");
        j.update("INSERT INTO metal_production_order_item(id,order_id,sequence_no,product_id,quantity,product_snapshot) VALUES(7603,7502,2,7201,30,JSON_OBJECT('name','over-capacity'))");
        j.update("INSERT INTO metal_production_order_step(id,item_id,sequence_no,process_id,process_name,process_snapshot,semantic_code,output_category,output_form,measurement_mode) VALUES(76031,7603,1,7401,'capacity','{}','NORMAL','SEMI','SEMI','PIECE')");
        for(long id:List.of(7704L,7705L,7706L,7707L))j.update("INSERT INTO metal_production_execution(id,factory_id,order_id,order_item_id,order_step_id,processor_id,planned_quantity,inherited_snapshot,create_by,input_weight) VALUES(?,7001,7502,?,?,1,?,'[]',1,?)",id,id<7706?7602:7603,id<7706?76021:76031,id<7706?10:20,id==7705?3:0);
        var guarded=scan(j,7001);require(((List<?>)guarded.get("candidates")).isEmpty(),"Ambiguous sibling or joint over-capacity leaked a candidate");
        var reasons=new HashMap<Long,List<?>>();for(Object raw:(List<?>)guarded.get("ambiguous")){var row=(Map<?,?>)raw;reasons.put(n(((Map<?,?>)row.get("execution")).get("id")),(List<?>)row.get("reasons"));}
        require(reasons.get(7704L).contains("UNMAPPED_SIBLING_REQUIRES_REVIEW"),"Balance-only sibling did not freeze empty OPEN");
        require(reasons.get(7706L).contains("PLAN_EXCEEDS_TARGET")&&reasons.get(7707L).contains("PLAN_EXCEEDS_TARGET"),"Over-capacity used execution ID priority");
        require(ledger.equals(snapshot(j)),"Safety scan changed business ledger");
        var proof=new LinkedHashMap<String,Object>();proof.putAll(Map.of("schema",j.queryForObject("SELECT DATABASE()",String.class),"dryRunNoWrites",true,"candidateCount",2,"ambiguousCount",1,"staleManifestRejected",true,"faultRollback",true,"idempotentReplay",true,"independentExecutionBatches",true,"businessLedgerUnchanged",true,"manifestHash",StorageSupport.hash(fresh)));proof.put("balanceOnlySiblingBlocksOpen",true);proof.put("jointOverCapacityBlocksAll",true);Files.writeString(Path.of(args[0]),json(proof));System.out.println(json(proof));
    }
}
