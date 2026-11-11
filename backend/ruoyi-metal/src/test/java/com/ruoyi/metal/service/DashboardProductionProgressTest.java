package com.ruoyi.metal.service;

import java.util.*;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import com.ruoyi.metal.security.FactoryScope;
import com.ruoyi.common.exception.ServiceException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DashboardProductionProgressTest {
 @Test void validatesPaginationAndFactoryBeforeReading(){
  var jdbc=mock(JdbcTemplate.class);var scope=mock(FactoryScope.class);
  var service=new DashboardService(jdbc,scope,null);
  assertThrows(ServiceException.class,()->service.productionProgress(null,0,10));
  assertThrows(ServiceException.class,()->service.productionProgress(null,1,51));
  when(scope.currentFactory()).thenReturn(7L);
  doThrow(new ServiceException("跨厂拒绝",403)).when(scope).check(8L);
  assertThrows(ServiceException.class,()->service.productionProgress(8L,1,10));
  assertThrows(ServiceException.class,()->service.productionProgress(null,1,10,"x".repeat(33),null));
  assertThrows(ServiceException.class,()->service.productionProgress(null,1,10,null,"COMPLETED"));
  verifyNoInteractions(jdbc);
 }

 // Session-local temporary tables shadow real tables; closing the connection removes every fixture.
 @Test @EnabledIfEnvironmentVariable(named="TEST_DASHBOARD_JDBC_URL",matches=".+")
 void mysqlProgressCountsPostedOutputsAcrossOpenAndClosedBatches() throws Exception {
  try(var connection=DriverManager.getConnection(System.getenv("TEST_DASHBOARD_JDBC_URL"),System.getenv("TEST_DASHBOARD_DB_USER"),System.getenv("TEST_DASHBOARD_DB_PASSWORD"))){
   var jdbc=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
   jdbc.execute("CREATE TEMPORARY TABLE metal_production_order(id BIGINT PRIMARY KEY,order_no VARCHAR(32),factory_id BIGINT,factory_name VARCHAR(100),status VARCHAR(30),flow_version INT,deleted INT,create_time DATETIME)");
   jdbc.execute("CREATE TEMPORARY TABLE metal_production_order_item(id BIGINT PRIMARY KEY,order_id BIGINT,sequence_no INT,quantity INT,product_snapshot JSON)");
   jdbc.execute("CREATE TEMPORARY TABLE metal_production_order_step(id BIGINT PRIMARY KEY,item_id BIGINT,sequence_no INT,process_name VARCHAR(100),measurement_mode VARCHAR(10),output_category VARCHAR(10))");
   jdbc.execute("CREATE TEMPORARY TABLE metal_production_execution(id BIGINT PRIMARY KEY,factory_id BIGINT,order_id BIGINT,order_item_id BIGINT,order_step_id BIGINT,status VARCHAR(10),output_quantity BIGINT)");
   jdbc.execute("INSERT INTO metal_production_order VALUES (1,'A',7,'Factory A','IN_PROGRESS',1,0,'2026-11-09'),(2,'B',7,'Factory A','CREATED',1,0,'2026-11-08'),(3,'C',7,'Factory A','COMPLETED',1,0,'2026-11-09'),(4,'D',7,'Factory A','EXCEPTION_COMPLETED',1,0,'2026-11-09'),(5,'E',7,'Factory A','CREATED',1,1,'2026-11-09'),(6,'F',8,'Factory B','IN_PROGRESS',1,0,'2026-11-09'),(7,'G',7,'Factory A','CREATED',0,0,'2026-11-07')");
   jdbc.execute("INSERT INTO metal_production_order_item VALUES (11,1,1,100,JSON_OBJECT('name','Silver rod','specification','10mm')),(12,1,2,200,JSON_OBJECT('name','Silver ring')),(21,2,1,100,JSON_OBJECT('name','Unstarted')),(71,7,1,50,JSON_OBJECT('name','Legacy'))");
   jdbc.execute("INSERT INTO metal_production_order_step VALUES (111,11,1,'First','PIECE','SEMI'),(112,11,2,'Final','PIECE','PRODUCT'),(121,12,1,'Smelting','WEIGHT','RAW'),(122,12,2,'Final ring','PIECE','PRODUCT'),(211,21,1,'Unstarted step','PIECE','PRODUCT')");
   jdbc.execute("INSERT INTO metal_production_execution VALUES (1,7,1,11,111,'OPEN',30),(2,7,1,11,111,'CLOSED',20),(3,7,1,11,112,'OPEN',10),(4,7,1,12,121,'CLOSED',0),(5,7,1,12,122,'OPEN',7)");
   jdbc.execute("INSERT INTO metal_production_execution VALUES (6,7,1,11,111,'CANCELLED',1000),(7,8,1,11,111,'OPEN',1000)");
   var scope=mock(FactoryScope.class);when(scope.currentFactory()).thenReturn(7L);
   var service=new DashboardService(jdbc,scope,null);
   var result=service.productionProgress(null,1,10);
   assertEquals(3L,result.get("total"));
   assertEquals(1L,service.productionProgress(null,1,3,"A","IN_PROGRESS").get("total"));
   assertEquals(2L,service.productionProgress(null,1,3,null,"CREATED").get("total"));
   assertEquals(0L,service.productionProgress(null,1,3,"%",null).get("total"));
   assertEquals(0L,service.productionProgress(null,1,3,"F",null).get("total"));
   var orders=rows(result,"rows");assertEquals(List.of("A","B","G"),orders.stream().map(o->o.get("orderNo")).toList());
   var products=rows(orders.get(0),"products");assertEquals(2,products.size());
   assertEquals("10",products.get(0).get("completedQuantity"));assertEquals("7",products.get(1).get("completedQuantity"));
   assertEquals("50",rows(products.get(0),"steps").get(0).get("completedQuantity"));
   assertEquals("WEIGHT",rows(products.get(1),"steps").get(0).get("measurementMode"));
   assertEquals("0",rows(orders.get(1),"products").get(0).get("completedQuantity"));
   assertEquals(0,orders.get(2).get("flowVersion"));
   assertEquals("B",rows(service.productionProgress(null,2,1),"rows").get(0).get("orderNo"));
   assertTrue(rows(service.productionProgress(null,Integer.MAX_VALUE,50),"rows").isEmpty());
   jdbc.update("UPDATE metal_production_order SET status='COMPLETED' WHERE id=1");
   assertEquals(2L,service.productionProgress(null,1,10).get("total"));
   when(scope.currentFactory()).thenReturn(null);
   assertEquals(3L,service.productionProgress(null,1,10).get("total"));
   assertEquals("F",rows(service.productionProgress(8L,1,10),"rows").get(0).get("orderNo"));
   verify(scope).check(8L);
  }
 }
 @SuppressWarnings("unchecked") private static List<Map<String,Object>> rows(Map<String,Object> map,String key){return (List<Map<String,Object>>)map.get(key);}
}
