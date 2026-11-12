package com.ruoyi.metal.service;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.metal.security.FactoryScope;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductionBatchAssociationTest {
    @Test void mappedRecordAdvertisesTheSameEditRestrictionAsTheWriteEndpoint() throws Exception {
        var scope=mock(FactoryScope.class);when(scope.isAdmin()).thenReturn(true);
        var execution=new java.util.HashMap<String,Object>(Map.of("id","6","batchId","12","status","OPEN"));
        var flow=Map.<String,Object>of("executions",List.of(execution),"events",List.of(),"documents",List.of(),"order",Map.of("status","CREATED"));
        var method=ProductionExecutionService.class.getDeclaredMethod("fillBatchActions",Map.class);
        method.setAccessible(true);
        method.invoke(new ProductionExecutionService(mock(JdbcTemplate.class),scope),flow);
        assertEquals(false,execution.get("canEdit"));
        assertTrue(execution.get("editDisabledReason").toString().contains("统一生产批次"));
    }

    @Test void emptyButActiveBatchStillPreventsOrderCompletion() throws Exception {
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList("SELECT id FROM metal_production_batch WHERE order_id=? AND lifecycle='ACTIVE' FOR UPDATE",7L)).thenReturn(List.of(Map.of("id",12L)));
        var method=ProductionExecutionService.class.getDeclaredMethod("noOpenWork",long.class);
        method.setAccessible(true);
        var cause=assertThrows(InvocationTargetException.class,()->method.invoke(new ProductionExecutionService(jdbc,mock(FactoryScope.class)),7L)).getCause();
        assertInstanceOf(ServiceException.class,cause);
        assertTrue(cause.getMessage().contains("统一生产批次"));
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }

    @Test void linkedExecutionRejectsAnotherBatchIdentityBeforeAnyStorageWrite() {
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList(startsWith("SELECT e.*"),eq(6L),eq(9L))).thenReturn(List.of(Map.of("id",6L,"batch_id",12L,"status","OPEN")));
        var service=new ProductionExecutionService(jdbc,mock(FactoryScope.class));
        var error=assertThrows(ServiceException.class,()->service.prepareDocument(9L,new java.util.HashMap<>(Map.of("type","PRODUCTION_OUT","executionId","6","batchId","13"))));
        assertEquals(409,error.getCode());
        assertTrue(error.getMessage().contains("生产批次"));
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }

    @Test void inactiveProductionBatchCannotCreateProcessingRecord() {
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList(startsWith("SELECT * FROM metal_production_batch"),eq(9L),eq(12L))).thenReturn(List.of(Map.of("lifecycle","SPLIT")));
        var error=assertThrows(ServiceException.class,()->new ProductionExecutionService(jdbc,mock(FactoryScope.class)).createForBatch(9L,12L,Map.of()));
        assertEquals(409,error.getCode());
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }

    @Test void creatingBatchFreezesOrderBeforeExistingEditDeletesAndRebuildsItsItems() throws Exception {
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList(startsWith("SELECT version,status,deleted"),eq(7L),eq(9L))).thenReturn(List.of(Map.of("deleted",0,"status","CREATED","version",0)));
        when(jdbc.queryForList("SELECT id FROM metal_production_batch WHERE factory_id=? AND order_id=? FOR UPDATE",9L,7L)).thenReturn(List.of(Map.of("id",12L)));
        var method=ProductionOrderService.class.getDeclaredMethod("lockEditable",Long.class,Long.class,Integer.class);
        method.setAccessible(true);
        var cause=assertThrows(InvocationTargetException.class,()->method.invoke(new ProductionOrderService(jdbc,mock(FactoryScope.class)),7L,9L,0)).getCause();
        assertInstanceOf(ServiceException.class,cause);
        assertTrue(cause.getMessage().contains("统一生产批次"));
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
}
