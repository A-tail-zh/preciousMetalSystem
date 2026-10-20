package com.ruoyi.metal.service;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ruoyi.metal.security.FactoryScope;
import com.ruoyi.common.exception.ServiceException;
import java.util.*;
class ProcessConsumptionAccessTest {
 @Test void rejectsCrossFactoryBeforeReadingItem(){var jdbc=mock(JdbcTemplate.class);var scope=mock(FactoryScope.class);when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(Map.of("factory_id",9L)));doThrow(new ServiceException("无权访问",403)).when(scope).check(9L);assertThrows(ServiceException.class,()->new ProcessConsumptionService(jdbc,scope).steps(1L,2L));verify(jdbc,times(1)).queryForList(anyString(),any(Object[].class));}
 @Test void rejectsUnrelatedItem(){var jdbc=mock(JdbcTemplate.class);var scope=mock(FactoryScope.class);when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(a->((String)a.getArgument(0)).contains("metal_work_order WHERE")?List.of(Map.of("factory_id",9L)):List.of());assertThrows(ServiceException.class,()->new ProcessConsumptionService(jdbc,scope).steps(1L,2L));verify(scope).check(9L);}
 @Test void rejectsUnrelatedBatchAndNeverReadsOrders(){var jdbc=mock(JdbcTemplate.class);var scope=mock(FactoryScope.class);when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(a->{String q=a.getArgument(0);if(q.contains("SELECT batch_id"))return List.of();if(q.contains("SELECT factory_id"))return List.of(Map.of("factory_id",9L));return List.of(Map.of("item_id",2L));});assertThrows(ServiceException.class,()->new ProcessConsumptionService(jdbc,scope).orders(1L,2L,3L,4L,1,16));verify(jdbc,never()).queryForObject(anyString(),eq(Long.class),any(Object[].class));}
 @Test void rejectsInvalidPagesBeforeAnyData(){var jdbc=mock(JdbcTemplate.class);var scope=mock(FactoryScope.class);var service=new ProcessConsumptionService(jdbc,scope);assertThrows(ServiceException.class,()->service.batches(1L,2L,3L,false,0,16));assertThrows(ServiceException.class,()->service.orders(1L,2L,3L,4L,1,101));assertThrows(ServiceException.class,()->service.lines(1L,2L,3L,4L,5L,1,0));verifyNoInteractions(jdbc,scope);}
 @Test void emptyStepsStillPreserveSavedSequenceAndDoNotWrite(){var jdbc=mock(JdbcTemplate.class);var scope=mock(FactoryScope.class);when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(a->{String q=a.getArgument(0);if(q.contains("SELECT factory_id"))return List.of(Map.of("factory_id",9L));if(q.contains("SELECT item_id"))return List.of(Map.of("item_id",2L));if(q.contains("SELECT process_id"))return List.of(Map.of("processId",7L,"sequenceNo",1,"processName","历史快照名"));return List.of();});var result=new ProcessConsumptionService(jdbc,scope).steps(1L,2L);var rows=(List<Map<String,Object>>)result.get("rows");assertEquals(1,rows.size());assertEquals("7",rows.get(0).get("processId"));assertEquals("历史快照名",rows.get(0).get("processName"));assertEquals("NOT_STARTED",rows.get(0).get("state"));verify(jdbc,never()).update(anyString(),any(Object[].class));}
}
