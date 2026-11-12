package com.ruoyi.metal.service;

import java.util.*;
import com.ruoyi.common.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="MASTER_DATA_TEST_JDBC_URL",matches="jdbc:mysql://127\\.0\\.0\\.1:3323/production_order_three_domain_audit_20261110(?:\\?.*)?")
class CustomerWarehouseReferenceMysqlTest {
 @Test void warehouseCustomerReferencesProtectStringAndNumericIdsUntilCancelledOrDeleted() throws Exception {
  var ds=new DriverManagerDataSource(System.getenv("MASTER_DATA_TEST_JDBC_URL"),System.getenv("MASTER_DATA_TEST_USER"),System.getenv("MASTER_DATA_TEST_PASSWORD"));
  try(var connection=ds.getConnection()){
   connection.setAutoCommit(false);
   try{
    var jdbc=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
    assertEquals(3323,jdbc.queryForObject("SELECT @@port",Integer.class));
    assertEquals("production_order_three_domain_audit_20261110",jdbc.queryForObject("SELECT DATABASE()",String.class));
    String suffix=UUID.randomUUID().toString();
    jdbc.update("INSERT INTO metal_factory(factory_name,area,address,create_by) VALUES(?,1,'隔离回归','test')","客户引用回归_"+suffix);
    long factory=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
    jdbc.update("INSERT INTO metal_customer(factory_id,customer_name,company_name,address,contact_name,contact_phone,create_by) VALUES(?,'客户引用回归','隔离公司','隔离地址','测试','13800138000','test')",factory);
    long customer=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
    jdbc.update("INSERT INTO metal_storage_document(factory_id,number,kind,type,creator_id,reviewer_id,business_date,data) VALUES(?,?,'IN','RAW_IN',1,1,CURRENT_DATE,'{}')",factory,"TEST"+suffix);
    long document=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
    jdbc.update("INSERT INTO metal_storage_document_line(factory_id,document_id,sequence_no,data) VALUES(?,?,1,'{}')",factory,document);
    jdbc.update("UPDATE metal_storage_document_line SET data=JSON_OBJECT('ownerId',?) WHERE document_id=?",Long.toString(customer+1),document);
    assertDoesNotThrow(()->WorkOrderReferences.customer(jdbc,customer));
    for(boolean numeric:List.of(false,true))for(String field:List.of("headerCustomer","ownerId","ownerCustomerId","customerId","borrowOwnerId","allocations")){
     Object value=numeric?customer:Long.toString(customer);
     String header=com.alibaba.fastjson2.JSON.toJSONString(field.equals("headerCustomer")?Map.of("customerId",value):Map.of());
     String line=com.alibaba.fastjson2.JSON.toJSONString(field.equals("headerCustomer")?Map.of():field.equals("allocations")?Map.of("allocations",List.of(Map.of("ownerId",value))):Map.of(field,value));
     jdbc.update("UPDATE metal_storage_document SET data=?,deleted=0 WHERE id=?",header,document);
     jdbc.update("UPDATE metal_storage_document_line SET data=? WHERE document_id=?",line,document);
     for(String state:List.of("DRAFT","PENDING_REVIEW","RETURNED","POSTED")){
      jdbc.update("UPDATE metal_storage_document SET status=? WHERE id=?",state,document);
      assertThrows(ServiceException.class,()->WorkOrderReferences.customer(jdbc,customer),field+" "+state+" numeric="+numeric);
     }
     for(String state:List.of("CANCELLED","REVERSED")){
      jdbc.update("UPDATE metal_storage_document SET status=? WHERE id=?",state,document);
      assertDoesNotThrow(()->WorkOrderReferences.customer(jdbc,customer));
     }
     jdbc.update("UPDATE metal_storage_document SET status='DRAFT',deleted=1 WHERE id=?",document);
     assertDoesNotThrow(()->WorkOrderReferences.customer(jdbc,customer));
    }
   }finally{connection.rollback();}
  }
 }
}
