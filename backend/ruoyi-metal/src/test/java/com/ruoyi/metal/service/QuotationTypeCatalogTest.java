package com.ruoyi.metal.service;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ruoyi.metal.catalog.CalculationRequest;
import com.ruoyi.common.exception.ServiceException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QuotationTypeCatalogTest {
 @Test void contextReturnsOnlyConfiguredFactoryTypesIncludingEmptyCatalog(){
  var jdbc=mock(JdbcTemplate.class);var scope=FactoryCatalogTestFixtures.scope();
  var service=spy(new QuoteRuleService(jdbc,scope));doReturn(Map.of()).when(service).volumeRule(anyLong());
  when(jdbc.queryForList("SELECT name FROM metal_product_type WHERE factory_id=? ORDER BY name,id",String.class,7L)).thenReturn(List.of("杆"));
  assertEquals(List.of("杆"),service.context(7L).get("productTypes"));
  assertEquals(List.of(),service.context(8L).get("productTypes"));
 }
 @Test void scopedCalculationRejectsMissingAndOtherFactoryTypesBeforeCalculating(){
  var jdbc=mock(JdbcTemplate.class);var scope=FactoryCatalogTestFixtures.scope();var service=spy(new QuoteRuleService(jdbc,scope));
  var input=new CalculationRequest(null,Map.of(),1,1L,"杆");
  doReturn(Map.of("volume","3.14")).when(service).calculate(input);
  when(jdbc.queryForList("SELECT name FROM metal_product_type WHERE factory_id=? AND name=?",String.class,7L,"杆")).thenReturn(List.of("杆"));
  assertEquals("3.14",service.calculate(input,7L).get("volume"));
  assertEquals(400,assertThrows(ServiceException.class,()->service.calculate(input,8L)).getCode());
  verify(service,times(1)).calculate(input);verify(scope).requireCatalog("metal",1L,8L);
 }
}
