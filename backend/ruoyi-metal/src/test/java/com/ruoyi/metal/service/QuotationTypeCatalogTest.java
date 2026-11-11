package com.ruoyi.metal.service;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ruoyi.metal.catalog.CalculationRequest;
import com.ruoyi.common.exception.ServiceException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QuotationTypeCatalogTest {
 @Test void contextAlwaysReturnsFixedProductTypes(){
  var jdbc=mock(JdbcTemplate.class);var scope=FactoryCatalogTestFixtures.scope();
  var service=spy(new QuoteRuleService(jdbc,scope));doReturn(Map.of()).when(service).volumeRule(anyLong());
  when(jdbc.queryForList(startsWith("SELECT CAST(product_id AS CHAR)"),eq(7L))).thenReturn(List.of());
  when(jdbc.queryForList(startsWith("SELECT CAST(product_id AS CHAR)"),eq(8L))).thenReturn(List.of());
  doReturn(List.of()).when(service).metals(anyLong());doReturn(List.of()).when(service).rules(isNull(),anyLong());
  assertEquals(ProductTypes.ALL,service.context(7L).get("productTypes"));
  assertEquals(ProductTypes.ALL,service.context(8L).get("productTypes"));
  verify(jdbc,never()).queryForList(contains("metal_product_type"),eq(String.class),anyLong());
 }
 @Test void scopedCalculationRejectsInvalidTypeAndStillScopesMetalCatalog(){
  var jdbc=mock(JdbcTemplate.class);var scope=FactoryCatalogTestFixtures.scope();var service=spy(new QuoteRuleService(jdbc,scope));
  var valid=new CalculationRequest(null,Map.of(),1,1L,"杆");
  var invalid=new CalculationRequest(null,Map.of(),1,1L,"自定义");
  doReturn(Map.of("volume","3.14")).when(service).calculate(valid);
  assertEquals("3.14",service.calculate(valid,7L).get("volume"));
  assertEquals(400,assertThrows(ServiceException.class,()->service.calculate(invalid,7L)).getCode());
  doThrow(new ServiceException("金属类型不属于当前工厂",400)).when(scope).requireCatalog("metal",1L,8L);
  assertEquals(400,assertThrows(ServiceException.class,()->service.calculate(valid,8L)).getCode());
  verify(service,times(1)).calculate(valid);verify(scope).requireCatalog("metal",1L,8L);
 }
}
