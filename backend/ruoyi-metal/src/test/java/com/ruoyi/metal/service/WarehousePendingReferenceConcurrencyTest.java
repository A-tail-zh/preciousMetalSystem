package com.ruoyi.metal.service;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

@EnabledIfEnvironmentVariable(named="METAL_FLOW_ISOLATED_JDBC_URL",matches=WarehouseIntegrationEnvironment.URL_PATTERN)
class WarehousePendingReferenceConcurrencyTest {
 @Test void pendingReferenceCommittedDuringMaterialLockWaitIsSeenDespiteAnEarlierSnapshot()throws Exception{
  var ds=WarehouseIntegrationEnvironment.dataSource();long factory=Long.parseLong(System.getenv("METAL_FLOW_FACTORY_ID"));var base=new JdbcTemplate(ds);String suffix=System.getenv("METAL_FLOW_FIXTURE_PREFIX").substring("工单验收_".length());long material=base.queryForObject("SELECT material_id FROM metal_material WHERE material_code=? AND del_flag='0'",Long.class,"WH_M2_"+suffix);assertFalse(WarehouseReferences.materialReferenced(base,material));
  long warehouse=base.queryForObject("SELECT warehouse_id FROM metal_warehouse WHERE factory_id=? AND del_flag='0' LIMIT 1",Long.class,factory),person=base.queryForObject("SELECT user_id FROM sys_user WHERE factory_id=? AND del_flag='0' ORDER BY user_id LIMIT 1",Long.class,factory);long flow=0,order=0,group=0,unrelatedMaterial=0;var executor=Executors.newSingleThreadExecutor();
  try(var a=ds.getConnection();var b=ds.getConnection()){
   a.setAutoCommit(false);b.setTransactionIsolation(java.sql.Connection.TRANSACTION_REPEATABLE_READ);b.setAutoCommit(false);var writer=new JdbcTemplate(new SingleConnectionDataSource(a,true));var reader=new JdbcTemplate(new SingleConnectionDataSource(b,true));
   writer.queryForMap("SELECT material_id FROM metal_material WHERE material_id=? FOR UPDATE",material);
   reader.queryForObject("SELECT COUNT(*) FROM metal_warehouse_flow WHERE factory_id=?",Long.class,factory); // Establish the caller's earlier repeatable-read snapshot.
   var started=new CountDownLatch(1);var result=executor.submit(()->{started.countDown();reader.queryForMap("SELECT material_id FROM metal_material WHERE material_id=? FOR UPDATE",material);return WarehouseReferences.materialReferenced(reader,material);});assertTrue(started.await(2,TimeUnit.SECONDS));
   String request=UUID.randomUUID().toString();writer.update("INSERT INTO metal_warehouse_flow(request_id,request_hash,request_actor,factory_id,kind,stage,plan_json) VALUES(?,REPEAT('a',64),?,?,'RAW_INBOUND','KEEPER','{}')",request,person,factory);flow=writer.queryForObject("SELECT flow_id FROM metal_warehouse_flow WHERE request_id=?",Long.class,request);
   String document=UUID.randomUUID().toString();writer.update("INSERT INTO metal_warehouse_order(order_no,request_id,request_hash,request_actor,factory_id,warehouse_id,kind,type,order_date,keeper_id,operator_name,keeper_name,warehouse_name,status,warehouse_flow_id) VALUES(?,?,REPEAT('b',64),?,?,?,'IN','RAW_IN',CURRENT_DATE,?,'registered fixture','registered fixture','registered fixture','WAITING_IN',?)","PENDING-"+request.substring(0,16),document,person,factory,warehouse,person,flow);order=writer.queryForObject("SELECT order_id FROM metal_warehouse_order WHERE request_id=?",Long.class,document);
   writer.update("INSERT INTO metal_warehouse_flow_group(flow_id,revision,sequence_no,order_id,warehouse_id,type,keeper_id,order_json) VALUES(?,0,1,?,?,'RAW_IN',?,'{}')",flow,order,warehouse,person);group=writer.queryForObject("SELECT group_id FROM metal_warehouse_flow_group WHERE order_id=?",Long.class,order);
   writer.update("INSERT INTO metal_warehouse_flow_line(group_id,sequence_no,line_json,planned_weight) VALUES(?,1,?,1)",group,"{\"category\":\"RAW\",\"referenceId\":\""+material+"\"}");a.commit();
   try{assertTrue(result.get(10,TimeUnit.SECONDS),"分类变更在物料锁后必须看到已提交的待确认引用，不能沿用旧快照");}finally{b.rollback();}
   writer.queryForMap("SELECT flow_id FROM metal_warehouse_flow WHERE flow_id=? FOR UPDATE",flow);reader.queryForMap("SELECT material_id FROM metal_material WHERE material_id=? FOR UPDATE",material);var pending=executor.submit(()->{try{return WarehouseReferences.materialReferenced(reader,material);}catch(com.ruoyi.common.exception.ServiceException e){assertTrue(e.getMessage().contains("正在处理中"));return true;}});boolean waitedForParent=false;try{assertTrue(pending.get(2,TimeUnit.SECONDS));}catch(TimeoutException e){waitedForParent=true;}finally{a.rollback();assertTrue(pending.get(5,TimeUnit.SECONDS));b.rollback();}assertFalse(waitedForParent,"引用检查只锁明细，不应反向等待交接父行锁");
   String unusedCode="UNRELATED-"+UUID.randomUUID();
   base.update("INSERT INTO metal_material(material_code,material_name,material_type,specification,metal_type_id,factory_id,create_by) SELECT ?,material_name,material_type,specification,metal_type_id,factory_id,'reference-test' FROM metal_material WHERE material_id=? AND factory_id=?",unusedCode,material,factory);
   unrelatedMaterial=base.queryForObject("SELECT material_id FROM metal_material WHERE material_code=? AND factory_id=?",Long.class,unusedCode,factory);
   writer.queryForMap("SELECT flow_line_id FROM metal_warehouse_flow_line WHERE group_id=? FOR UPDATE",group);
   reader.queryForMap("SELECT material_id FROM metal_material WHERE material_id=? FOR UPDATE",unrelatedMaterial);
   try{assertFalse(WarehouseReferences.materialReferenced(reader,unrelatedMaterial),"不得因其他物料的交接明细被锁而误报本物料正在处理中");}finally{a.rollback();b.rollback();}
  }finally{
   executor.shutdownNow();if(group!=0)base.update("DELETE FROM metal_warehouse_flow_line WHERE group_id=?",group);if(group!=0)base.update("DELETE FROM metal_warehouse_flow_group WHERE group_id=?",group);if(order!=0)base.update("DELETE FROM metal_warehouse_order WHERE order_id=? AND factory_id=?",order,factory);if(flow!=0)base.update("DELETE FROM metal_warehouse_flow WHERE flow_id=? AND factory_id=?",flow,factory);if(unrelatedMaterial!=0)base.update("DELETE FROM metal_material WHERE material_id=? AND factory_id=?",unrelatedMaterial,factory);
  }
 }
}
