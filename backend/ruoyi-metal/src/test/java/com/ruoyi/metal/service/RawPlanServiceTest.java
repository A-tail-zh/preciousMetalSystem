package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.util.*;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.metal.security.FactoryScope;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RawPlanServiceTest {
 @Test void planCannotBeLowerThanCommittedAndPendingInput(){
  assertThrows(ServiceException.class,()->RawPlanService.requireMinimum(new BigDecimal("9.99"),new BigDecimal("8"),new BigDecimal("2")));
  assertDoesNotThrow(()->RawPlanService.requireMinimum(new BigDecimal("10"),new BigDecimal("8"),new BigDecimal("2")));
 }
 @Test void malformedChangeIsRejectedBeforeDatabaseAccess(){
  var jdbc=mock(JdbcTemplate.class);var service=new RawPlanService(jdbc,null);
  assertThrows(ServiceException.class,()->service.change(1L,new RawPlanService.Change("invalid",0,2L,BigDecimal.TEN,"调整")));
  verifyNoInteractions(jdbc);
 }
 @Test void internalInputUsesSameItemAndPostedPrimaryRawOutput(){
  var jdbc=mock(JdbcTemplate.class);
  when(jdbc.queryForObject(anyString(),eq(Long.class),any(Object[].class))).thenReturn(1L);
  assertTrue(RawPlanService.internalInput(jdbc,3L,2L));
  var sql=org.mockito.ArgumentCaptor.forClass(String.class);
  verify(jdbc).queryForObject(sql.capture(),eq(Long.class),eq(3L),eq(2L));
  assertTrue(sql.getValue().contains("RAW_PROCESS_IN"));
  assertTrue(sql.getValue().contains("b.item_id=?"));
 }

 private JdbcTemplate changeDatabase(String status,int version){
  var jdbc=mock(JdbcTemplate.class);
  when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(call->{
   String sql=call.getArgument(0);
   if(sql.startsWith("SELECT factory_id FROM"))return List.of(Map.of("factory_id",1L));
   if(sql.contains("metal_raw_plan_change"))return List.of();
   if(sql.startsWith("SELECT planned_input_weight"))return List.of(Map.of("planned_input_weight",new BigDecimal("100")));
   throw new AssertionError(sql);
  });
  when(jdbc.queryForMap(anyString(),any(Object[].class))).thenReturn(Map.of("factory_id",1L,"task_type","RAW_PROCESSING","status",status,"version",version));
  when(jdbc.queryForObject(anyString(),eq(BigDecimal.class),any(Object[].class))).thenAnswer(call->call.<String>getArgument(0).contains("metal_stock_reservation")?new BigDecimal("2"):new BigDecimal("8"));
  return jdbc;
 }
 @Test void lowerThanPendingOrStaleVersionDoesNotWrite(){
  var jdbc=changeDatabase("IN_PROGRESS",4);var service=new RawPlanService(jdbc,mock(FactoryScope.class));
  assertThrows(ServiceException.class,()->service.change(1L,new RawPlanService.Change(UUID.randomUUID().toString(),4,2L,new BigDecimal("9.99"),"调整")));
  assertThrows(ServiceException.class,()->service.change(1L,new RawPlanService.Change(UUID.randomUUID().toString(),3,2L,BigDecimal.TEN,"调整")));
  verify(jdbc,never()).update(anyString(),any(Object[].class));
 }
 @Test void completedPlanChangeOnlyWritesWeightVersionAndAudit(){
  var jdbc=changeDatabase("COMPLETED",4);var service=new RawPlanService(jdbc,mock(FactoryScope.class));
  try(var security=mockStatic(SecurityUtils.class)){
   security.when(SecurityUtils::getUserId).thenReturn(7L);security.when(SecurityUtils::getUsername).thenReturn("planner");
   var user=new SysUser();user.setNickName("计划员");var login=new LoginUser();login.setUser(user);security.when(SecurityUtils::getLoginUser).thenReturn(login);
   service.change(1L,new RawPlanService.Change(UUID.randomUUID().toString(),4,2L,new BigDecimal("110")," 补充来料 "));
   var sql=org.mockito.ArgumentCaptor.forClass(String.class);
   verify(jdbc,times(3)).update(sql.capture(),any(Object[].class));
   assertEquals(3,sql.getAllValues().size());
   assertTrue(sql.getAllValues().stream().anyMatch(value->value.contains("INSERT INTO metal_raw_plan_change")));
   assertTrue(sql.getAllValues().stream().noneMatch(value->value.contains("metal_work_order_step")||value.contains("status=")||value.contains("weight_stage_closed")));
  }
 }
 @Test void sameRequestReplaysBeforeVersionCheckButChangedPayloadOrActorIsRejected(){
  var jdbc=changeDatabase("IN_PROGRESS",4);var service=new RawPlanService(jdbc,mock(FactoryScope.class));
  var audit=new ArrayList<Map<String,Object>>();
  when(jdbc.queryForList(contains("metal_raw_plan_change"),any(Object[].class))).thenAnswer(call->audit);
  doAnswer(call->{audit.add(Map.of("request_hash",call.getArgument(2),"actor_id",call.getArgument(8)));return 1;}).when(jdbc).update(startsWith("INSERT INTO metal_raw_plan_change"),any(Object[].class));
  var input=new RawPlanService.Change(UUID.randomUUID().toString(),4,2L,new BigDecimal("110"),"调整");
  try(var security=mockStatic(SecurityUtils.class)){
   security.when(SecurityUtils::getUserId).thenReturn(7L);security.when(SecurityUtils::getUsername).thenReturn("planner");
   var user=new SysUser();user.setNickName("计划员");var login=new LoginUser();login.setUser(user);security.when(SecurityUtils::getLoginUser).thenReturn(login);
   service.change(1L,input);
   when(jdbc.queryForMap(anyString(),any(Object[].class))).thenReturn(Map.of("factory_id",1L,"task_type","RAW_PROCESSING","status","IN_PROGRESS","version",5));
   assertDoesNotThrow(()->service.change(1L,input));
   when(jdbc.queryForMap(anyString(),any(Object[].class))).thenReturn(Map.of("factory_id",1L,"task_type","RAW_PROCESSING","status","IN_PROGRESS","version",5,"del_flag","2"));
   assertDoesNotThrow(()->service.change(1L,input));
   assertThrows(ServiceException.class,()->service.change(1L,new RawPlanService.Change(UUID.randomUUID().toString(),5,2L,new BigDecimal("110"),"已删除不能新增变更")));
   assertThrows(ServiceException.class,()->service.change(1L,new RawPlanService.Change(input.requestId(),4,2L,new BigDecimal("111"),"调整")));
   security.when(SecurityUtils::getUserId).thenReturn(8L);assertThrows(ServiceException.class,()->service.change(1L,input));
   verify(jdbc,times(3)).update(anyString(),any(Object[].class));
  }
 }
}
