package com.ruoyi.metal.service;
import java.math.BigDecimal;import java.util.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class RouteExecutionRulesTest {
 @Test void skipsOnlyInterveningSteps(){assertEquals(List.of(2L),RouteExecutionRules.skipped(1,3));assertEquals(List.of(),RouteExecutionRules.skipped(2,3));assertThrows(RuntimeException.class,()->RouteExecutionRules.skipped(3,3));}
 @Test void propagatedSkipsAreNotNewCredit(){var t=RouteExecutionRules.advance(List.of(1L,3L),List.of(2L),3,4);assertEquals(List.of(1L,3L,4L),t.processed());assertEquals(List.of(2L),t.skipped());assertEquals(List.of(),t.newSkipped());}
 @Test void permittedSkipPreservesActualHistory(){var t=RouteExecutionRules.advance(List.of(1L),List.of(),1,3);assertEquals(List.of(1L,3L),t.processed());assertEquals(List.of(2L),t.skipped());assertEquals(List.of(2L),t.newSkipped());}
 @Test void coverageRespectsPlan(){assertDoesNotThrow(()->RouteExecutionRules.capacity(10,6,0,0,4));assertThrows(RuntimeException.class,()->RouteExecutionRules.capacity(10,6,0,0,5));}
 @Test void differentTraceCannotMix(){assertThrows(RuntimeException.class,()->RouteExecutionRules.sameProfile("actual1,2","actual1;skip2"));}
 @Test void balanceDoesNotCountInternalTransfers(){assertEquals(new BigDecimal("0.000000"),WorkOrderWeightBalanceService.difference(new BigDecimal("300"),List.of(new BigDecimal("140"),new BigDecimal("40"),new BigDecimal("20"),new BigDecimal("100"))));assertEquals(new BigDecimal("0.000000"),WorkOrderWeightBalanceService.difference(new BigDecimal("300"),List.of(new BigDecimal("140"),new BigDecimal("20"),new BigDecimal("20"),new BigDecimal("20"),new BigDecimal("100"))));}
 @Test void previousProductionTemplateRemainsReadable(){var headers=WarehouseTemplateLayout.production().stream().filter(c->!c.key().startsWith("skipPermit")).map(WarehouseTemplateLayout.Column::label).toList();assertTrue(WarehouseTemplateLayout.identify(headers,"PRODUCTION_OUT").containsKey("releaseId"));}
 @Test void retryBackoffStopsAfterFiveRetries(){assertEquals(2,SemiAutoReleaseService.retryDelay(1));assertEquals(300,SemiAutoReleaseService.retryDelay(5));assertEquals(-1,SemiAutoReleaseService.retryDelay(6));}
}
