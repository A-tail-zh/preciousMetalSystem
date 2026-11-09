package com.ruoyi.metal.service;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ruoyi.metal.security.FactoryScope;
import com.ruoyi.common.exception.ServiceException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class QuotationFactoryScopeTest {
 @Test void historyRequiresResolvedFactoryAndBindsSql(){
  var jdbc=mock(JdbcTemplate.class);var scope=mock(FactoryScope.class);var service=new DashboardService(jdbc,scope,mock(QuoteRuleService.class));
  when(scope.resolveFactory(null)).thenThrow(new ServiceException("请先选择所属工厂"));
  assertThrows(ServiceException.class,()->service.quotationList(null,null,1,16));verifyNoInteractions(jdbc);
  when(scope.resolveFactory(7L)).thenReturn(7L);when(jdbc.queryForObject(anyString(),eq(Long.class),any(Object[].class))).thenReturn(0L);
  service.quotationList(7L,null,1,16);
  verify(jdbc).queryForObject("SELECT COUNT(*) FROM metal_saved_quotation q WHERE 1=1 AND q.factory_id=?",Long.class,7L);
 }
 @Test void selectedFactoryMustMatchDocumentEvenForAdmin(){
  var scope=mock(FactoryScope.class);when(scope.resolveFactory(7L)).thenReturn(7L);when(scope.resolveFactory(8L)).thenReturn(8L);
  var service=new DashboardService(mock(JdbcTemplate.class),scope,mock(QuoteRuleService.class)){
   @Override public Map<String,Object> quotation(Long id){return Map.of("id",id,"factoryId","7");}
  };
  assertEquals("7",service.quotation(1L,7L).get("factoryId"));
  assertEquals(403,assertThrows(ServiceException.class,()->service.quotation(1L,8L)).getCode());
 }
}
