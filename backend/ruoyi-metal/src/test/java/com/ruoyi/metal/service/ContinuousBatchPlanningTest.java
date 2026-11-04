package com.ruoyi.metal.service;

import java.util.*;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.metal.security.FactoryScope;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ContinuousBatchPlanningTest {
    private Map<String,Object> batch(long id,int plan,boolean started,boolean submitted){
        return new HashMap<>(Map.of("batch_id",id,"batch_no","B"+id,"status","OPEN","version",2,"planned_quantity",plan,"completed_quantity",0,"handover_started",started,"handover_submitted",submitted));
    }
    private ContinuousBatchPlanning planner(List<Map<String,Object>> batches){
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(batches);
        return new ContinuousBatchPlanning(jdbc,mock(FactoryScope.class));
    }
    @Test void submittedFirstBatchDoesNotBindNextPartToItsPendingAcceptance(){
        var p=planner(List.of(batch(1,100,true,true)));
        assertNull(p.select(10L,20L,null,null,"NEW"));
        assertDoesNotThrow(()->p.requireNew(10L,20L));
    }
    @Test void collectingOnlySomeProcessorsStillBlocksNewBatch(){
        var p=planner(List.of(batch(1,100,true,false)));
        assertThrows(ServiceException.class,()->p.requireNew(10L,20L));
    }
    @Test void incompleteHandoverMayStillReceiveMaterialInTheSameExplicitBatch(){
        assertEquals(1L,planner(List.of(batch(1,100,true,false))).select(10L,20L,1L,2,"APPEND").get("batch_id"));
    }
    @Test void notYetHandedOverBatchStillBlocksNewBatch(){
        assertThrows(ServiceException.class,()->planner(List.of(batch(1,100,false,false))).requireNew(10L,20L));
    }
    @Test void pendingAcceptanceAndNewProcessingHaveSeparateExplicitIdentities(){
        var p=planner(List.of(batch(1,100,true,true),batch(2,60,false,false)));
        assertEquals(2L,p.select(10L,20L,2L,2,"APPEND").get("batch_id"));
        assertThrows(ServiceException.class,()->p.select(10L,20L,null,null,null));
        assertThrows(ServiceException.class,()->p.requireNew(10L,20L));
    }
    @Test void submittedBatchCannotReceiveSupplementaryMaterial(){
        assertThrows(ServiceException.class,()->planner(List.of(batch(1,100,true,true))).select(10L,20L,1L,2,"APPEND"));
    }
    @Test void batchFromAnotherStepAndStaleVersionAreRejected(){
        var p=planner(List.of(batch(2,60,false,false)));
        assertThrows(ServiceException.class,()->p.select(10L,20L,1L,2,"APPEND"));
        assertEquals(409,assertThrows(ServiceException.class,()->p.select(10L,20L,2L,1,"APPEND")).getCode());
    }
    @Test void oldUniqueUnsubmittedBatchCanStillBeSelected(){
        assertEquals(1L,planner(List.of(batch(1,100,false,false))).select(10L,20L,null,null,null).get("batch_id"));
    }
    @Test void newAndAppendCannotBeConfused(){
        var p=planner(List.of());
        assertThrows(ServiceException.class,()->p.select(10L,20L,1L,2,"NEW"));
        assertThrows(ServiceException.class,()->p.select(10L,20L,null,null,"APPEND"));
        assertThrows(ServiceException.class,()->p.select(10L,20L,null,null,"GUESS"));
    }
    @Test void allPendingPlansRemainOccupiedUntilAcceptanceSettles(){
        assertEquals(40L,ContinuousBatchPlanning.available(200,0,160,0,0));
        assertEquals(50L,ContinuousBatchPlanning.available(200,90,60,0,0));
        assertEquals(0L,ContinuousBatchPlanning.available(100,0,100,0,0));
    }
}
