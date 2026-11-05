package com.ruoyi.metal.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import com.ruoyi.metal.security.FactoryScope;

@EnabledIfEnvironmentVariable(named="METAL_FLOW_ISOLATED_JDBC_URL",matches=WarehouseIntegrationEnvironment.URL_PATTERN)
class AcidWashReservationOptionsTest {
 @Test void optionsKeepBookReservedAndAvailableSeparateAndIgnoreReleasedHolds()throws Exception{
  try(var connection=WarehouseIntegrationEnvironment.dataSource().getConnection()){
   var jdbc=new JdbcTemplate(new SingleConnectionDataSource(connection,true)){
    @Override public List<Map<String,Object>> queryForList(String query){return List.of();}
    @Override public List<Map<String,Object>> queryForList(String query,Object...args){return query.contains("FROM metal_inventory")?super.queryForList(query,args):List.of();}
   };
   jdbc.execute("CREATE TEMPORARY TABLE metal_inventory(inventory_id BIGINT,warehouse_id BIGINT,factory_id BIGINT,reference_id BIGINT,code VARCHAR(32),name VARCHAR(32),specification VARCHAR(32),net_weight DECIMAL(20,6),customer_id BIGINT,material_ownership VARCHAR(16),category VARCHAR(16),raw_material_kind VARCHAR(32),del_flag CHAR(1),ownership_status VARCHAR(16))");
   jdbc.execute("CREATE TEMPORARY TABLE metal_material(material_id BIGINT,metal_type_id BIGINT,del_flag CHAR(1))");
   jdbc.execute("CREATE TEMPORARY TABLE metal_warehouse(warehouse_id BIGINT,name VARCHAR(32),del_flag CHAR(1),status CHAR(1))");
   jdbc.execute("CREATE TEMPORARY TABLE metal_customer(customer_id BIGINT,company_name VARCHAR(32))");
   jdbc.execute("CREATE TEMPORARY TABLE metal_stock_reservation(inventory_id BIGINT,weight DECIMAL(20,6),status VARCHAR(16))");
   jdbc.execute("INSERT INTO metal_material VALUES(1,1,'0')");jdbc.execute("INSERT INTO metal_warehouse VALUES(1,'warehouse','0','0')");
   jdbc.execute("INSERT INTO metal_inventory VALUES(1,1,2,1,'A','old','test',100,NULL,'FACTORY','RAW','UNPROCESSED','0','NORMAL'),(2,1,2,1,'B','old','test',10,NULL,'FACTORY','RAW','UNPROCESSED','0','NORMAL')");
   jdbc.execute("INSERT INTO metal_stock_reservation VALUES(1,80,'ACTIVE'),(1,9,'RELEASED'),(2,10,'ACTIVE')");
   var scope=mock(FactoryScope.class);when(scope.isAdmin()).thenReturn(true);var service=new AcidWashService(jdbc,scope,null);
   var rows=(List<Map<String,Object>>)service.options(2L).get("stocks");assertEquals(1,rows.size());assertEquals("1",rows.get(0).get("id"));assertDecimal("100",rows.get(0).get("netWeight"));assertDecimal("80",rows.get(0).get("reservedWeight"));assertDecimal("20",rows.get(0).get("availableWeight"));
   jdbc.execute("UPDATE metal_stock_reservation SET status='RELEASED' WHERE inventory_id=1");rows=(List<Map<String,Object>>)service.options(2L).get("stocks");assertDecimal("100",rows.get(0).get("availableWeight"));assertDecimal("0",rows.get(0).get("reservedWeight"));
  }
 }
 private void assertDecimal(String expected,Object actual){assertNotNull(actual);assertEquals(0,new BigDecimal(expected).compareTo(new BigDecimal(actual.toString())));}
}
