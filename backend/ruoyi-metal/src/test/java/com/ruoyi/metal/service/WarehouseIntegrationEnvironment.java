package com.ruoyi.metal.service;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

final class WarehouseIntegrationEnvironment {
 private WarehouseIntegrationEnvironment(){}
 static DriverManagerDataSource dataSource(){
  String url=System.getenv("METAL_FLOW_ISOLATED_JDBC_URL");
  if(url==null||!url.matches("jdbc:mysql://(?:127\\.0\\.0\\.1|localhost):(?:3310/heavy_metal_plan_upgrade_20261022|3321/(?:heavy_metal_flow|heavy_metal_factory_20261104))(?:\\?.*)?"))throw new IllegalStateException("Only the explicitly registered isolated schema is allowed");
  boolean current=url.contains(":3321/");var ds=new DriverManagerDataSource(url,current?System.getenv("METAL_FLOW_ISOLATED_JDBC_USER"):"root",current?System.getenv("METAL_FLOW_ISOLATED_JDBC_PASSWORD"):"");
  if(current){String prefix=System.getenv("METAL_FLOW_FIXTURE_PREFIX"),factory=System.getenv("METAL_FLOW_FACTORY_ID");if(prefix==null||!prefix.matches("工单验收_[0-9]+_[a-f0-9]{6}")||factory==null||!factory.matches("[0-9]+"))throw new IllegalStateException("A registered fixture is required");if(!new JdbcTemplate(ds).queryForObject("SELECT factory_name FROM metal_factory WHERE factory_id=?",String.class,Long.valueOf(factory)).equals(prefix+"厂A"))throw new IllegalStateException("Fixture ownership mismatch");}
  return ds;
 }
 static String factoryFilter(String alias){String id=System.getenv("METAL_FLOW_FACTORY_ID");if(id==null)return "";if(!id.matches("[0-9]+"))throw new IllegalStateException("Invalid fixture factory");return " AND "+(alias.isBlank()?"":alias+".")+"factory_id="+id;}
}
