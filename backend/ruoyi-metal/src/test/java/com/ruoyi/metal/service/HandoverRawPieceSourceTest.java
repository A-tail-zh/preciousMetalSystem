package com.ruoyi.metal.service;
import java.util.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ruoyi.metal.security.FactoryScope;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class HandoverRawPieceSourceTest {
 private List<Map<String,Object>> sources(int version)throws Exception{
  var j=mock(JdbcTemplate.class);
  when(j.queryForList(anyString(),any(Object[].class))).thenAnswer(c->{String q=c.getArgument(0);
   if(q.startsWith("SELECT inventory_id,origin_batch_id"))return List.of(Map.of("inventory_id",8L,"origin_batch_id",18L));
   if(q.startsWith("SELECT b.batch_id,b.measurement_mode"))return List.of(Map.of("batch_id",18L,"measurement_mode","PIECE","product_snapshot","{\"measurementPolicyVersion\":"+version+"}"));
   if(q.contains("l.*")&&q.contains("PRODUCTION_OUT"))return List.of(new HashMap<>(Map.of("line_id",4L,"inventory_id",8L,"category","RAW","measurement_mode","PIECE","quantity",10L,"net_weight",BigDecimal.TEN)));
   throw new AssertionError(q);
  });
  var service=new WarehouseWorkflowService(j,mock(FactoryScope.class),mock(WarehouseService.class),mock(WarehouseImportService.class),mock(OutboundDraftService.class));
  var m=WarehouseWorkflowService.class.getDeclaredMethod("processingSources",long.class);m.setAccessible(true);return (List<Map<String,Object>>)m.invoke(service,1L);
 }
 @Test void weightTargetDoesNotEraseRealPieceLimitsFromItsRawSource()throws Exception{
  var source=sources(3).get(0);assertFalse(InventoryMeasurement.weight(source));assertEquals("PIECE",source.get("effective_measurement_mode"));
  long issued=((Number)source.get("quantity")).longValue();assertTrue(11>issued,"11 returned pieces must exceed the actual ten issued pieces even if only 1g is returned");assertTrue(9<=issued);
 }
 @Test void historicalRawStillHasWeightSemantics()throws Exception{assertTrue(InventoryMeasurement.weight(sources(2).get(0)));}
}
