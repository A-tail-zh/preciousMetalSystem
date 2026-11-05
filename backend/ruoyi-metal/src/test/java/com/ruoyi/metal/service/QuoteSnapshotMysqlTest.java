package com.ruoyi.metal.service;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import com.ruoyi.metal.catalog.CalculationRequest;

@EnabledIfEnvironmentVariable(named="METAL_FLOW_ISOLATED_JDBC_URL",matches=WarehouseIntegrationEnvironment.URL_PATTERN)
class QuoteSnapshotMysqlTest {
 @Test void independentTrialUsesOneRealMysqlSnapshotWhenAnotherConnectionChangesPi()throws Exception{
  var ds=WarehouseIntegrationEnvironment.dataSource();var base=new JdbcTemplate(ds);String table="test_qr_snapshot_"+System.getenv("METAL_FLOW_FACTORY_ID")+"_"+UUID.randomUUID().toString().substring(0,8);assertTrue(table.matches("test_qr_snapshot_[0-9]+_[a-f0-9]{8}"));boolean created=false;long factory=Long.parseLong(System.getenv("METAL_FLOW_FACTORY_ID"));long metal=base.queryForObject("SELECT metal_type_id FROM metal_type WHERE factory_id=? AND name='铱'",Long.class,factory);
  try{
   base.execute("CREATE TABLE "+table+" LIKE metal_factory_volume_rule");created=true;System.out.println("REGISTERED ISOLATED SNAPSHOT TABLE "+table);base.execute("INSERT INTO "+table+" SELECT * FROM metal_factory_volume_rule");base.update("UPDATE "+table+" SET pi_value=3.14 WHERE factory_id="+factory);
   var reader=new JdbcTemplate(ds){
    @Override public List<Map<String,Object>> queryForList(String query,Object...args){return super.queryForList(query.replace("metal_factory_volume_rule",table),args);}
    @Override public Map<String,Object> queryForMap(String query,Object...args){return super.queryForMap(query.replace("metal_factory_volume_rule",table),args);}
   };
   var scope=new com.ruoyi.metal.security.FactoryScope(reader,null){@Override public Long currentFactory(){return factory;}};
   var target=new QuoteRuleService(reader,scope){@Override public Map<String,Object> rule(Long id){var value=super.rule(id);try(var connection=ds.getConnection();var statement=connection.createStatement()){assertTrue(connection.getAutoCommit());statement.executeUpdate("UPDATE "+table+" SET pi_value=3.20,version=version+1 WHERE factory_id="+factory);}catch(java.sql.SQLException e){throw new RuntimeException(e);}return value;}};
   var proxy=new ProxyFactory(target);proxy.setProxyTargetClass(true);var interceptor=new TransactionInterceptor();interceptor.setTransactionManager(new DataSourceTransactionManager(ds));interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());proxy.addAdvice(interceptor);
   var result=((QuoteRuleService)proxy.getProxy()).calculate(new CalculationRequest(null,Map.of("innerDiameter","0","outerDiameter","2","length","1"),1,metal,"杆"),factory);var rule=(Map<?,?>)result.get("rule");var volume=(Map<?,?>)result.get("volumeRule");assertEquals(rule.get("piValue"),volume.get("piValue"));assertEquals(rule.get("piVersion"),volume.get("version"));assertEquals(0,new java.math.BigDecimal("3.14").compareTo(new java.math.BigDecimal(rule.get("piValue").toString())));assertEquals(0,new java.math.BigDecimal("3.20").compareTo(base.queryForObject("SELECT pi_value FROM "+table+" WHERE factory_id="+factory,java.math.BigDecimal.class)));
  }finally{if(created)base.execute("DROP TABLE "+table);}
 }
}
