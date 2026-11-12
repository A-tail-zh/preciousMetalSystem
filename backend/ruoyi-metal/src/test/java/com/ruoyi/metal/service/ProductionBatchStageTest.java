package com.ruoyi.metal.service;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.metal.security.FactoryScope;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductionBatchStageTest {
    private final List<Map<String,Object>> route=List.of(Map.of("id","11","processId","5"),Map.of("id","12","processId","6"),Map.of("id","13","processId","5"));

    @Test void orderedFrozenStepIdsKeepRepeatedProcessOccurrencesDistinct(){
        assertEquals(0,ProductionExecutionService.nextStageIndex(route,List.of()));
        assertEquals(2,ProductionExecutionService.nextStageIndex(route,List.of(Map.of("order_step_id",11L,"status","CLOSED"),Map.of("order_step_id",12L,"status","CLOSED"))));
        assertEquals(3,ProductionExecutionService.nextStageIndex(route,List.of(Map.of("order_step_id",11L,"status","CLOSED"),Map.of("order_step_id",12L,"status","CLOSED"),Map.of("order_step_id",13L,"status","CLOSED"))));
    }

    @Test void unfinishedStageCannotStartItsSuccessor(){
        assertTrue(assertThrows(ServiceException.class,()->ProductionExecutionService.nextStageIndex(route,List.of(Map.of("order_step_id",11L,"status","OPEN")))).getMessage().contains("部分完成"));
    }

    @Test void completedButOutOfOrderRecordDoesNotSkipRouteNodes(){
        assertThrows(ServiceException.class,()->ProductionExecutionService.nextStageIndex(route,List.of(Map.of("order_step_id",13L,"status","CLOSED"))));
    }

    @Test void thirtyCompletedPiecesCannotAdvanceAPlanOfOneHundred(){
        assertThrows(ServiceException.class,()->ProductionExecutionService.requireWholeQuantity(100L,30,30));
    }

    @Test void missingPlanOrPreviouslyDispatchedOutputCannotBeCalledWholeBatch(){
        assertThrows(ServiceException.class,()->ProductionExecutionService.requireWholeQuantity(null,100,100));
        assertThrows(ServiceException.class,()->ProductionExecutionService.requireWholeQuantity(100L,100,70));
        assertDoesNotThrow(()->ProductionExecutionService.requireWholeQuantity(100L,100,100));
    }

    @Test void existingProcessingRecordCannotBeRelabeledAsAnotherBatch(){
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList(startsWith("SELECT e.*"),eq(6L),eq(9L))).thenReturn(List.of(Map.of("id",6L,"order_id",7L,"status","OPEN","batch_id",101L)));
        var body=new HashMap<String,Object>(Map.of("type","PRODUCTION_OUT","orderId","7","executionId","6","batchId","102"));
        assertTrue(assertThrows(ServiceException.class,()->new ProductionExecutionService(jdbc,mock(FactoryScope.class)).ensureExecutionForOutbound(9,body,List.of())).getMessage().contains("不一致"));
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }

    @Test void explicitBatchRequiresAnExplicitStartedProcessingRecord(){
        var jdbc=mock(JdbcTemplate.class);
        var body=new HashMap<String,Object>(Map.of("type","PRODUCTION_OUT","orderId","7","batchId","101","orderItemId","2","orderStepId","3","processorId","4"));
        assertTrue(assertThrows(ServiceException.class,()->new ProductionExecutionService(jdbc,mock(FactoryScope.class)).ensureExecutionForOutbound(9,body,List.of())).getMessage().contains("START_STAGE"));
        verifyNoInteractions(jdbc);
    }
}
