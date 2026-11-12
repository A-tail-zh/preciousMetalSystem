package com.ruoyi.metal.service;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.metal.security.FactoryScope;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ProductionCompletionTest {
 private Map<String,Object> quantity(long planned,long completed){return Map.of("plannedQuantity",planned,"completedQuantity",completed);}
 private void verifyCompletion(List<Map<String,Object>> quantities,boolean allowed){
  var jdbc=mock(JdbcTemplate.class);var scope=mock(FactoryScope.class);when(scope.isAdmin()).thenReturn(true);when(scope.resolveFactory(9L)).thenReturn(9L);
  when(jdbc.queryForList(startsWith("SELECT * FROM metal_production_order WHERE"),eq(10L),eq(9L))).thenReturn(List.of(Map.of("flow_version",1,"status","IN_PROGRESS","version",0)));
  when(jdbc.queryForObject(startsWith("SELECT COUNT(*)"),eq(Integer.class),eq(10L))).thenReturn(0);
  when(jdbc.queryForList(startsWith("SELECT CAST(i.id AS CHAR) orderItemId"),eq(10L))).thenReturn(quantities);
  var user=new SysUser();user.setUserId(1L);var login=new LoginUser(1L,null,user,Set.of("*:*:*"));SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(login,null,List.of()));
  try{
   var service=new ProductionExecutionService(jdbc,scope);var body=Map.<String,Object>of("factoryId",9L,"version",0,"requestId",UUID.randomUUID().toString());
   if(allowed){assertEquals("COMPLETED",service.complete(10L,body).get("status"));verify(jdbc).update(startsWith("UPDATE metal_production_order SET status='COMPLETED'"),eq(10L));}
   else{assertTrue(assertThrows(ServiceException.class,()->service.complete(10L,body)).getMessage().contains("成品数量"));verify(jdbc,never()).update(startsWith("UPDATE metal_production_order SET status='COMPLETED'"),anyLong());}
  }finally{SecurityContextHolder.clearContext();}
 }
 @Test void shortageStillCannotComplete(){verifyCompletion(List.of(quantity(11,10)),false);}
 @Test void exactPlanCanComplete(){verifyCompletion(List.of(quantity(11,11)),true);}
 @Test void surplusCanCompleteWithoutChangingPlan(){verifyCompletion(List.of(quantity(11,13)),true);}
 @Test void surplusInOneGroupCannotCoverAnotherGroupsShortage(){verifyCompletion(List.of(quantity(11,13),quantity(5,4)),false);}
}
