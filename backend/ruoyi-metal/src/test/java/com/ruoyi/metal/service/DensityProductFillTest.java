package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.metal.security.FactoryScope;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DensityProductFillTest {
 @Test void fillsOnlyScopedMissingWeightsUsingExistingRoundedVolumeAndVersions(){
  var jdbc=mock(JdbcTemplate.class);var service=new QuoteRuleService(jdbc,mock(FactoryScope.class));
  when(jdbc.queryForList(anyString(),eq(7L),eq(9L))).thenReturn(List.of(Map.of("product_id",11L,"product_code","P11","volume",new BigDecimal("0.034567"),"version",3)));
  try(var security=mockStatic(SecurityUtils.class)){
   security.when(SecurityUtils::getUsername).thenReturn("tester");
   var result=service.fillMissingProductWeights(7L,9L,new BigDecimal("22.65"));
   assertEquals("0.68",result.get(0).get("weight"));assertNull(result.get(0).get("previousWeight"));
   verify(jdbc).queryForList("SELECT product_id,product_code,volume,version FROM metal_product WHERE factory_id=? AND metal_type_id=? AND del_flag='0' AND theoretical_weight IS NULL AND volume>0 ORDER BY product_id FOR UPDATE",7L,9L);
   verify(jdbc).update("UPDATE metal_product SET theoretical_weight=?,version=version+1,update_by=?,update_time=NOW() WHERE factory_id=? AND metal_type_id=? AND product_id=? AND del_flag='0' AND theoretical_weight IS NULL",new BigDecimal("0.68"),"tester",7L,9L,11L);
  }
 }
 @Test void overflowIdentifiesProductAndDoesNotWriteInvalidValue(){
  var jdbc=mock(JdbcTemplate.class);var service=new QuoteRuleService(jdbc,mock(FactoryScope.class));
  when(jdbc.queryForList(anyString(),eq(7L),eq(9L))).thenReturn(List.of(Map.of("product_id",11L,"product_code","BIG","volume",new BigDecimal("99999999999999"),"version",0)));
  assertTrue(assertThrows(ServiceException.class,()->service.fillMissingProductWeights(7,9,new BigDecimal("99"))).getMessage().contains("BIG"));verify(jdbc,never()).update(anyString(),any(Object[].class));
 }
 @Test void productionCandidatesExcludeFinishedButOtherUsesKeepThem(){
  var jdbc=mock(JdbcTemplate.class);var scope=mock(FactoryScope.class);var ledger=mock(StorageLedgerService.class);
  when(scope.isAdmin()).thenReturn(true);when(scope.resolveFactory(7L)).thenReturn(7L);
  when(ledger.options(eq(7L),anyMap())).thenReturn(List.of(Map.of("id","1","category","RAW"),Map.of("id","2","category","SEMI"),Map.of("id","3","category","PRODUCT")));
  var service=new StorageOrderService(jdbc,scope,ledger,mock(ProductionExecutionService.class));
  assertEquals(2,((List<?>)service.options(7L,null,"PRODUCTION_OUT").get("stocks")).size());assertEquals(3,((List<?>)service.options(7L,null,"PRODUCT_OUT").get("stocks")).size());
  when(scope.resolveFactory(8L)).thenThrow(new ServiceException("禁止跨厂",403));assertThrows(ServiceException.class,()->service.options(8L,null,"PRODUCTION_OUT"));verify(ledger,never()).options(eq(8L),anyMap());
 }
}
