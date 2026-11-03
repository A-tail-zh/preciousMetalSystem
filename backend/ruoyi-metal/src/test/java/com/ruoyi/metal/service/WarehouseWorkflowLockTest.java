package com.ruoyi.metal.service;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ruoyi.metal.security.FactoryScope;

class WarehouseWorkflowLockTest {
 @Test void batchVersionIsRereadAfterTheFactoryLock() throws Exception {
  var jdbc=mock(JdbcTemplate.class);var scope=mock(FactoryScope.class);var locked=new AtomicBoolean();
  doAnswer(call->{locked.set(true);return null;}).when(scope).lockFactories(anyList());
  when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(call->{String sql=call.getArgument(0);if(sql.startsWith("SELECT batch_id"))return List.of(Map.of("batch_id",9L));if(sql.startsWith("SELECT b.*"))return List.of(Map.of("factory_id",2L,"batch_id",9L,"version",locked.get()?1:0));throw new AssertionError(sql);});
  var service=new WarehouseWorkflowService(jdbc,scope,mock(WarehouseService.class),mock(WarehouseImportService.class),mock(OutboundDraftService.class));
  var method=WarehouseWorkflowService.class.getDeclaredMethod("processingBatch",long.class,boolean.class);method.setAccessible(true);
  var batch=(Map<?,?>)method.invoke(service,9L,true);assertEquals(1,batch.get("version"));
 }
}
