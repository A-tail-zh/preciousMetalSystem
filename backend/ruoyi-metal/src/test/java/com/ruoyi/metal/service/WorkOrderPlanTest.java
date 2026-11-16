package com.ruoyi.metal.service;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.ruoyi.metal.catalog.WorkOrderRequest;
import com.ruoyi.common.exception.ServiceException;
import static org.junit.jupiter.api.Assertions.*;
class WorkOrderPlanTest {
 private WorkOrderRequest.Plan plan(long id,String start,String end){return new WorkOrderRequest.Plan(id,LocalDateTime.parse("2026-10-14T"+start),LocalDateTime.parse("2026-10-14T"+end));}
 @Test void adjacentStepsAllowed(){assertDoesNotThrow(()->WorkOrderService.validatePlans(List.of(plan(1,"09:00:00","10:00:00"),plan(2,"10:00:00","11:00:00")),List.of(1L,2L)));}
 @Test void overlappingStepsRejected(){assertThrows(ServiceException.class,()->WorkOrderService.validatePlans(List.of(plan(1,"09:00:00","10:00:00"),plan(2,"09:30:00","11:00:00")),List.of(1L,2L)));}
 @Test void reorderingAndMissingStepsRejected(){assertThrows(ServiceException.class,()->WorkOrderService.validatePlans(List.of(plan(2,"09:00:00","10:00:00")),List.of(1L)));assertThrows(ServiceException.class,()->WorkOrderService.validatePlans(List.of(),List.of(1L)));}
 @Test void invalidEndAndSecondsRejected(){assertThrows(ServiceException.class,()->WorkOrderService.validatePlans(List.of(plan(1,"09:00:00","09:00:00")),List.of(1L)));assertThrows(ServiceException.class,()->WorkOrderService.validatePlans(List.of(plan(1,"09:00:01","10:00:00")),List.of(1L)));}
}
