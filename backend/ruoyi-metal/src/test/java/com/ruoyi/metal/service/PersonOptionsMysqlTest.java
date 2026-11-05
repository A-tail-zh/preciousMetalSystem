package com.ruoyi.metal.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="METAL_FLOW_ISOLATED_JDBC_URL",matches=WarehouseIntegrationEnvironment.URL_PATTERN)
class PersonOptionsMysqlTest {
 @Test void roleNameSearchWorksWithMixedSystemTableCollations(){
  var jdbc=new JdbcTemplate(WarehouseIntegrationEnvironment.dataSource());
  String prefix=System.getenv("METAL_FLOW_FIXTURE_PREFIX"),label=PersonOptions.label("sys_user");
  var rows=jdbc.queryForList("SELECT "+label+" name FROM sys_user WHERE factory_id=? AND "+label+" LIKE ? ESCAPE '!'",System.getenv("METAL_FLOW_FACTORY_ID"),"%"+prefix+"角色A%");
  assertEquals(1,rows.size());assertTrue(rows.get(0).get("name").toString().contains(prefix+"角色A"));
 }
}
