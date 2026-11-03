package com.ruoyi.metal.service;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RawTaskDependencyLockTest {
 @Test void existingRawMaterialsAreLockedOnceInOrderBeforeTheWorkHeaderPhase()throws Exception {
  var jdbc=mock(JdbcTemplate.class);var locked=new ArrayList<Long>();
  when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(call->{String sql=call.getArgument(0);
   if(sql.startsWith("SELECT material_id FROM metal_work_order_item")){var empty=new HashMap<String,Object>();empty.put("material_id",null);return List.of(Map.of("material_id",9L),empty,Map.of("material_id",3L),Map.of("material_id",9L));}
   if(sql.startsWith("SELECT material_id FROM metal_material")&&sql.contains("FOR UPDATE"))locked.add(((Number)call.getArgument(1)).longValue());
   return List.of();
  });
  var method=WorkOrderService.class.getDeclaredMethod("lockDependencies",Long.class,List.class,Long.class);method.setAccessible(true);
  method.invoke(new WorkOrderService(jdbc,null,null,null),1L,List.of(),2L);
  assertEquals(List.of(3L,9L),locked);
 }
}
