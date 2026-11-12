package com.ruoyi.metal.service;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.ruoyi.common.exception.ServiceException;
import static org.junit.jupiter.api.Assertions.*;

class ProductionBatchReverseTest {
 @Test void missingAffectedObjectCannotSkipItsVersionCheck(){assertThrows(ServiceException.class,()->ProductionBatchReverseService.versions(List.of(Map.of("batchId","1","version",2),Map.of("batchId","2","version",3)),List.of(Map.of("batchId","1","version",2)),"batchId"));}
 @Test void duplicateObjectCannotReplaceAnotherAffectedObject(){assertThrows(ServiceException.class,()->ProductionBatchReverseService.versions(List.of(Map.of("batchId","1","version",2)),List.of(Map.of("batchId","1","version",2),Map.of("batchId","1","version",2)),"batchId"));}
 @Test void staleVersionIsRejected(){assertThrows(ServiceException.class,()->ProductionBatchReverseService.versions(List.of(Map.of("executionId","1","version",4)),List.of(Map.of("executionId","1","version",3)),"executionId"));}
 @Test void completeCurrentVersionsCanBeSubmittedInAnyOrder(){assertDoesNotThrow(()->ProductionBatchReverseService.versions(List.of(Map.of("batchId","1","version",4),Map.of("batchId","2","version",7)),List.of(Map.of("batchId","2","version",7),Map.of("batchId","1","version",4)),"batchId"));}
}
