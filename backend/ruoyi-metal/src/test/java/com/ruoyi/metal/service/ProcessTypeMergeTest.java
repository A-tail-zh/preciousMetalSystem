package com.ruoyi.metal.service;

import java.util.*;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.metal.catalog.ProcessRecord;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ProcessTypeMergeTest {
 @Test void reservedNamesCannotBeCreatedAsCustomTypes(){
  for(String name:List.of("酸洗"," 融定 "))assertThrows(ServiceException.class,()->ProcessTypeService.validName(name));
  assertEquals("精酸洗",ProcessTypeService.validName("精酸洗"));
 }
 @Test void typeManagementProcessApiIsRejected(){
  var jdbc=mock(JdbcTemplate.class);var scope=FactoryCatalogTestFixtures.scope();when(scope.isAdmin()).thenReturn(true);
  var service=new TypeManagementService(jdbc,scope);
  var error=assertThrows(ServiceException.class,()->service.list("process",7L,"",1,16));
  assertTrue(error.getMessage().contains("工序管理"));
  verify(jdbc,never()).queryForList(anyString(),any(Object[].class));
 }
 @Test void missingConfigurationStillDerivesRuleFromFactoryType(){
  for(String semantic:List.of("NORMAL","ACID_WASH","SMELTING")){
   var jdbc=mock(JdbcTemplate.class);var type=new HashMap<String,Object>();type.put("builtin_code",semantic.equals("NORMAL")?null:semantic);
   when(jdbc.queryForList(contains("FROM metal_process_type"),eq(12),eq(7L))).thenReturn(List.of(type));
   var form=new ProcessRecord();form.setFactoryId(7L);form.setProcessType(12);
   new StorageConfigurationService(jdbc,FactoryCatalogTestFixtures.scope()).prepareCatalog("process",form);
   assertNotNull(form.getStorageConfiguration());assertEquals(semantic,form.getStorageConfiguration().get("semanticCode"));
  }
 }
 @Test void suppliedRuleCannotOverrideType(){
  var jdbc=mock(JdbcTemplate.class);
  when(jdbc.queryForList(contains("FROM metal_process_type"),eq(12),eq(7L))).thenReturn(List.of(Map.of("builtin_code","ACID_WASH")));
  var form=new ProcessRecord();form.setFactoryId(7L);form.setProcessType(12);form.setStorageConfiguration(Map.of("semanticCode","NORMAL"));
  assertEquals(409,assertThrows(ServiceException.class,()->new StorageConfigurationService(jdbc,FactoryCatalogTestFixtures.scope()).prepareCatalog("process",form)).getCode());
  verify(jdbc,never()).update(anyString(),any(Object[].class));
 }
 @Test void builtinDeleteRejectsEvenWithoutReferences(){
  var jdbc=mock(JdbcTemplate.class);var scope=FactoryCatalogTestFixtures.scope();
  when(jdbc.queryForList(contains("FOR UPDATE"),eq(12L),eq(7L))).thenReturn(List.of(Map.of("id",12L,"name","酸洗","version",0,"builtinCode","ACID_WASH")));
  var service=new ProcessTypeService(jdbc,scope);
  var error=assertThrows(ServiceException.class,()->service.remove(12L,0,7L));
  assertTrue(error.getMessage().contains("内置"));
  verify(jdbc,never()).update(anyString(),any(Object[].class));
 }
 @Test void referencedTypeBlocksRenameAndDelete(){
  var jdbc=mock(JdbcTemplate.class);var scope=FactoryCatalogTestFixtures.scope();
  when(jdbc.queryForList(contains("FOR UPDATE"),eq(11L),eq(7L))).thenReturn(List.of(Map.of("id",11L,"name","打磨","version",0)));
  when(jdbc.queryForObject(eq("SELECT COUNT(*) FROM metal_process WHERE process_type=?"),eq(Integer.class),eq(11L))).thenReturn(1);
  var service=new ProcessTypeService(jdbc,scope);
  assertEquals(409,assertThrows(ServiceException.class,()->service.edit(11L,"清洗",0,7L)).getCode());
  assertEquals(409,assertThrows(ServiceException.class,()->service.remove(11L,0,7L)).getCode());
  verify(jdbc,never()).update(anyString(),any(Object[].class));
 }
 @Test void executionLocksRuleEvenWhenNewClientOmitsConfiguration(){
  var jdbc=mock(JdbcTemplate.class);
  when(jdbc.queryForList(contains("FROM metal_process_type"),eq(12),eq(7L))).thenReturn(List.of(Map.of("builtin_code","ACID_WASH")));
  when(jdbc.queryForList(startsWith("SELECT * FROM metal_process WHERE"),eq(21L),eq(7L))).thenReturn(List.of(Map.of("semantic_code","NORMAL")));
  when(jdbc.queryForObject(startsWith("SELECT COUNT(*) FROM metal_production_order_step"),eq(Integer.class),eq(21L))).thenReturn(1);
  var form=new ProcessRecord();form.setId(21L);form.setFactoryId(7L);form.setProcessType(12);
  assertEquals(409,assertThrows(ServiceException.class,()->new StorageConfigurationService(jdbc,FactoryCatalogTestFixtures.scope()).prepareCatalog("process",form)).getCode());
  verify(jdbc,never()).update(anyString(),any(Object[].class));
 }
 @Test void compatibilityEndpointRejectsIndependentRuleChange(){
  var jdbc=mock(JdbcTemplate.class);var scope=FactoryCatalogTestFixtures.scope();when(scope.isAdmin()).thenReturn(true);
  when(jdbc.queryForList(startsWith("SELECT * FROM metal_process WHERE"),eq(21L),eq(7L))).thenReturn(List.of(Map.of("semantic_code","ACID_WASH","process_type",12,"version",0)));
  when(jdbc.queryForList(contains("FROM metal_process_type"),eq(12),eq(7L))).thenReturn(List.of(Map.of("builtin_code","ACID_WASH")));
  when(jdbc.queryForObject(anyString(),eq(Integer.class),any(Object[].class))).thenReturn(0);
  assertEquals(409,assertThrows(ServiceException.class,()->new StorageConfigurationService(jdbc,scope).save("process",21L,Map.of("factoryId",7L,"version",0,"semanticCode","SMELTING"))).getCode());
  verify(jdbc,never()).update(anyString(),any(Object[].class));
 }
 @Test void foreignOrMissingTypeNeverDefaultsToNormal(){
  var form=new ProcessRecord();form.setFactoryId(7L);form.setProcessType(99);
  assertThrows(ServiceException.class,()->new StorageConfigurationService(mock(JdbcTemplate.class),FactoryCatalogTestFixtures.scope()).prepareCatalog("process",form));
 }
 @Test void missingProductConfigurationOnlyPreservesTheSameCompleteRoute(){
  var jdbc=mock(JdbcTemplate.class);var service=new StorageConfigurationService(jdbc,FactoryCatalogTestFixtures.scope());
  var product=new com.ruoyi.metal.catalog.ProductRecord();product.setFactoryId(7L);
  var step=new com.ruoyi.metal.catalog.ProductStep();step.setProcessId(1L);product.setProcesses(List.of(step));
  assertThrows(ServiceException.class,()->service.prepareCatalog("product",product));
  product.setId(31L);
  when(jdbc.queryForList(startsWith("SELECT CAST(pp.process_id AS CHAR)"),eq(31L))).thenReturn(List.of(Map.of("processId","1","sequenceNo",1,"measurementMode","PIECE","outputCategory","PRODUCT")));
  when(jdbc.queryForList(startsWith("SELECT * FROM metal_product WHERE"),eq(31L),eq(7L))).thenReturn(List.of(Map.of("version",0)));
  when(jdbc.queryForList(startsWith("SELECT * FROM metal_process WHERE"),eq(1L),eq(7L))).thenReturn(List.of(Map.of("semantic_code","NORMAL","process_name","完成")));
  when(jdbc.queryForObject(anyString(),eq(Integer.class),any(Object[].class))).thenReturn(0);
  service.prepareCatalog("product",product);assertNotNull(product.getStorageConfiguration());
  product.setStorageConfiguration(null);step.setProcessId(2L);assertThrows(ServiceException.class,()->service.prepareCatalog("product",product));
  verify(jdbc,never()).update(anyString(),any(Object[].class));
 }
}
