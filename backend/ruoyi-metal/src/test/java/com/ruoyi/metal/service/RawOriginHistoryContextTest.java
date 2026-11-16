package com.ruoyi.metal.service;

import java.lang.reflect.*;
import java.util.*;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.metal.security.FactoryScope;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RawOriginHistoryContextTest {
 private static final class Fixture {
  final JdbcTemplate jdbc=mock(JdbcTemplate.class);final FactoryScope scope=mock(FactoryScope.class);
  final RawOriginService service=new RawOriginService(jdbc,scope);
  final List<Map<String,Object>> audit=List.of(Map.of("id","7","inventoryId","1","inventoryVersion",3,"actorName","核对员"));
  Fixture(){when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(call->
   call.<String>getArgument(0).contains("metal_inventory_origin_verification")?audit:List.of(Map.of("inventory_id",1L,"factory_id",2L,"version",99,"phone","should-not-leak")));
  }
  Object context()throws Exception {
   Method method=assertDoesNotThrow(()->RawOriginService.class.getMethod("historyContext",long.class),"Origin-only history needs a current-version context");
   try{return method.invoke(service,1L);}catch(InvocationTargetException failure){throw (Exception)failure.getCause();}
  }
 }
 private Object field(Object context,String name)throws Exception{return context.getClass().getMethod(name).invoke(context);}
 @Test void originOnlyPermissionGetsCurrentStockVersionAndUnchangedAuditArray()throws Exception {
  var f=new Fixture();try(var security=mockStatic(SecurityUtils.class)){
   security.when(()->SecurityUtils.hasPermi("metal:inventory:verify-origin")).thenReturn(true);
   Object context=f.context();assertEquals(99L,field(context,"version"));assertEquals(f.audit,field(context,"history"));
   assertEquals(f.audit,f.service.history(1L));verify(f.scope,times(2)).check(2L);
   security.verify(()->SecurityUtils.hasPermi("metal:inventory:query"),never());
   assertEquals(Set.of("history","version"),Arrays.stream(context.getClass().getRecordComponents()).map(RecordComponent::getName).collect(java.util.stream.Collectors.toSet()));
   verify(f.jdbc,never()).update(anyString(),any(Object[].class));
  }
 }
 @Test void deniedOriginPermissionReadsNeitherStockNorAudit()throws Exception {
  var f=new Fixture();try(var security=mockStatic(SecurityUtils.class)){
   assertEquals(403,assertThrows(ServiceException.class,f::context).getCode());verifyNoInteractions(f.jdbc);
  }
 }
 @Test void foreignFactoryDoesNotExposeVersionOrAudit()throws Exception {
  var f=new Fixture();when(f.scope.isAdmin()).thenReturn(true);doThrow(new ServiceException("跨厂",403)).when(f.scope).check(2L);
  assertEquals(403,assertThrows(ServiceException.class,f::context).getCode());
  verify(f.jdbc,never()).queryForList(contains("metal_inventory_origin_verification"),any(Object[].class));
 }
 @Test void historyAndCurrentVersionShareAReadOnlyRepeatableSnapshot(){
  Method method=assertDoesNotThrow(()->RawOriginService.class.getMethod("historyContext",long.class));
  var transaction=method.getAnnotation(Transactional.class);assertNotNull(transaction);assertTrue(transaction.readOnly());assertEquals(Isolation.REPEATABLE_READ,transaction.isolation());
 }
}
