package com.ruoyi.metal.service;

import org.junit.jupiter.api.Test;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ruoyi.metal.security.FactoryScope;
import static org.mockito.Mockito.*;
import com.ruoyi.common.exception.ServiceException;
import static org.junit.jupiter.api.Assertions.*;

class CustomerArrivalFormTest {
 @Test void customerArrivalAcceptsOnlyPowderAndUntreated(){
  for(String form:new String[]{"POWDER","UNTREATED"})assertDoesNotThrow(()->StorageLedgerService.requireCustomerArrivalForm("CUSTOMER_IN",form,"M001"));
  for(Object form:new Object[]{"METAL","",null,"UNKNOWN"}){
   var error=assertThrows(ServiceException.class,()->StorageLedgerService.requireCustomerArrivalForm("CUSTOMER_IN",form,"M-METAL"));
   assertTrue(error.getMessage().contains("M-METAL"));assertTrue(error.getMessage().contains("客户来料仅允许粉状或未处理料"));
  }
 }
 @Test void otherInboundTypesRetainTheirMetalRules(){
  for(String type:new String[]{"RAW_IN","PROCESS_RAW_IN","RECOVERY_RETURN"})assertDoesNotThrow(()->StorageLedgerService.requireCustomerArrivalForm(type,"METAL","M-METAL"));
 }
 @Test void normalizationUsesCatalogFormAndStillAllowsProcessingMetal(){
  var jdbc=mock(JdbcTemplate.class);
  when(jdbc.queryForList(contains("FROM metal_warehouse"),any(Object[].class))).thenReturn(List.of(Map.of("warehouse_id",1L)));
  when(jdbc.queryForList(contains("FROM metal_material"),any(Object[].class))).thenReturn(List.of(Map.of("material_id",2L,"material_code","M-METAL","material_form","METAL","measurement_mode","WEIGHT","material_name","金属料","metal_type_id",3L)));
  var ledger=new StorageLedgerService(jdbc,mock(FactoryScope.class),mock(StorageReservationService.class));
  var forged=new HashMap<String,Object>(Map.of("warehouseId","1","materialId","2","category","RAW","form","POWDER","productionIdentity",Map.of("form","POWDER")));
  assertThrows(ServiceException.class,()->ledger.normalizeInbound(8L,"CUSTOMER_IN",forged));
  forged.remove("productionIdentity");
  var result=ledger.normalizeInbound(8L,"PROCESS_RAW_IN",forged);
  assertEquals("METAL",((Map<?,?>)result.get("identity")).get("form"));
 }

}
