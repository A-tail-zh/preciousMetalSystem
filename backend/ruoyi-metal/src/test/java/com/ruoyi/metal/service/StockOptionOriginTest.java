package com.ruoyi.metal.service;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ruoyi.metal.security.FactoryScope;
class StockOptionOriginTest {
 @Test void stockOriginsUseSavedStepAndPreserveEachInventory(){
  var jdbc=mock(JdbcTemplate.class);var scope=mock(FactoryScope.class);when(scope.isAdmin()).thenReturn(true);
  when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(a->{String sql=a.getArgument(0);
   assertTrue(sql.contains("LEFT JOIN metal_work_order w ON w.work_order_id=b.work_order_id"));
   assertTrue(sql.contains("LEFT JOIN metal_work_order_step s ON s.item_id=b.item_id AND s.process_id=b.process_id"));
   assertTrue(sql.contains("s.process_name origin_process_name"));assertTrue(sql.contains("b.sequence_no origin_process_sequence"));assertTrue(sql.contains("w.order_no origin_work_order_no"));
   assertFalse(sql.contains("JOIN metal_process p"));assertFalse(sql.contains("GROUP BY"));assertTrue(sql.contains("i.factory_id=? AND i.del_flag='0'"));
   return List.of(Map.of("id",11L,"code","A001","category","SEMI","origin_process_name","熔炼快照","origin_process_sequence",1,"origin_work_order_no","WO-A","batch_no","B-A"),Map.of("id",12L,"code","A001","category","SEMI","origin_process_name","成型快照","origin_process_sequence",2,"origin_work_order_no","WO-A","batch_no","B-B"));
  });
  var rows=new WarehouseService(jdbc,scope).options("stocks",3L,null);assertEquals(2,rows.size());assertEquals("11",rows.get(0).get("id"));assertEquals("12",rows.get(1).get("id"));assertEquals("熔炼快照",rows.get(0).get("originProcessName"));assertEquals(2,rows.get(1).get("originProcessSequence"));assertEquals("WO-A",rows.get(1).get("originWorkOrderNo"));verify(scope).check(3L);verify(jdbc).queryForList(anyString(),eq(3L));
 }
 @Test void unboundInventoryIsNotLostWhenOriginsAreMissing(){
  var jdbc=mock(JdbcTemplate.class);var scope=mock(FactoryScope.class);when(scope.isAdmin()).thenReturn(true);var row=new HashMap<String,Object>(Map.of("id",13L,"code","EXT","category","SEMI"));row.put("origin_process_name",null);row.put("origin_process_sequence",null);row.put("origin_work_order_no",null);when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(row));var rows=new WarehouseService(jdbc,scope).options("stocks",3L,null);assertEquals(1,rows.size());assertNull(rows.get(0).get("originProcessName"));assertNull(rows.get(0).get("originProcessSequence"));
 }
}
