package com.ruoyi.metal.service;

import com.ruoyi.common.exception.ServiceException;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProductionBatchTransformTest {
    private Map<String,Object> batch(String inherited){
        var batch=new HashMap<String,Object>();
        batch.put("route_snapshot","{\"steps\":[{\"id\":\"11\",\"processId\":\"5\"},{\"id\":\"12\",\"processId\":\"6\"},{\"id\":\"13\",\"processId\":\"5\"}]}");
        if(inherited!=null)batch.put("completed_step_ids",inherited);
        return batch;
    }
    @Test void inheritedCompletionAndLocalWorkFormOneOrderedRoute(){
        assertEquals(List.of("11","12"),ProductionBatchTransformService.completed(batch("[\"11\"]"),List.of(Map.of("order_step_id",12L,"status","CLOSED"))));
    }
    @Test void transferredChildDoesNotNeedFabricatedPastProcessingRecords(){
        assertEquals(List.of("11","12"),ProductionBatchTransformService.completed(batch("[\"11\",\"12\"]"),List.of()));
    }
    @Test void openProcessingDoesNotBecomeACompletedStage(){
        assertEquals(List.of("11"),ProductionBatchTransformService.completed(batch("[\"11\"]"),List.of(Map.of("order_step_id",12L,"status","OPEN"))));
    }
    @Test void carriedMaterialIsNotAnUnusedCancelableExecution(){
        assertFalse(ProductionExecutionService.unusedReason(Map.of("status","OPEN","carryInWeight","700.00"),false,List.of()).isEmpty());
    }
    @Test void actualScrapDoesNotEraseTheProductionTarget(){
        assertDoesNotThrow(()->ProductionBatchTransformService.requirePlannedCapacity(70L,68));
        assertThrows(ServiceException.class,()->ProductionBatchTransformService.requirePlannedCapacity(70L,71));
        assertThrows(ServiceException.class,()->ProductionBatchTransformService.requirePlannedCapacity(null,68));
    }
    @Test void matchingProcessTypeCannotSkipItsFrozenOccurrence(){
        assertThrows(ServiceException.class,()->ProductionBatchTransformService.completed(batch("[\"13\"]"),List.of()));
        assertThrows(ServiceException.class,()->ProductionBatchTransformService.completed(batch("[\"11\"]"),List.of(Map.of("order_step_id",13L,"status","CLOSED"))));
    }
}
