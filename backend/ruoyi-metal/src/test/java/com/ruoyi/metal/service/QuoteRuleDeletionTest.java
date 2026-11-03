package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.util.*;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.metal.catalog.MetalQuoteRule;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class QuoteRuleDeletionTest {
 private JdbcTemplate database(int version,String flag){
  var jdbc=mock(JdbcTemplate.class);
  when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(call->{String sql=call.getArgument(0);return sql.contains("SELECT name")?List.of(Map.of("name","铱")):sql.contains("FROM metal_type_quote_rule")?List.of(Map.of("version",version,"del_flag",flag)):List.of(Map.of("metal_type_id",2L));});
  return jdbc;
 }
 @Test void deletionOnlyChangesRuleStatusAndAdvancesVersion(){
  var jdbc=database(4,"0");try(var security=mockStatic(SecurityUtils.class)){
   security.when(SecurityUtils::getUserId).thenReturn(1L);security.when(()->SecurityUtils.isAdmin(1L)).thenReturn(true);security.when(SecurityUtils::getUsername).thenReturn("tester");
   new QuoteRuleService(jdbc).remove(2L,4);
   verify(jdbc).update("UPDATE metal_type_quote_rule SET del_flag='2',version=version+1,update_by=?,update_time=NOW() WHERE metal_type_id=?","tester",2L);
   var order=inOrder(jdbc);order.verify(jdbc).queryForList("SELECT name FROM metal_type WHERE metal_type_id=?",2L);order.verify(jdbc).queryForList("SELECT metal_type_id FROM metal_type WHERE metal_type_id=? FOR UPDATE",2L);order.verify(jdbc).queryForList("SELECT version,del_flag FROM metal_type_quote_rule WHERE metal_type_id=? FOR UPDATE",2L);
  }
 }
 @Test void staleAndAlreadyDeletedVersionsCannotWrite(){
  try(var security=mockStatic(SecurityUtils.class)){
   security.when(SecurityUtils::getUserId).thenReturn(1L);security.when(()->SecurityUtils.isAdmin(1L)).thenReturn(true);
   for(String flag:List.of("0","2")){var jdbc=database(4,flag);var service=new QuoteRuleService(jdbc);assertEquals(409,assertThrows(ServiceException.class,()->service.remove(2L,flag.equals("0")?3:4)).getCode());verify(jdbc,never()).update(anyString(),any(Object[].class));}
  }
 }
 @Test void unauthorizedDeletionNeverTouchesDatabase(){
  var jdbc=database(4,"0");try(var security=mockStatic(SecurityUtils.class)){security.when(SecurityUtils::getUserId).thenReturn(2L);assertEquals(403,assertThrows(ServiceException.class,()->new QuoteRuleService(jdbc).remove(2L,4)).getCode());verifyNoInteractions(jdbc);}
 }
 @Test void recreateRequiresTombstoneVersionAndKeepsItIncreasing(){
  var jdbc=database(5,"2");try(var security=mockStatic(SecurityUtils.class)){
   security.when(SecurityUtils::getUserId).thenReturn(1L);security.when(()->SecurityUtils.isAdmin(1L)).thenReturn(true);security.when(SecurityUtils::getUsername).thenReturn("tester");when(jdbc.queryForMap(anyString())).thenReturn(Map.of("piValue","3.14"));var service=new QuoteRuleService(jdbc);var one=BigDecimal.ONE;
   assertEquals(409,assertThrows(ServiceException.class,()->service.update(new MetalQuoteRule(2L,one,one,one,one,one,0))).getCode());
   service.update(new MetalQuoteRule(2L,one,one,one,one,one,5));verify(jdbc).update(contains("ON DUPLICATE KEY UPDATE del_flag='0'"),eq(2L),eq(new BigDecimal("1.00")),eq(new BigDecimal("3.14")),eq(new BigDecimal("1.00")),eq(new BigDecimal("1.00")),eq(new BigDecimal("1.00")),eq(new BigDecimal("1.00")),eq(6),eq("tester"));
  }
 }
}
