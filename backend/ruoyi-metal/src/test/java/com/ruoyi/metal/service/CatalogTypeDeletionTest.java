package com.ruoyi.metal.service;

import java.util.List;
import java.util.Map;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.metal.security.FactoryScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatalogTypeDeletionTest {
 private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
 private final FactoryScope scope=FactoryCatalogTestFixtures.scope();
 private void ready(String table,String key){
  when(jdbc.queryForObject(anyString(),eq(Integer.class),any(Object[].class))).thenReturn(0);
  when(jdbc.queryForList("SELECT "+key+" FROM "+table+" WHERE "+key+"=? AND factory_id=? FOR UPDATE",10L,7L)).thenReturn(List.of(Map.of(key,10L)));
  when(jdbc.update("DELETE FROM "+table+" WHERE "+key+"=? AND factory_id=?",10L,7L)).thenReturn(1);
 }
 @Test void unusedProcessTypeCanBeDeletedWithinFactory(){
  ready("metal_process_type","type_id");new ProcessTypeService(jdbc,scope).remove(10L);
  verify(scope).lockFactories(List.of(7L));verify(jdbc).update("DELETE FROM metal_process_type WHERE type_id=? AND factory_id=?",10L,7L);
 }
 @Test void processReferencesIncludeHistoricalDeletedProcesses(){
  ready("metal_process_type","type_id");when(jdbc.queryForObject("SELECT COUNT(*) FROM metal_process WHERE process_type=?",Integer.class,10L)).thenReturn(1);
  assertTrue(assertThrows(ServiceException.class,()->new ProcessTypeService(jdbc,scope).remove(10L)).getMessage().contains("已被工序引用"));
  verify(jdbc,never()).update(anyString(),any(Object[].class));
 }
 @Test void unusedMetalCanBeDeletedWithinFactory(){
  ready("metal_type","metal_type_id");new QuoteRuleService(jdbc,scope).removeMetal(10L);
  verify(scope).lockFactories(List.of(7L));verify(jdbc).update("DELETE FROM metal_type WHERE metal_type_id=? AND factory_id=?",10L,7L);
 }
 @ParameterizedTest @ValueSource(strings={"metal_product","metal_material","metal_type_quote_rule","metal_process_batch"})
 void referencedMetalCannotBeDeleted(String table){
  ready("metal_type","metal_type_id");when(jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE metal_type_id=?",Integer.class,10L)).thenReturn(1);
  assertTrue(assertThrows(ServiceException.class,()->new QuoteRuleService(jdbc,scope).removeMetal(10L)).getMessage().contains("不能删除"));
  verify(jdbc,never()).update(anyString(),any(Object[].class));
 }
 @Test void stockSnapshotProtectsMetal(){
  ready("metal_type","metal_type_id");when(jdbc.queryForObject("SELECT COUNT(*) FROM metal_inventory WHERE metal_type_id_snapshot=?",Integer.class,10L)).thenReturn(1);
  assertThrows(ServiceException.class,()->new QuoteRuleService(jdbc,scope).removeMetal(10L));verify(jdbc,never()).update(anyString(),any(Object[].class));
 }
 @Test void savedQuotationProtectsStringAndNumericMetalIds(){
  ready("metal_type","metal_type_id");when(jdbc.queryForObject(contains("JSON_CONTAINS"),eq(Integer.class),eq("10"),eq("10"))).thenReturn(1);
  assertThrows(ServiceException.class,()->new QuoteRuleService(jdbc,scope).removeMetal(10L));verify(jdbc,never()).update(anyString(),any(Object[].class));
 }
 @Test void crossFactoryRequestsStopBeforeReadingOrDeleting(){
  when(scope.catalogFactory(anyString(),eq(10L))).thenThrow(new ServiceException("无权访问",403));
  assertThrows(ServiceException.class,()->new ProcessTypeService(jdbc,scope).remove(10L));assertThrows(ServiceException.class,()->new QuoteRuleService(jdbc,scope).removeMetal(10L));verifyNoInteractions(jdbc);
 }
 @Test void missingTypeIsReportedWithoutDelete(){
  assertThrows(ServiceException.class,()->new ProcessTypeService(jdbc,scope).remove(10L));assertThrows(ServiceException.class,()->new QuoteRuleService(jdbc,scope).removeMetal(10L));verify(jdbc,never()).update(anyString(),any(Object[].class));
 }
 @Test void concurrentForeignKeyReferencesReturnChineseErrors(){
  ready("metal_type","metal_type_id");ready("metal_process_type","type_id");when(jdbc.update(startsWith("DELETE"),eq(10L),eq(7L))).thenThrow(new DataIntegrityViolationException("internal SQL"));
  assertEquals("金属类型已被引用，不能删除",assertThrows(ServiceException.class,()->new QuoteRuleService(jdbc,scope).removeMetal(10L)).getMessage());
  assertEquals("工序类型已被引用，不能删除",assertThrows(ServiceException.class,()->new ProcessTypeService(jdbc,scope).remove(10L)).getMessage());
 }
}
