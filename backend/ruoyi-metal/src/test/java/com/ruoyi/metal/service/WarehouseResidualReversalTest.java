package com.ruoyi.metal.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ruoyi.common.exception.ServiceException;

class WarehouseResidualReversalTest {
 private JdbcTemplate jdbc(boolean laterCloseout) {
  var jdbc=mock(JdbcTemplate.class);
  when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(call->{
   String query=call.getArgument(0);
   if(query.startsWith("SELECT u.*"))return List.of(Map.of("use_id",8L,"residual_id",3L,"weight",new BigDecimal("4"),"initial_weight",BigDecimal.TEN,"remaining_weight",BigDecimal.ZERO));
   if(query.contains("metal_customer_residual_closeout"))return laterCloseout?List.of(Map.of("closeout_id",9L)):List.of();
   throw new AssertionError(query);
  });
  return jdbc;
 }
 @Test void laterCustomerCloseoutBlocksRestoringReissuedResidualBeforeAnyUpdate() {
  var jdbc=jdbc(true);
  var error=assertThrows(ServiceException.class,()->WarehouseResiduals.apply(jdbc,Map.of("category","RAW"),new BigDecimal("4"),20L,30L,40L));
  assertTrue(error.getMessage().contains("收尾"));
  verify(jdbc,never()).update(anyString(),any(Object[].class));
 }
 @Test void reissueReversalWithoutLaterCloseoutRestoresItsAttributedWeight() {
  var jdbc=jdbc(false);
  WarehouseResiduals.apply(jdbc,Map.of("category","RAW"),new BigDecimal("4"),20L,30L,40L);
  verify(jdbc).update("UPDATE metal_work_order_residual SET remaining_weight=remaining_weight+?,version=version+1 WHERE residual_id=?",new BigDecimal("4"),3L);
  verify(jdbc).update("UPDATE metal_work_order_residual_use SET reversed=TRUE WHERE use_id=?",8L);
 }
}
