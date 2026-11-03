package com.ruoyi.metal.service;

import java.lang.reflect.*;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.*;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.metal.catalog.MaterialRecord;
import com.ruoyi.metal.security.FactoryScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="METAL_FLOW_ISOLATED_JDBC_URL",matches="jdbc:mysql://(?:127\\.0\\.0\\.1|localhost):3321/heavy_metal_flow(?:\\?.*)?")
class RawTaskMaterialConcurrencyTest {
 private static final class Fixture implements AutoCloseable {
  final javax.sql.DataSource ds=WarehouseIntegrationEnvironment.dataSource();final JdbcTemplate base=new JdbcTemplate(ds);
  final long factory=Long.parseLong(System.getenv("METAL_FLOW_FACTORY_ID"));
  final long material=base.queryForObject("SELECT material_id FROM metal_material WHERE material_code=? AND del_flag='0'",Long.class,"WH_M2_"+System.getenv("METAL_FLOW_FIXTURE_PREFIX").substring("工单验收_".length()));
  final String request=UUID.randomUUID().toString();long work;
  long create(JdbcTemplate jdbc){
   // A registered unissued RAW reference fixture, without stock, batches or invented historical reports.
   assertEquals(1,jdbc.update("INSERT INTO metal_work_order(order_no,request_id,request_hash,request_actor,customer,factory_id,production_line_id,owner_id,factory_name,line_name,owner_name,order_date,status,create_by,creator_name,update_by,task_type,processing_policy_version,processing_type,customer_id,customer_snapshot) SELECT ?,?,REPEAT('a',64),request_actor,customer,factory_id,production_line_id,owner_id,factory_name,line_name,owner_name,CURRENT_DATE,'WAITING_MATERIAL','reference-test','reference-test','reference-test','RAW_PROCESSING',3,processing_type,customer_id,customer_snapshot FROM metal_work_order WHERE factory_id=? AND del_flag='0' ORDER BY work_order_id LIMIT 1","RAW-REF-"+request.substring(0,20),request,factory));
   work=jdbc.queryForObject("SELECT work_order_id FROM metal_work_order WHERE request_id=? AND factory_id=?",Long.class,request,factory);
   jdbc.update("INSERT INTO metal_work_order_item(work_order_id,sequence_no,product_id,quantity,product_snapshot,material_id,material_snapshot,planned_input_weight) SELECT ?,1,NULL,0,'{}',material_id,JSON_OBJECT('name','registered raw reference','metalTypeId',metal_type_id,'measurementPolicyVersion',2),1 FROM metal_material WHERE material_id=?",work,material);
   return work;
  }
  public void close(){if(work!=0){base.update("DELETE i FROM metal_work_order_item i JOIN metal_work_order w ON w.work_order_id=i.work_order_id WHERE w.work_order_id=? AND w.factory_id=? AND w.request_id=?",work,factory,request);base.update("DELETE FROM metal_work_order WHERE work_order_id=? AND factory_id=? AND request_id=?",work,factory,request);}}
 }
 @Test void committedRawPlanAndItsDeletionAreSeenAfterWaitingDespiteAnEarlierSnapshot()throws Exception {
  try(var f=new Fixture();Connection writer=f.ds.getConnection();Connection reader=f.ds.getConnection()){
   writer.setAutoCommit(false);reader.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);reader.setAutoCommit(false);
   var a=new JdbcTemplate(new SingleConnectionDataSource(writer,true));var b=new JdbcTemplate(new SingleConnectionDataSource(reader,true));var executor=Executors.newSingleThreadExecutor();
   try{
    assertFalse(WarehouseReferences.materialReferenced(f.base,f.material));
    a.queryForMap("SELECT material_id FROM metal_material WHERE material_id=? FOR UPDATE",f.material);
    b.queryForObject("SELECT COUNT(*) FROM metal_work_order WHERE factory_id=?",Long.class,f.factory);
    var started=new CountDownLatch(1);var result=executor.submit(()->{started.countDown();b.queryForMap("SELECT material_id FROM metal_material WHERE material_id=? FOR UPDATE",f.material);return WarehouseReferences.materialReferenced(b,f.material);});assertTrue(started.await(2,TimeUnit.SECONDS));
    f.create(a);writer.commit();assertTrue(result.get(10,TimeUnit.SECONDS));reader.rollback();
    a.queryForMap("SELECT material_id FROM metal_material WHERE material_id=? FOR UPDATE",f.material);
    b.queryForObject("SELECT COUNT(*) FROM metal_work_order WHERE factory_id=?",Long.class,f.factory);
    var deleted=executor.submit(()->{b.queryForMap("SELECT material_id FROM metal_material WHERE material_id=? FOR UPDATE",f.material);return WarehouseReferences.materialReferenced(b,f.material);});
    a.update("UPDATE metal_work_order SET del_flag='2' WHERE work_order_id=? AND factory_id=?",f.work,f.factory);writer.commit();assertFalse(deleted.get(10,TimeUnit.SECONDS));reader.rollback();
   }finally{writer.rollback();reader.rollback();executor.shutdownNow();}
  }
 }
 @Test void rawEditAndClassificationUseMaterialBeforeHeaderWithoutADeadlock()throws Exception {
  try(var f=new Fixture()){
   f.create(f.base);
   try(Connection materialConnection=f.ds.getConnection();Connection editConnection=f.ds.getConnection()){
    materialConnection.setAutoCommit(false);editConnection.setAutoCommit(false);
    var materialJdbc=new JdbcTemplate(new SingleConnectionDataSource(materialConnection,true));var attempted=new CountDownLatch(1);var executor=Executors.newSingleThreadExecutor();
    var editJdbc=new JdbcTemplate(new SingleConnectionDataSource(editConnection,true)){
     @Override public List<Map<String,Object>> queryForList(String sql,Object...args){if(sql.startsWith("SELECT material_id FROM metal_material")&&sql.contains("FOR UPDATE"))attempted.countDown();return super.queryForList(sql,args);}
     @Override public Map<String,Object> queryForMap(String sql,Object...args){if(sql.startsWith("SELECT material_id FROM metal_material")&&sql.contains("FOR UPDATE"))attempted.countDown();return super.queryForMap(sql,args);}
    };
    long owner=f.base.queryForObject("SELECT owner_id FROM metal_work_order WHERE work_order_id=?",Long.class,f.work);
    materialJdbc.queryForMap("SELECT material_id FROM metal_material WHERE material_id=? FOR UPDATE",f.material);
    try{
     var editing=executor.submit(()->{try{var locks=WorkOrderService.class.getDeclaredMethod("lockDependencies",Long.class,List.class,Long.class);locks.setAccessible(true);locks.invoke(new WorkOrderService(editJdbc,null,null,null),f.work,List.of(),owner);editJdbc.queryForMap("SELECT work_order_id FROM metal_work_order WHERE work_order_id=? FOR UPDATE",f.work);editJdbc.queryForMap("SELECT material_id FROM metal_material WHERE material_id=? FOR UPDATE",f.material);return true;}catch(ReflectiveOperationException failure){throw new RuntimeException(failure);}});
     assertTrue(attempted.await(3,TimeUnit.SECONDS));
     var old=materialJdbc.queryForMap("SELECT material_type,metal_type_id FROM metal_material WHERE material_id=?",f.material);var previous=new MaterialRecord();previous.setId(f.material);previous.setMaterialType(old.get("material_type").toString());previous.setMetalTypeId(((Number)old.get("metal_type_id")).longValue());var form=new MaterialRecord();form.setId(f.material);form.setMaterialType("旧料".equals(previous.getMaterialType())?"块料":"旧料");form.setMetalTypeId(previous.getMetalTypeId());
     try{assertThrows(ServiceException.class,()->new MaterialService(null,mock(FactoryScope.class),materialJdbc).prepare(form,previous));}finally{materialConnection.rollback();}
     assertTrue(editing.get(10,TimeUnit.SECONDS));
    }finally{materialConnection.rollback();editConnection.rollback();executor.shutdownNow();}
   }
  }
 }
}
