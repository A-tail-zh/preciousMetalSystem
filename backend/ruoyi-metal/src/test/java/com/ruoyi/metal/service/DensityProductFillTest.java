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
 private QuoteRuleService service(JdbcTemplate jdbc){return new QuoteRuleService(jdbc,mock(FactoryScope.class)){
  @Override public String metalName(Long id){return "合成金属";}
  @Override public BigDecimal pi(Long id){return new BigDecimal("3.14");}
 };}
 private com.ruoyi.metal.catalog.MetalQuoteRule rule(String density){return new com.ruoyi.metal.catalog.MetalQuoteRule(9L,new BigDecimal(density),BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,0);}
 @Test void changingDensityOnlyUpdatesTheRuleAndDoesNotRewriteProductWeights(){
  var jdbc=mock(JdbcTemplate.class);var service=service(jdbc);
  try(var security=mockStatic(SecurityUtils.class)){
   security.when(SecurityUtils::getUserId).thenReturn(1L);security.when(()->SecurityUtils.isAdmin(1L)).thenReturn(true);
   security.when(SecurityUtils::getUsername).thenReturn("tester");
   service.update(rule("22.65"));
   verify(jdbc).update(startsWith("INSERT INTO metal_type_quote_rule"),eq(9L),eq(new BigDecimal("22.65")),eq(new BigDecimal("3.14")),eq(new BigDecimal("1.00")),eq(new BigDecimal("1.00")),eq(new BigDecimal("1.00")),eq(new BigDecimal("1.00")),eq(1),eq("tester"));
   verify(jdbc,never()).queryForList(contains("metal_product"),any(Object[].class));
   verify(jdbc,never()).update(contains("metal_product"),any(Object[].class));
  }
 }
 @Test void overflowingDensityIsRejectedBeforeAnyWrite(){
  var jdbc=mock(JdbcTemplate.class);var service=service(jdbc);
  try(var security=mockStatic(SecurityUtils.class)){
   security.when(SecurityUtils::getUserId).thenReturn(1L);security.when(()->SecurityUtils.isAdmin(1L)).thenReturn(true);
   assertThrows(ServiceException.class,()->service.update(rule("999999999999999")));verify(jdbc,never()).update(anyString(),any(Object[].class));
  }
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
