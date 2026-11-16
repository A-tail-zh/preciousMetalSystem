package com.ruoyi.metal.service;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="PROCESS_TYPE_TEST_JDBC_URL",matches="jdbc:mysql://127\\.0\\.0\\.1:3323/production_batch_p4_20261112_[a-f0-9]{10}(?:\\?.*)?")
class ProcessTypeMigrationMysqlTest {
 JdbcTemplate jdbc;TransactionTemplate tx;
 @BeforeAll static void requireEmptyIsolatedSchema(){
  var ds=new DriverManagerDataSource(System.getenv("PROCESS_TYPE_TEST_JDBC_URL"),System.getenv("PROCESS_TYPE_TEST_USER"),System.getenv("PROCESS_TYPE_TEST_PASSWORD"));
  var jdbc=new JdbcTemplate(ds);
  assertEquals(3323,jdbc.queryForObject("SELECT @@port",Integer.class));
  assertTrue(jdbc.queryForObject("SELECT DATABASE()",String.class).matches("production_batch_p4_20261112_[a-f0-9]{10}"));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()",Integer.class),"Refuse to modify a used schema");
 }
 @BeforeEach void setup()throws Exception{
  var ds=new DriverManagerDataSource(System.getenv("PROCESS_TYPE_TEST_JDBC_URL"),System.getenv("PROCESS_TYPE_TEST_USER"),System.getenv("PROCESS_TYPE_TEST_PASSWORD"));
  jdbc=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
  assertTrue(jdbc.queryForObject("SELECT DATABASE()",String.class).matches("production_batch_p4_20261112_[a-f0-9]{10}"));assertEquals(3323,jdbc.queryForObject("SELECT @@port",Integer.class));
  for(String table:List.of("metal_production_order_step","metal_product_process","metal_process","metal_process_type","metal_factory"))jdbc.execute("DROP TABLE IF EXISTS "+table);
  jdbc.execute("CREATE TABLE metal_factory(factory_id BIGINT PRIMARY KEY,del_flag CHAR(1) NOT NULL DEFAULT '0')");
  jdbc.execute("CREATE TABLE metal_process_type(type_id INT AUTO_INCREMENT PRIMARY KEY,factory_id BIGINT NULL,type_name VARCHAR(100) NOT NULL,version INT DEFAULT 0,create_by VARCHAR(64),update_by VARCHAR(64),create_time DATETIME DEFAULT CURRENT_TIMESTAMP,update_time DATETIME DEFAULT CURRENT_TIMESTAMP,UNIQUE(factory_id,type_name))");
  jdbc.execute("CREATE TABLE metal_process(process_id BIGINT PRIMARY KEY,factory_id BIGINT NULL,process_name VARCHAR(100),process_type INT,semantic_code VARCHAR(16),version INT DEFAULT 0,update_by VARCHAR(64),update_time DATETIME,del_flag CHAR(1) DEFAULT '0')");
  jdbc.execute("CREATE TABLE metal_product_process(process_id BIGINT)");
  jdbc.execute("CREATE TABLE metal_production_order_step(process_id BIGINT,semantic_code VARCHAR(16),snapshot JSON)");
  jdbc.execute(Files.readString(Path.of("../ruoyi-admin/src/main/resources/db/migration/V72__builtin_process_type_schema.sql")));
  jdbc.update("INSERT INTO metal_factory VALUES(7,'0'),(8,'0'),(9,'1')");
  jdbc.update("INSERT INTO metal_process_type(type_id,factory_id,type_name) VALUES(11,7,'混合旧类型'),(12,7,'融定'),(13,NULL,'历史公共'),(14,9,'已删工厂')");
  jdbc.update("INSERT INTO metal_process(process_id,factory_id,process_name,process_type,semantic_code) VALUES(21,7,'清理',11,'ACID_WASH'),(22,7,'焊接',11,'NORMAL'),(23,7,'融定',12,'SMELTING'),(24,NULL,'历史工序',13,NULL),(25,9,'删除工厂工序',14,NULL)");
  jdbc.update("INSERT INTO metal_production_order_step VALUES(21,'ACID_WASH',JSON_OBJECT('processType',11,'semanticCode','ACID_WASH'))");
 }
 void migrate(){tx.executeWithoutResult(status->ProcessTypeMigration.migrate(jdbc));}
 @Test void remapsMixedTypesWithoutChangingHistoricalSnapshotAndIsRepeatable(){
  var history=jdbc.queryForList("SELECT * FROM metal_production_order_step");
  migrate();assertEquals(4,jdbc.queryForObject("SELECT COUNT(*) FROM metal_process_type WHERE builtin_code IS NOT NULL",Integer.class));
  assertEquals("ACID_WASH",jdbc.queryForObject("SELECT t.builtin_code FROM metal_process p JOIN metal_process_type t ON t.type_id=p.process_type WHERE p.process_id=21",String.class));
  assertEquals(11,jdbc.queryForObject("SELECT process_type FROM metal_process WHERE process_id=22",Integer.class));
  assertEquals(12,jdbc.queryForObject("SELECT process_type FROM metal_process WHERE process_id=23",Integer.class));
  assertEquals(1,jdbc.queryForObject("SELECT version FROM metal_process WHERE process_id=21",Integer.class));
  assertEquals(history,jdbc.queryForList("SELECT * FROM metal_production_order_step"));
  var types=jdbc.queryForList("SELECT * FROM metal_process_type ORDER BY type_id");migrate();assertEquals(types,jdbc.queryForList("SELECT * FROM metal_process_type ORDER BY type_id"));
  assertNull(jdbc.queryForObject("SELECT semantic_code FROM metal_process WHERE process_id=24",String.class));
 }
 @Test void missingRuleAndReservedNameConflictBlockBeforeAnyDataChange(){
  for(String invalid:List.of("UPDATE metal_process SET semantic_code=NULL WHERE process_id=21","UPDATE metal_process SET semantic_code='NORMAL' WHERE process_id=23")){
   tx.executeWithoutResult(status->{jdbc.update(invalid);var before=jdbc.queryForList("SELECT * FROM metal_process_type ORDER BY type_id");assertTrue(ProcessTypeMigration.inspect(jdbc).stream().anyMatch(r->r.get("issue")!=null));assertThrows(IllegalStateException.class,()->ProcessTypeMigration.migrate(jdbc));assertEquals(before,jdbc.queryForList("SELECT * FROM metal_process_type ORDER BY type_id"));status.setRollbackOnly();});
  }
 }
 @Test void databaseRejectsDuplicateOrMisnamedBuiltinsAndMigrationRollsBack(){
  tx.executeWithoutResult(status->{
   ProcessTypeMigration.migrate(jdbc);
   assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM metal_process_type WHERE factory_id=8",Integer.class));
   var misnamed=assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("INSERT INTO metal_process_type(factory_id,type_name,builtin_code) VALUES(8,'伪造','ACID_WASH')"));
   assertEquals(3819,assertInstanceOf(java.sql.SQLException.class,misnamed.getMostSpecificCause()).getErrorCode());
   assertThrows(org.springframework.dao.DuplicateKeyException.class,()->jdbc.update("INSERT INTO metal_process_type(factory_id,type_name,builtin_code) VALUES(8,'酸洗','ACID_WASH')"));
   ProcessTypeMigration.migrate(jdbc);
   assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM metal_process_type WHERE factory_id=8",Integer.class));
   status.setRollbackOnly();
  });
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM metal_process_type WHERE factory_id=8",Integer.class));
 }
 @Test void historicalReverseReferencesBlockBuiltinConversion(){
  for(long id:List.of(24L,25L))tx.executeWithoutResult(status->{
   jdbc.update("UPDATE metal_process SET process_type=12,semantic_code='NORMAL' WHERE process_id=?",id);
   assertThrows(IllegalStateException.class,()->ProcessTypeMigration.migrate(jdbc));
   assertNull(jdbc.queryForObject("SELECT builtin_code FROM metal_process_type WHERE type_id=12",String.class));
   status.setRollbackOnly();
  });
 }
}
