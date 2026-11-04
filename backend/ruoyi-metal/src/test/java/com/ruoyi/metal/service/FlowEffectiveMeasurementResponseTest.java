package com.ruoyi.metal.service;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ruoyi.metal.security.FactoryScope;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FlowEffectiveMeasurementResponseTest {
 @Test void rawReturnConfirmationResolvesOriginalInventoryAndDoesNotTrustLineJson(){
  var j=mock(JdbcTemplate.class);var scope=mock(FactoryScope.class);when(scope.isAdmin()).thenReturn(true);
  var reads=new int[1];
  when(j.queryForList(anyString(),any(Object[].class))).thenAnswer(c->{String q=c.getArgument(0);
   if(q.startsWith("SELECT * FROM metal_warehouse_flow WHERE"))return List.of(Map.of("flow_id",1L,"factory_id",7L));
   if(q.startsWith("SELECT * FROM metal_warehouse_flow_group"))return List.of(Map.of("group_id",2L,"order_id",3L,"order_json","{}","type","RAW_RETURN","status","WAITING"));
   if(q.startsWith("SELECT order_no,warehouse_name"))return List.of(Map.of("order_no","TEST"));
   if(q.startsWith("SELECT * FROM metal_warehouse_flow_line"))return List.of(Map.of("flow_line_id",4L,"source_line_id",19L,"line_json","{\"category\":\"RAW\",\"effectiveMeasurementMode\":\"WEIGHT\",\"measurementPolicyVersion\":1}"),Map.of("flow_line_id",5L,"inventory_id",9L,"line_json","{\"category\":\"RAW\",\"effectiveMeasurementMode\":\"PIECE\",\"measurementPolicyVersion\":3}"));
   if(q.contains("mode_flow_line_id"))return List.of(new HashMap<>(Map.of("mode_flow_line_id",4L,"inventory_id",8L,"origin_batch_id",18L,"category","RAW","measurement_mode","PIECE")),new HashMap<>(Map.of("mode_flow_line_id",5L,"inventory_id",9L,"category","RAW","measurement_mode","PIECE")));
   if(q.startsWith("SELECT inventory_id,origin_batch_id FROM")){reads[0]++;return List.of(Map.of("inventory_id",8L,"origin_batch_id",18L),Map.of("inventory_id",9L));}
   if(q.startsWith("SELECT b.batch_id,b.measurement_mode"))return List.of(Map.of("batch_id",18L,"measurement_mode","PIECE","product_snapshot","{\"measurementPolicyVersion\":3}"));
   if(q.startsWith("SELECT inventory_id,origin_batch_id,code"))return List.of(Map.of("inventory_id",9L,"category","RAW","measurement_mode","PIECE"));
   if(q.contains("metal_warehouse_flow_processor")||q.contains("metal_warehouse_flow_event"))return List.of();
   throw new AssertionError(q);
  });
  var warehouse=new WarehouseService(j,scope);var service=new WarehouseWorkflowService(j,scope,warehouse,mock(WarehouseImportService.class),mock(OutboundDraftService.class));
  var detail=service.detail(1);var group=(Map<?,?>)((List<?>)detail.get("groups")).get(0);var lines=(List<?>)group.get("lines");
  var first=(Map<?,?>)lines.get(0);var line=(Map<?,?>)first.get("line");assertEquals("PIECE",line.get("effectiveMeasurementMode"));assertEquals(3,line.get("measurementPolicyVersion"));assertEquals("PIECE",((Map<?,?>)first.get("stock")).get("effectiveMeasurementMode"));
  var legacy=(Map<?,?>)lines.get(1);assertEquals("WEIGHT",((Map<?,?>)legacy.get("line")).get("effectiveMeasurementMode"));assertEquals("WEIGHT",((Map<?,?>)legacy.get("stock")).get("effectiveMeasurementMode"));assertEquals(1,reads[0],"resolve a flow's stock origins in one batch");
 }
}
