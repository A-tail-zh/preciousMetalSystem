package com.ruoyi.metal.service;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.metal.security.FactoryScope;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductionLegacyMappingTest {
 @Test void unknownProductionIdentityIsReadonlyButOrdinaryRawMaterialIsNot(){
  var row=new HashMap<String,Object>();
  ProductionExecutionService.markBatchMapping(row,null,true);
  assertEquals(false,row.get("batchMapped"));assertEquals(true,row.get("readOnly"));assertEquals("LEGACY_UNMAPPED",row.get("writeBlockedReasonCode"));
  ProductionExecutionService.markBatchMapping(row,null,false);assertEquals(false,row.get("readOnly"));assertNull(row.get("writeBlockedReason"));
  ProductionExecutionService.markBatchMapping(row,"42",true);assertEquals(true,row.get("batchMapped"));assertEquals(false,row.get("readOnly"));
 }
 @Test void directProductionPostingCannotWriteAnUnmappedHistoricalExecution(){
  var jdbc=mock(JdbcTemplate.class);var service=new ProductionExecutionService(jdbc,mock(FactoryScope.class));
  when(jdbc.queryForList(startsWith("SELECT e.*"),eq(6L),eq(9L))).thenReturn(List.of(Map.of("id",6L,"factory_id",9L)));
  var error=assertThrows(ServiceException.class,()->service.postDocument(9L,new HashMap<>(Map.of("type","SEMI_IN","executionId","6","id","8")),List.of()));
  assertEquals(409,error.getCode());assertTrue(error.getMessage().contains("历史"));verify(jdbc,never()).update(anyString(),any(Object[].class));
 }
 @Test void cancellingHistoricalDocumentDoesNotAutoCancelItsUnmappedProcessingRecord(){
  var jdbc=mock(JdbcTemplate.class);var service=new ProductionExecutionService(jdbc,mock(FactoryScope.class));
  when(jdbc.queryForList(startsWith("SELECT e.*"),eq(6L),eq(9L))).thenReturn(List.of(Map.of("id",6L,"status","OPEN","autoCreated",true)));
  assertFalse(service.cancelUnusedAutoExecution(9L,6L,8L));verify(jdbc,never()).update(anyString(),any(Object[].class));
 }
}
