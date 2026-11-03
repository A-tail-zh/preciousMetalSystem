package com.ruoyi.metal.service;

import java.util.*;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.metal.catalog.LineRecord;
import com.ruoyi.metal.catalog.MaterialRecord;
import com.ruoyi.metal.mapper.LineMapper;
import com.ruoyi.metal.mapper.MaterialMapper;
import com.ruoyi.metal.security.FactoryScope;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RawTaskReferenceTest {
 private Map<String,Object> rawItem(){var row=new HashMap<String,Object>();row.put("product_id",null);return row;}
 private JdbcTemplate lineItems(List<Map<String,Object>> rows){var jdbc=mock(JdbcTemplate.class);when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(rows);return jdbc;}
 @Test void pureRawTaskDoesNotPreventRenamingItsProductionLine(){
  var jdbc=lineItems(List.of(rawItem()));var service=new LineService(mock(LineMapper.class),mock(FactoryScope.class),jdbc);
  var old=new LineRecord();old.setId(1L);old.setFactoryId(2L);old.setProductIds(List.of());
  var changed=new LineRecord();changed.setId(1L);changed.setFactoryId(2L);changed.setProductIds(List.of());changed.setName("新的产线名称");
  assertDoesNotThrow(()->service.prepare(changed,old));
 }
 @Test void mixedRawAndProductOrdersCanRetainTheReferencedProduct(){
  assertDoesNotThrow(()->WorkOrderReferences.lineProducts(lineItems(List.of(rawItem(),Map.of("product_id",3L))),1L,List.of(3L)));
 }
 @Test void mixedRawAndProductOrdersStillCannotRemoveTheReferencedProduct(){
  assertThrows(ServiceException.class,()->WorkOrderReferences.lineProducts(lineItems(List.of(rawItem(),Map.of("product_id",3L))),1L,List.of()));
 }
 @Test void rawOrdersStillProtectLineFactoryAndDeletion(){
  var jdbc=lineItems(List.of(Map.of("work_order_id",4L)));
  assertThrows(ServiceException.class,()->WorkOrderReferences.lineMove(jdbc,1L));
  assertThrows(ServiceException.class,()->WorkOrderReferences.lineDelete(jdbc,1L));
 }
 private MaterialRecord material(String type,long metal){var row=new MaterialRecord();row.setId(7L);row.setMaterialType(type);row.setMetalTypeId(metal);return row;}
 @Test void unissuedRawTaskProtectsMaterialDeletionAndClassification(){
  var jdbc=mock(JdbcTemplate.class);when(jdbc.queryForList(contains("FROM metal_type WHERE"),any(Object[].class))).thenReturn(List.of(Map.of("metal_type_id",1L)));
  when(jdbc.queryForList(contains("FROM metal_work_order_item"),any(Object[].class))).thenReturn(List.of(Map.of("item_id",8L)));
  var service=new MaterialService(mock(MaterialMapper.class),mock(FactoryScope.class),jdbc);
  assertTrue(WarehouseReferences.materialReferenced(jdbc,7L));
  assertTrue(assertThrows(ServiceException.class,()->service.deletable(material("粉料",1))).getMessage().contains("工单"));
  assertTrue(assertThrows(ServiceException.class,()->service.prepare(material("块料",1),material("粉料",1))).getMessage().contains("工单"));
  assertThrows(ServiceException.class,()->service.prepare(material("粉料",2),material("粉料",1)));
  assertDoesNotThrow(()->service.prepare(material("粉料",1),material("粉料",1)));
 }
 @Test void unreferencedMaterialsCanStillBeDeletedOrReclassified(){
  var jdbc=mock(JdbcTemplate.class);when(jdbc.queryForList(contains("FROM metal_type WHERE"),any(Object[].class))).thenReturn(List.of(Map.of("metal_type_id",1L)));
  var service=new MaterialService(mock(MaterialMapper.class),mock(FactoryScope.class),jdbc);
  assertFalse(WarehouseReferences.materialReferenced(jdbc,7L));assertDoesNotThrow(()->service.deletable(material("粉料",1)));assertDoesNotThrow(()->service.prepare(material("块料",2),material("粉料",1)));
 }
}
