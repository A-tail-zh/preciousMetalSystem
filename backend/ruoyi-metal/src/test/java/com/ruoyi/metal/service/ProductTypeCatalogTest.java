package com.ruoyi.metal.service;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ruoyi.metal.catalog.ProductRecord;
import com.ruoyi.common.exception.ServiceException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ProductTypeCatalogTest {
 ProductRecord row(){var p=new ProductRecord();p.setFactoryId(7L);p.setMetalTypeId(1L);p.setProductType("环");p.setCalculationVersion(2);p.setCategory("铂环");p.setDimensions(Map.of("outerDiameter","4","innerDiameter","2","thickness","1"));return p;}
 QuoteRuleService rules(){var r=mock(QuoteRuleService.class);when(r.metalName(1L)).thenReturn("铂");when(r.volumeRuleForMetal(1L)).thenReturn(Map.of("piValue","3.14"));when(r.rule(1L)).thenReturn(Map.of("density","21.45"));return r;}
 ProductService service(JdbcTemplate j){return new ProductService(null,FactoryCatalogTestFixtures.scope(),j,rules(),mock(StorageConfigurationService.class));}
 @Test void invalidFixedTypeIsRejectedWithoutCatalogLookup(){
  var j=mock(JdbcTemplate.class);var bad=row();bad.setProductType("自定义");bad.setCategory("铂自定义");
  assertEquals(400,assertThrows(ServiceException.class,()->service(j).prepare(bad,null)).getCode());
  verify(j,never()).queryForList(contains("metal_product_type"),eq(String.class),anyLong(),anyString());
 }
 @Test void fixedTypeSavesWithoutFactoryProductCatalog(){
  var j=mock(JdbcTemplate.class);var s=service(j);
  assertDoesNotThrow(()->s.prepare(row(),row()));
  assertDoesNotThrow(()->s.prepare(row(),null));
  verify(j,never()).queryForList(contains("metal_product_type"),eq(String.class),anyLong(),anyString());
 }
 @Test void changingBetweenFixedTypesDoesNotNeedCatalogMembership(){
  var j=mock(JdbcTemplate.class);var old=row();old.setProductType("杆");old.setCategory("铂杆");
  assertDoesNotThrow(()->service(j).prepare(row(),old));
  verify(j,never()).queryForList(contains("metal_product_type"),eq(String.class),anyLong(),anyString());
 }
}
