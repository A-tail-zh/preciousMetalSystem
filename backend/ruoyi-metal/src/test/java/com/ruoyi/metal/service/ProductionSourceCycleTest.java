package com.ruoyi.metal.service;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ruoyi.common.exception.ServiceException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ProductionSourceCycleTest {
 private ProductionSourceBalances sources(Long producer,Map<Long,List<Long>> parents){
  var jdbc=mock(JdbcTemplate.class);
  when(jdbc.queryForList(startsWith("SELECT DISTINCT execution_id"),eq(Long.class),eq(9L),eq(18L))).thenReturn(producer==null?List.of():List.of(producer));
  when(jdbc.queryForList(startsWith("SELECT DISTINCT COALESCE"),eq(Long.class),eq(9L),anyLong())).thenAnswer(call->parents.getOrDefault(call.getArgument(3),List.of()));
  return new ProductionSourceBalances(jdbc);
 }
 @Test void rejectsOwnOutputWithActionableMessage(){var error=assertThrows(ServiceException.class,()->sources(14L,Map.of()).requireAcyclicInput(9,14,18));assertEquals(409,error.getCode());assertTrue(error.getMessage().contains("新批次领料"));}
 @Test void rejectsIndirectReturnToAnAncestor(){assertThrows(ServiceException.class,()->sources(21L,Map.of(21L,List.of(20L),20L,List.of(14L))).requireAcyclicInput(9,14,18));}
 @Test void repeatedAncestorInADiamondIsNotACycle(){assertDoesNotThrow(()->sources(30L,Map.of(30L,List.of(20L,21L),20L,List.of(10L),21L,List.of(10L))).requireAcyclicInput(9,99,18));}
 @Test void rejectsAlreadyCyclicSourceHistory(){assertThrows(ServiceException.class,()->sources(30L,Map.of(30L,List.of(20L),20L,List.of(30L))).requireAcyclicInput(9,99,18));}
 @Test void newBatchMayUseEarlierOutput(){assertDoesNotThrow(()->sources(14L,Map.of(14L,List.of(13L))).requireAcyclicInput(9,99,18));}
 @Test void customerArrivalWithoutProductionSourceIsAllowed(){assertDoesNotThrow(()->sources(null,Map.of()).requireAcyclicInput(9,14,18));}
}
