package com.ruoyi.metal.service;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ruoyi.metal.security.FactoryScope;
import com.ruoyi.common.exception.ServiceException;
import java.util.*;
class ProcessConsumptionScopeTest {
 @Test void settledStepsFilterBeforeAggregationAndValidateProcess(){var jdbc=mock(JdbcTemplate.class);var scope=mock(FactoryScope.class);when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(a->{String q=a.getArgument(0);if(q.contains("SELECT factory_id"))return List.of(Map.of("factory_id",9L));if(q.contains("SELECT item_id")||q.contains("SELECT step_id"))return List.of(Map.of("id",1L));if(q.contains("SELECT process_id")){assertTrue(q.contains("process_id=?"));return List.of(Map.of("processId",3L,"sequenceNo",1,"processName","粉料"));}assertTrue(q.contains("b.status='SETTLED'"));assertTrue(q.contains("b.process_id=?"));return List.of();});var out=new ProcessConsumptionService(jdbc,scope).steps(1L,2L,3L,"SETTLED");assertEquals("3",out.get("processId"));assertEquals("SETTLED",out.get("batchScope"));verify(scope).check(9L);}
 @Test void invalidScopeRejectedBeforeRead(){var jdbc=mock(JdbcTemplate.class);var service=new ProcessConsumptionService(jdbc,mock(FactoryScope.class));assertThrows(ServiceException.class,()->service.steps(1L,2L,3L,"INVALID"));assertThrows(ServiceException.class,()->service.batches(1L,2L,3L,false,1,16,"INVALID"));verifyNoInteractions(jdbc);}
 @Test void settledPageAndCountShareFilterEvenIfIncludeVoided(){var jdbc=mock(JdbcTemplate.class);var scope=mock(FactoryScope.class);when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(a->{String q=a.getArgument(0);if(q.contains("SELECT factory_id"))return List.of(Map.of("factory_id",9L));if(q.contains("SELECT item_id")||q.contains("SELECT step_id"))return List.of(Map.of("id",1L));assertTrue(q.contains("b.status='SETTLED'"));assertFalse(q.contains("b.status<>'VOID'"));return List.of();});when(jdbc.queryForObject(anyString(),eq(Long.class),any(Object[].class))).thenAnswer(a->{assertTrue(((String)a.getArgument(0)).contains("b.status='SETTLED'"));return 0L;});var out=new ProcessConsumptionService(jdbc,scope).batches(1L,2L,3L,true,1,16,"SETTLED");assertEquals(0L,out.get("total"));assertEquals("SETTLED",out.get("batchScope"));}
}
