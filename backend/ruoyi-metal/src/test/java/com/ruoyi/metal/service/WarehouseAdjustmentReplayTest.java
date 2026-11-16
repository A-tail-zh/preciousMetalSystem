package com.ruoyi.metal.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.metal.catalog.WarehouseRequests.Adjustment;
import com.ruoyi.metal.security.FactoryScope;

class WarehouseAdjustmentReplayTest {
 @Test void archivedInventoryStillAllowsOnlyItsOriginalSuccessfulReplay()throws Exception{try(var security=mockStatic(SecurityUtils.class)){
  security.when(SecurityUtils::getUserId).thenReturn(7L);var jdbc=mock(JdbcTemplate.class);var scope=mock(FactoryScope.class);when(scope.isAdmin()).thenReturn(true);var service=new WarehouseService(jdbc,scope);var input=new Adjustment(UUID.randomUUID().toString(),0,7L,7L,BigDecimal.ONE.negate(),0L,"归零");var fingerprint=WarehouseService.class.getDeclaredMethod("fingerprint",Object.class);fingerprint.setAccessible(true);String hash=(String)fingerprint.invoke(service,input.toString()+":1");
  when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(c->{String q=c.getArgument(0);if(q.contains("metal_warehouse_request"))return List.of(Map.of("kind","inventory-adjust","request_hash",hash,"actor_id",7L,"result_id",99L));if(q.contains("metal_inventory"))return q.contains("del_flag='0'")?List.of():List.of(Map.of("inventory_id",1L,"factory_id",2L));throw new AssertionError(q);});
  assertEquals("99",assertDoesNotThrow(()->service.adjustStock(1L,input)));verify(scope).check(2L);verify(scope).lockFactories(List.of(2L));verify(jdbc,never()).update(anyString(),any(Object[].class));security.when(SecurityUtils::getUserId).thenReturn(8L);assertThrows(ServiceException.class,()->service.adjustStock(1L,input));
 }}
 @Test void archivedInventoryRejectsANewAdjustmentBeforeAnyWrite(){
  var jdbc=mock(JdbcTemplate.class);var scope=mock(FactoryScope.class);when(scope.isAdmin()).thenReturn(true);var service=new WarehouseService(jdbc,scope);
  when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(c->{String q=c.getArgument(0);if(q.contains("metal_inventory")&&!q.contains("del_flag='0'"))return List.of(Map.of("inventory_id",1L,"factory_id",2L));return List.of();});
  assertThrows(ServiceException.class,()->service.adjustStock(1L,new Adjustment(UUID.randomUUID().toString(),0,7L,7L,BigDecimal.ONE,0L,"新调整")));verify(jdbc,never()).update(anyString(),any(Object[].class));
 }
 @Test void successfulReplayPrecedesMutableReleaseChecksAndNeverWritesAgain()throws Exception{try(var security=mockStatic(SecurityUtils.class)){
  security.when(SecurityUtils::getUserId).thenReturn(7L);var jdbc=mock(JdbcTemplate.class);var scope=mock(FactoryScope.class);when(scope.isAdmin()).thenReturn(true);var service=new WarehouseService(jdbc,scope);var semi=mock(SemiFlowService.class);var field=WarehouseService.class.getDeclaredField("semi");field.setAccessible(true);field.set(service,semi);doThrow(new ServiceException("库存已释放")).when(semi).guardStock(1L);
  var input=new Adjustment(UUID.randomUUID().toString(),0,7L,7L,BigDecimal.ONE,1L,"盘盈");var fingerprint=WarehouseService.class.getDeclaredMethod("fingerprint",Object.class);fingerprint.setAccessible(true);String hash=(String)fingerprint.invoke(service,input.toString()+":1");
  when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(c->{String q=c.getArgument(0);if(q.contains("metal_warehouse_request"))return List.of(Map.of("kind","inventory-adjust","request_hash",hash,"actor_id",7L,"result_id",99L));if(q.contains("metal_inventory"))return List.of(Map.of("inventory_id",1L,"factory_id",2L));throw new AssertionError(q);});
  assertEquals("99",assertDoesNotThrow(()->service.adjustStock(1L,input)));verify(semi,never()).guardStock(anyLong());verify(jdbc,never()).update(anyString(),any(Object[].class));
  security.when(SecurityUtils::getUserId).thenReturn(8L);assertThrows(ServiceException.class,()->service.adjustStock(1L,input));
  security.when(SecurityUtils::getUserId).thenReturn(7L);assertThrows(ServiceException.class,()->service.adjustStock(1L,new Adjustment(input.requestId(),0,7L,7L,BigDecimal.TEN,1L,"盘盈")));
 }}
 @Test void firstRequestStillChecksActiveRelease()throws Exception{
  var jdbc=mock(JdbcTemplate.class);var scope=mock(FactoryScope.class);when(scope.isAdmin()).thenReturn(true);var service=new WarehouseService(jdbc,scope);var semi=mock(SemiFlowService.class);var field=WarehouseService.class.getDeclaredField("semi");field.setAccessible(true);field.set(service,semi);doThrow(new ServiceException("库存已释放")).when(semi).guardStock(1L);
  when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(c->c.<String>getArgument(0).contains("metal_inventory")?List.of(Map.of("inventory_id",1L,"factory_id",2L)):List.of());
  assertThrows(ServiceException.class,()->service.adjustStock(1L,new Adjustment(UUID.randomUUID().toString(),0,7L,7L,BigDecimal.ONE,1L,"盘盈")));verify(semi).guardStock(1L);
 }
}
