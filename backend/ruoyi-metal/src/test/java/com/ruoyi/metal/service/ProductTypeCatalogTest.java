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
 QuoteRuleService rules(){var r=mock(QuoteRuleService.class);when(r.metalName(1L)).thenReturn("铂");when(r.volumeRuleForMetal(1L)).thenReturn(Map.of("piValue","3.14"));when(r.rule(1L)).thenReturn(Map.of());return r;}
 @Test void newProductRequiresFactoryCatalogAfterFactoryLock(){var j=mock(JdbcTemplate.class);var scope=FactoryCatalogTestFixtures.scope();var s=new ProductService(null,scope,j,rules());assertEquals(400,assertThrows(ServiceException.class,()->s.prepare(row(),null)).getCode());var order=inOrder(scope,j);order.verify(scope).lockFactories(List.of(7L));order.verify(j).queryForList("SELECT name FROM metal_product_type WHERE factory_id=? AND name=? FOR UPDATE",String.class,7L,"环");}
 @Test void maintainedTypeSavesAndHistoricalUnchangedTypeIsPreserved(){var j=mock(JdbcTemplate.class);var s=new ProductService(null,FactoryCatalogTestFixtures.scope(),j,rules());assertDoesNotThrow(()->s.prepare(row(),row()));verify(j,never()).queryForList(contains("metal_product_type"),eq(String.class),anyLong(),anyString());FactoryCatalogTestFixtures.productTypes(j);assertDoesNotThrow(()->s.prepare(row(),null));}
 @Test void changingAnExistingTypeNeedsCatalogMembership(){var j=mock(JdbcTemplate.class);var old=row();old.setProductType("杆");old.setCategory("铂杆");assertEquals(400,assertThrows(ServiceException.class,()->new ProductService(null,FactoryCatalogTestFixtures.scope(),j,rules()).prepare(row(),old)).getCode());}
}
