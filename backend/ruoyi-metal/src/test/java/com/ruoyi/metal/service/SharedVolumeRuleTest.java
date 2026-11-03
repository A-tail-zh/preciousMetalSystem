package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.util.Map;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.metal.catalog.VolumeRule;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SharedVolumeRuleTest {
 @Test void allMetalsReadTheSamePi(){
  var jdbc=mock(JdbcTemplate.class);when(jdbc.queryForMap(anyString())).thenReturn(Map.of("piValue","3.20","curvedBottomFactor","1.03","version",4));
  var service=new QuoteRuleService(jdbc);assertEquals(new BigDecimal("3.20"),service.pi(1L));assertEquals(service.pi(1L),service.pi(9L));
 }
 @Test void legacyPiSavePreservesExistingCurvedFactor(){
  var jdbc=mock(JdbcTemplate.class);when(jdbc.queryForMap(anyString())).thenReturn(Map.of("version",3,"curved_bottom_factor",new BigDecimal("1.17")));
  try(var security=mockStatic(SecurityUtils.class)){
   security.when(SecurityUtils::getUserId).thenReturn(1L);security.when(()->SecurityUtils.isAdmin(1L)).thenReturn(true);security.when(SecurityUtils::getUsername).thenReturn("tester");
   new QuoteRuleService(jdbc).updateVolumeRule(new VolumeRule(new BigDecimal("3.145"),3));
   verify(jdbc).update("UPDATE metal_volume_rule SET pi_value=?,curved_bottom_factor=?,version=version+1,update_by=?,update_time=NOW() WHERE rule_id=1",new BigDecimal("3.15"),new BigDecimal("1.17"),"tester");
  }
 }
 @Test void staleVersionAndInvalidRoundedPiNeverWrite(){
  var jdbc=mock(JdbcTemplate.class);when(jdbc.queryForMap(anyString())).thenReturn(Map.of("version",3,"curved_bottom_factor",new BigDecimal("1.17")));
  try(var security=mockStatic(SecurityUtils.class)){
   security.when(SecurityUtils::getUserId).thenReturn(1L);security.when(()->SecurityUtils.isAdmin(1L)).thenReturn(true);var service=new QuoteRuleService(jdbc);
   assertTrue(assertThrows(RuntimeException.class,()->service.updateVolumeRule(new VolumeRule(new BigDecimal("3.14"),2))).getMessage().contains("已被修改"));
   assertTrue(assertThrows(RuntimeException.class,()->service.updateVolumeRule(new VolumeRule(new BigDecimal("0.004"),3))).getMessage().contains("大于0"));
   assertTrue(assertThrows(RuntimeException.class,()->service.updateVolumeRule(new VolumeRule(new BigDecimal("99999.995"),3))).getMessage().contains("5位"));
   verify(jdbc,never()).update(anyString(),any(Object[].class));
  }
 }
 @Test void unauthorizedCallerCannotReadOrWriteTheConfigurationThroughSave(){
  var jdbc=mock(JdbcTemplate.class);try(var security=mockStatic(SecurityUtils.class)){
   security.when(SecurityUtils::getUserId).thenReturn(2L);
   assertThrows(RuntimeException.class,()->new QuoteRuleService(jdbc).updateVolumeRule(new VolumeRule(new BigDecimal("3.14"),0)));verifyNoInteractions(jdbc);
  }
 }
 @Test void piAndCurvedFactorRoundAndSaveAtomically(){
  var jdbc=mock(JdbcTemplate.class);when(jdbc.queryForMap(anyString())).thenReturn(Map.of("version",3,"curved_bottom_factor",new BigDecimal("1.03")));
  try(var security=mockStatic(SecurityUtils.class)){
   security.when(SecurityUtils::getUserId).thenReturn(1L);security.when(()->SecurityUtils.isAdmin(1L)).thenReturn(true);security.when(SecurityUtils::getUsername).thenReturn("tester");
   new QuoteRuleService(jdbc).updateVolumeRule(new VolumeRule(new BigDecimal("3.145"),3,new BigDecimal("1.035")));
   verify(jdbc).update("UPDATE metal_volume_rule SET pi_value=?,curved_bottom_factor=?,version=version+1,update_by=?,update_time=NOW() WHERE rule_id=1",new BigDecimal("3.15"),new BigDecimal("1.04"),"tester");
  }
 }
 @Test void invalidCurvedFactorCannotWriteEitherParameter(){
  var jdbc=mock(JdbcTemplate.class);when(jdbc.queryForMap(anyString())).thenReturn(Map.of("version",3,"curved_bottom_factor",new BigDecimal("1.03")));
  try(var security=mockStatic(SecurityUtils.class)){
   security.when(SecurityUtils::getUserId).thenReturn(1L);security.when(()->SecurityUtils.isAdmin(1L)).thenReturn(true);
   for(String value:new String[]{"0","-0.004","0.004","-1","99999999999999.995"})assertThrows(RuntimeException.class,()->new QuoteRuleService(jdbc).updateVolumeRule(new VolumeRule(new BigDecimal("3.14"),3,new BigDecimal(value))));
   verify(jdbc,never()).update(anyString(),any(Object[].class));
  }
 }
 @Test void dtoAllowsLegacyOmissionButRejectsInvalidFactorFormat(){
  try(var factory=jakarta.validation.Validation.buildDefaultValidatorFactory()){
   var validator=factory.getValidator();assertTrue(validator.validate(new VolumeRule(new BigDecimal("3.14"),0)).isEmpty());
   assertTrue(validator.validate(new VolumeRule(new BigDecimal("3.14"),0,new BigDecimal("1.03"))).isEmpty());
   for(String value:new String[]{"0","-1","1.0000001"})assertFalse(validator.validate(new VolumeRule(new BigDecimal("3.14"),0,new BigDecimal(value))).isEmpty());
  }
 }
}
