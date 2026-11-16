package com.ruoyi.metal.service;
import java.util.*;
import java.math.BigDecimal;
import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class HistoricalSettlementPrecisionTest {
 @Test void incompatibleHistoricalBatchCannotProduceRoundedButUnbalancedNewReport()throws Exception {
  var jdbc=mock(JdbcTemplate.class);
  when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(Map.of("issued",new BigDecimal("1.234567"),"returned",BigDecimal.ONE,"output_qty",5L)));
  var service=new WarehouseService(jdbc,null);var method=WarehouseService.class.getDeclaredMethod("settleInternal",Map.class,Integer.class);method.setAccessible(true);
  var failure=assertThrows(InvocationTargetException.class,()->method.invoke(service,new HashMap<>(Map.of("status","OPEN","batch_id",42L,"planned_quantity",5L)),5));
  assertTrue(failure.getCause().getMessage().contains("批次#42历史领料合计"));
  verify(jdbc,never()).update(anyString(),any(Object[].class));
 }
}
