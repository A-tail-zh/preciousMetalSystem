package com.ruoyi.metal.service;

import java.util.*;
import com.ruoyi.metal.catalog.MaterialRecord;
import com.ruoyi.metal.catalog.ListQuery;
import com.ruoyi.metal.mapper.MaterialMapper;
import com.ruoyi.metal.security.FactoryScope;
import com.ruoyi.common.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MaterialNameTest {
 private MaterialRecord material(String name,long metal){var row=new MaterialRecord();row.setId(7L);row.setFactoryId(8L);row.setCode("M1");row.setName(name);row.setMetalTypeId(metal);return row;}
 @Test void namesAreTrimmedAndRequiredWithoutTypeCatalog(){
  try(var factory=jakarta.validation.Validation.buildDefaultValidatorFactory()){
   var validator=factory.getValidator();var row=material("  自定义金属料  ",9L);
   assertEquals("自定义金属料",row.getName());assertTrue(validator.validate(row).isEmpty());
   for(String name:List.of("", "  ", "长".repeat(101))){row.setName(name);assertFalse(validator.validate(row).isEmpty());}
  }
 }
 @Test void referencedMaterialCanBeRenamedButCannotChangeMetal(){
  var jdbc=mock(JdbcTemplate.class);var scope=mock(FactoryScope.class);var storage=mock(StorageConfigurationService.class);
  when(scope.resolveFactory(8L)).thenReturn(8L);
  when(jdbc.queryForList(contains("FROM metal_type WHERE"),any(Object[].class))).thenReturn(List.of(Map.of("metal_type_id",9L)));
  when(jdbc.queryForList(contains("FROM metal_storage_stock"),any(Object[].class))).thenReturn(List.of(Map.of("id",1L)));
  var service=new MaterialService(mock(MaterialMapper.class),scope,jdbc,storage);
  assertDoesNotThrow(()->service.prepare(material("新名称",9L),material("旧名称",9L)));
  assertThrows(ServiceException.class,()->service.prepare(material("新名称",10L),material("旧名称",9L)));
  verify(jdbc,never()).queryForList(contains("metal_material_type"),eq(String.class),anyLong(),anyString());
 }
 @Test void formFiltersRejectUnknownValuesAndNamesUseLiteralPatterns(){
  var q=new ListQuery();q.setName("  金%料_  ");assertEquals("金%料_",q.getName());assertEquals("%金!%料!_%",q.getNamePattern());
  for(String form:List.of("POWDER","UNTREATED","METAL")){q.setMaterialForm(form);assertDoesNotThrow(q::validate);}
  q.setMaterialForm("旧料");assertThrows(ServiceException.class,q::validate);
 }
}
