package com.ruoyi.metal.service;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.ruoyi.common.exception.ServiceException;
import static org.junit.jupiter.api.Assertions.*;
class ProductionRoutePolicyTest {
 private Map<String,Object> step(String semantic,String category){return Map.of("processName","测试工序","semanticCode",semantic,"outputCategory",category,"measurementMode","PIECE","outputForm","METAL");}
 private void valid(Map<String,Object>... steps){assertDoesNotThrow(()->ProductionRoutePolicy.validate(List.of(steps)));}
 private void invalid(Map<String,Object>... steps){assertThrows(ServiceException.class,()->ProductionRoutePolicy.validate(List.of(steps)));}
 @Test void ordinaryRoutesSupportRawAndSemiWithoutSmelting(){
  valid(step("NORMAL","PRODUCT"));valid(step("NORMAL","SEMI"),step("NORMAL","PRODUCT"));
  valid(step("NORMAL","RAW"),step("NORMAL","SEMI"),step("NORMAL","SEMI"),step("NORMAL","PRODUCT"));
  invalid(step("NORMAL","SEMI"),step("NORMAL","RAW"),step("NORMAL","PRODUCT"));
  invalid(step("NORMAL","PRODUCT"),step("NORMAL","PRODUCT"));invalid(step("NORMAL","SEMI"));
 }
 @Test void specialStagesKeepTheirOwnRules(){
  valid(step("ACID_WASH","RAW"),step("SMELTING","SEMI"),step("NORMAL","PRODUCT"));
  valid(step("ACID_WASH","RAW"),step("NORMAL","PRODUCT"));
  invalid(step("ACID_WASH","SEMI"),step("NORMAL","PRODUCT"));
  invalid(step("ACID_WASH","PRODUCT"));invalid(step("SMELTING","PRODUCT"));
  invalid(step("SMELTING","SEMI"),step("SMELTING","SEMI"),step("NORMAL","PRODUCT"));
  invalid(step("SMELTING","SEMI"),step("ACID_WASH","RAW"),step("NORMAL","PRODUCT"));
  invalid(step("NORMAL","SEMI"),step("SMELTING","SEMI"),step("NORMAL","PRODUCT"));
 }
 @Test void missingConfigurationNamesTheProcess(){
  var e=assertThrows(ServiceException.class,()->ProductionRoutePolicy.validate(List.of(step("","PRODUCT"))));
  assertTrue(e.getMessage().contains("测试工序"));assertTrue(e.getMessage().contains("工序管理"));
  var s=new HashMap<>(step("NORMAL","PRODUCT"));s.put("measurementMode","");invalid(s);
  s=new HashMap<>(step("ACID_WASH","RAW"));s.put("outputForm","POWDER");invalid(s,step("NORMAL","PRODUCT"));
 }
}
