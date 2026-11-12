package com.ruoyi.metal.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import java.nio.file.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;

/** Read-only evidence inventory; no provenance, identities or ledger rows are inferred or written. */
public class ProductionBatchP5FullProvenance {
 static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
 static long n(Object x){return x==null?0:x instanceof Boolean b?(b?1:0):Long.parseLong(x.toString());}
 static BigDecimal w(Object x){return x==null?BigDecimal.ZERO:new BigDecimal(x.toString());}
 static String json(Object x){return JSON.toJSONString(x,JSONWriter.Feature.WriteMapNullValue,JSONWriter.Feature.MapSortField);}
 static boolean exists(JdbcTemplate j,String table){return j.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?",Integer.class,table)>0;}
 static class HistoricalJdbc extends JdbcTemplate {
  final boolean legacy;
  HistoricalJdbc(DriverManagerDataSource ds,boolean legacy){super(ds);this.legacy=legacy;}
  @Override public <T>T queryForObject(String sql,Class<T> type,Object...args){return super.queryForObject(legacy?sql.replace("SELECT batch_id FROM metal_production_execution","SELECT NULL AS batch_id FROM metal_production_execution"):sql,type,args);}
 }
 static List<String> reasons(boolean mapped,int inputs,int exact,boolean replay,boolean mixed){
  var r=new ArrayList<String>();
  if(!mapped)r.add("NO_PRODUCTION_BATCH_MAPPING");
  if(inputs>exact)r.add("INPUT_WITHOUT_IMMUTABLE_MATERIAL_ALLOCATION");
  if(!replay)r.add("SOURCE_REPLAY_REJECTED");
  if(mixed)r.add("MIXED_POOL_DOES_NOT_PROVE_PER_PIECE_ORIGIN");
  return r;
 }
 static void selfCheck(){
  require(reasons(true,1,1,true,false).isEmpty(),"Exact source self-check");
  require(reasons(false,2,0,true,true).size()==3,"Legacy ambiguity self-check");
  require(reasons(true,1,1,false,false).contains("SOURCE_REPLAY_REJECTED"),"Replay failure self-check");
 }
 static Map<String,Object> scan(JdbcTemplate j){
  var out=new LinkedHashMap<String,Object>();
  out.put("schema",j.queryForObject("SELECT DATABASE()",String.class));
  int readOnly=j.queryForObject("SELECT @@transaction_read_only",Integer.class);require(readOnly==1,"Server read-only transaction required");out.put("readOnlyTransaction",readOnly);
  out.put("flywayVersion",j.queryForObject("SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success=1",Integer.class));
  boolean precise=exists(j,"metal_production_material_source"),batches=exists(j,"metal_production_batch");out.put("newMaterialSourceTablesPresent",precise);out.put("legacyNullBatchReadAdapter",!batches);
  var summaries=new ArrayList<Object>();
  for(var e:j.queryForList("SELECT id,factory_id,order_id,order_item_id,order_step_id,"+(batches?"batch_id":"NULL AS batch_id")+",status,input_weight,output_weight,returned_weight,recovered_weight,loss_weight,input_quantity,output_quantity FROM metal_production_execution ORDER BY factory_id,id")){
   long id=n(e.get("id"));
   var events=j.queryForList("SELECT ev.id,ev.factory_id,ev.execution_id,ev.document_id,ev.line_id,ev.event_type,ev.stock_id,ev.source_execution_id,ev.weight,ev.quantity,ev.customer_weight,ev.factory_weight,ev.reversed,st.category,st.origin_order_id,st.origin_step_id,CAST(JSON_UNQUOTE(JSON_EXTRACT(st.identity_snapshot,'$.sourceExecutionId')) AS UNSIGNED) snapshot_source_execution_id FROM metal_production_execution_event ev LEFT JOIN metal_storage_stock st ON st.factory_id=ev.factory_id AND st.id=ev.stock_id WHERE ev.execution_id=? ORDER BY ev.id",id);
   int inputs=0,exact=0;boolean raw=false,semi=false,cross=false,mixed=false;var eventMissing=new ArrayList<String>();
   for(var ev:events){
    if(!"INPUT".equals(ev.get("event_type"))||n(ev.get("reversed"))!=0)continue;
    inputs++;raw|="RAW".equals(ev.get("category"));semi|="SEMI".equals(ev.get("category"));
    mixed|=w(ev.get("customer_weight")).signum()>0&&w(ev.get("factory_weight")).signum()>0;
    var allocation=precise?j.queryForMap("SELECT COUNT(*) allocation_count,COALESCE(SUM(a.weight),0) allocated_weight,COALESCE(SUM(CASE WHEN t.execution_id=? AND t.factory_id=a.factory_id AND t.root_issue_allocation_id IS NOT NULL THEN 0 ELSE 1 END),0) invalid_target_count FROM metal_production_material_allocation a JOIN metal_storage_event s ON s.factory_id=a.factory_id AND s.id=a.storage_event_id LEFT JOIN metal_production_material_source t ON t.id=a.to_source_id WHERE s.factory_id=? AND s.document_id=? AND s.line_id=? AND a.purpose='ISSUE'",id,ev.get("factory_id"),ev.get("document_id"),ev.get("line_id")):Map.<String,Object>of("allocation_count",0,"allocated_weight",BigDecimal.ZERO,"invalid_target_count",0);
    ev.put("immutableIssueEvidence",allocation);if(n(allocation.get("allocation_count"))>0&&n(allocation.get("invalid_target_count"))==0&&w(allocation.get("allocated_weight")).compareTo(w(ev.get("weight")))==0)exact++;
    var sourceIds=j.queryForList("SELECT p.id production_event_id,p.execution_id source_execution_id,e.order_step_id source_step_id FROM metal_production_execution_event p JOIN metal_production_execution e ON e.id=p.execution_id WHERE p.factory_id=? AND p.stock_id=? AND p.event_type='OUTPUT' AND p.reversed=0 ORDER BY p.id",ev.get("factory_id"),ev.get("stock_id"));
    ev.put("stockOutputSources",sourceIds);
    if(sourceIds.size()>1)eventMissing.add("INPUT_EVENT_"+ev.get("id")+"_MULTIPLE_STOCK_OUTPUT_PRODUCERS_REQUIRE_CONFIRMED_CONTRIBUTIONS");
    if("SEMI".equals(ev.get("category"))&&sourceIds.isEmpty())eventMissing.add("INPUT_EVENT_"+ev.get("id")+"_SEMI_WITHOUT_ACTIVE_OUTPUT_PRODUCER");
    if(ev.get("source_execution_id")!=null&&ev.get("snapshot_source_execution_id")!=null&&n(ev.get("source_execution_id"))!=n(ev.get("snapshot_source_execution_id")))eventMissing.add("INPUT_EVENT_"+ev.get("id")+"_SOURCE_EXECUTION_DISAGREEMENT");
    for(var s:sourceIds)cross|=n(s.get("source_step_id"))!=n(e.get("order_step_id"));
    ev.put("issueAllocations",j.queryForList("SELECT id,event_id,stock_id,source_owner_id,customer_id,issued_weight,received_weight,returned_weight,issued_quantity,received_quantity,returned_quantity,advance_debt_id,borrow_debt_id FROM metal_storage_issue_allocation WHERE factory_id=? AND document_id=? AND line_id=? ORDER BY id",ev.get("factory_id"),ev.get("document_id"),ev.get("line_id")));
   }
   boolean replay=true;var result=new LinkedHashMap<String,Object>();
   try{var source=new ProductionSourceBalances(j);var net=source.net(id);result.put("net",net);mixed|=net.get(0).signum()>0&&net.get(1).signum()>0;result.put("remaining",source.remaining(id));for(var ev:events)if(n(ev.get("reversed"))==0){if("INPUT".equals(ev.get("event_type"))&&ev.get("stock_id")!=null)source.requireAcyclicInput(n(e.get("factory_id")),id,n(ev.get("stock_id")));if(Set.of("OUTPUT","RECOVERY").contains(ev.get("event_type")))ev.put("replayedSource",source.output(id,n(ev.get("id"))));}}
   catch(RuntimeException ex){replay=false;result.put("errorType",ex.getClass().getSimpleName());}
   boolean nonempty=!events.isEmpty()||w(e.get("input_weight")).signum()!=0;
   var missing=reasons(e.get("batch_id")!=null,inputs,exact,replay,mixed||raw&&semi);
   missing.addAll(eventMissing);
   String classification=!nonempty?"EMPTY_RECORD_REQUIRES_P5_CANDIDATE_SCAN":missing.isEmpty()?"TRUSTED_EVIDENCE_CANDIDATE_NOT_WRITE_AUTHORIZATION":replay?"HISTORICAL_READ_ONLY":"BUSINESS_VERIFICATION_REQUIRED";
   var row=new LinkedHashMap<String,Object>();row.put("execution",e);row.put("events",events);row.put("sourceReplay",result);row.put("classification",classification);row.put("missingFacts",missing);row.put("mixedRawSemi",raw&&semi);row.put("crossStepInput",cross);row.put("mixedCustomerFactory",mixed);summaries.add(row);
  }
  out.put("executions",summaries);
  out.put("stocks",j.queryForList("SELECT id,factory_id,category,measurement_mode,origin_order_id,origin_step_id,origin_document_id,origin_line_id,net_weight,quantity,CAST(JSON_UNQUOTE(JSON_EXTRACT(identity_snapshot,'$.sourceExecutionId')) AS UNSIGNED) snapshot_source_execution_id FROM metal_storage_stock ORDER BY id"));
  out.put("materialSources",precise?j.queryForList("SELECT id,factory_id,stock_slice_id,execution_id,source_allocation_id,root_issue_allocation_id,current_owner_id,source_owner_id,source_kind,initial_weight,initial_quantity,weight,quantity,returnable_weight,returnable_quantity FROM metal_production_material_source ORDER BY id"):List.of());
  out.put("materialAllocations",precise?j.queryForList("SELECT id,factory_id,operation_id,storage_event_id,from_source_id,to_source_id,wip_transfer_id,purpose,weight,quantity,allocation_basis FROM metal_production_material_allocation ORDER BY id"):List.of());
  out.put("wipTransfers",precise?j.queryForList("SELECT id,factory_id,operation_id,source_execution_id,target_execution_id,order_step_id,weight,quantity,allocation_basis FROM metal_production_wip_transfer ORDER BY id"):List.of());
  out.put("returns",j.queryForList("SELECT r.event_id,r.issue_allocation_id,r.weight,r.quantity,e.factory_id,e.reversed_by,a.line_id issue_line_id,a.stock_id,a.returned_weight,a.returned_quantity FROM metal_storage_return_allocation r JOIN metal_storage_event e ON e.id=r.event_id JOIN metal_storage_issue_allocation a ON a.id=r.issue_allocation_id ORDER BY r.event_id,r.issue_allocation_id"));
  out.put("reversals",j.queryForList("SELECT id,factory_id,document_id,line_id,action,reversed_by FROM metal_storage_event WHERE reversed_by IS NOT NULL OR action='REVERSAL' ORDER BY id"));
  out.put("debts",j.queryForList("SELECT id,factory_id,event_id,stock_id,work_order_id,creditor_id,debtor_id,debt_kind,original_weight,original_quantity,remaining_weight,remaining_quantity FROM metal_storage_debt ORDER BY id"));
  out.put("settlements",j.queryForList("SELECT id,event_id,factory_id,debt_id,weight,quantity FROM metal_storage_debt_settlement ORDER BY id"));
  var retired=new LinkedHashMap<String,Object>();
  Set<String> allowed=Set.of("id","batch_id","factory_id","work_order_id","item_id","process_id","sequence_no","planned_quantity","completed_quantity","status","measurement_mode","source_line_id","remaining_weight","flow_id","line_id","inventory_id","report_id","handover_flow_id","voided","before_weight","allocated_weight","after_weight","returned_weight","net_weight","quantity","origin_batch_id","origin_handover_flow_id","order_id","kind","type");
  for(String table:List.of("metal_process_batch","metal_batch_wip_balance","metal_handover_allocation","metal_process_raw_return_line","metal_work_report","metal_inventory","metal_warehouse_flow","metal_warehouse_order")){
   var columns=j.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=? ORDER BY ordinal_position",String.class,table).stream().filter(allowed::contains).toList();
   if(!columns.isEmpty()){
    String snapshot="metal_process_raw_return_line".equals(table)?",(source_snapshot IS NOT NULL) source_snapshot_present,JSON_LENGTH(source_snapshot) source_snapshot_field_count":"";
    retired.put(table,j.queryForList("SELECT "+String.join(",",columns)+snapshot+" FROM "+table));
   }
  }
  out.put("retiredReadOnlyEvidence",retired);return out;
 }
 public static void main(String[] args)throws Exception{
  selfCheck();require(args.length==2,"schema output-json required");String schema=args[0];
  require(schema.matches("production_batch_p5_20261112_[a-f0-9]{10}")&&!schema.endsWith("762c61d418"),"New P5 isolation schema only");
  String url=System.getenv("PRODUCTION_BATCH_P5_TEST_JDBC_URL");
  require(url!=null&&url.matches("jdbc:mysql://127\\.0\\.0\\.1:3323/"+schema+"(?:\\?.*)?"),"Explicit isolated URL required");
  Path target=Path.of(args[1]).toAbsolutePath().normalize();
  require(target.startsWith(Path.of(".runtime/production-batch-p5-full-history-20261112").toAbsolutePath().normalize())&&target.getFileName().toString().startsWith("provenance-"),"New provenance output only");
  var ds=new DriverManagerDataSource(url,System.getenv("PRODUCTION_BATCH_P5_TEST_USER"),System.getenv("PRODUCTION_BATCH_P5_TEST_PASSWORD"));var j=new JdbcTemplate(ds);
  require(j.queryForObject("SELECT @@port",Integer.class)==3323,"Wrong server");j=new HistoricalJdbc(ds,!exists(j,"metal_production_batch"));final var readJdbc=j;
  var manager=new DataSourceTransactionManager(ds);
  var tx=new TransactionTemplate(manager);tx.setReadOnly(true);tx.setIsolationLevel(java.sql.Connection.TRANSACTION_REPEATABLE_READ);
  var report=tx.execute(s->scan(readJdbc));Files.writeString(target,json(report),StandardOpenOption.CREATE_NEW);
  System.out.println(json(Map.of("schema",schema,"output",target.toString(),"executions",((List<?>)report.get("executions")).size(),"readOnly",true)));
 }
}
