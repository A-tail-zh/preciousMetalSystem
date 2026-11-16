package com.ruoyi.metal.service;

import java.util.*;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.metal.security.FactoryScope;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductionP4GuardTest {
 private JdbcTemplate jdbc;private FactoryScope scope;private ProductionExecutionService service;
 @BeforeEach void setup(){jdbc=mock(JdbcTemplate.class);scope=mock(FactoryScope.class);when(scope.isAdmin()).thenReturn(true);when(scope.resolveFactory(9L)).thenReturn(9L);service=new ProductionExecutionService(jdbc,scope);var user=new SysUser();user.setUserId(1L);var login=new LoginUser(1L,null,user,Set.of("*:*:*"));SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(login,null,List.of()));}
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 private Map<String,Object> command(){return new LinkedHashMap<>(Map.of("factoryId","9","requestId",UUID.randomUUID().toString(),"version",0,"reason","P4 guard regression","physicalChecked",true));}
 private void record(){when(jdbc.queryForList(startsWith("SELECT e.*,s.sequence_no"),eq(6L),eq(9L))).thenReturn(List.of(Map.of("id",6L,"batch_id",101L,"order_id",7L,"status","OPEN","version",0,"processor_id",1L)));}
 @Test void stageCloseRejectsForgedProductionIdentityBeforeAnyMutation(){record();var body=command();body.put("batchId","102");assertTrue(assertThrows(ServiceException.class,()->service.close(6,body)).getMessage().contains("不一致"));verify(jdbc,never()).update(anyString(),any(Object[].class));}
 @Test void stageCancelCannotUseAnInactiveBatchOrClaimAnotherIdentity(){record();var body=command();body.put("batchId","102");assertThrows(ServiceException.class,()->service.cancel(6,body));body.put("batchId","101");when(jdbc.queryForList(startsWith("SELECT lifecycle FROM metal_production_batch"),eq(9L),eq(101L))).thenReturn(List.of(Map.of("lifecycle","SPLIT")));assertTrue(assertThrows(ServiceException.class,()->service.cancel(6,body)).getMessage().contains("退出"));verify(jdbc,never()).update(anyString(),any(Object[].class));}
 @Test void stageCloseCannotContinueACompletedProductionBatch(){record();when(jdbc.queryForList(startsWith("SELECT lifecycle FROM metal_production_batch"),eq(9L),eq(101L))).thenReturn(List.of(Map.of("lifecycle","COMPLETED")));assertThrows(ServiceException.class,()->service.close(6,command()));verify(jdbc,never()).update(anyString(),any(Object[].class));}
 @Test void emptyActiveIdentityStillBlocksOrderCompletionEvenWithoutAPlan(){when(jdbc.queryForList(startsWith("SELECT * FROM metal_production_order WHERE"),eq(7L),eq(9L))).thenReturn(List.of(Map.of("flow_version",1,"status","IN_PROGRESS","version",0)));when(jdbc.queryForList(startsWith("SELECT b.id FROM metal_production_batch b WHERE b.order_id=? AND b.lifecycle='ACTIVE' AND NOT EXISTS"),eq(7L))).thenReturn(List.of(Map.of("id",101L)));assertTrue(assertThrows(ServiceException.class,()->service.complete(7,command())).getMessage().contains("未领料的手工计划批次"));verify(jdbc,never()).update(anyString(),any(Object[].class));}
 @Test void closedCounterWithoutAnActualOutputCannotCompleteARequiredStep(){when(jdbc.queryForList(startsWith("SELECT * FROM metal_production_order WHERE"),eq(7L),eq(9L))).thenReturn(List.of(Map.of("flow_version",1,"status","IN_PROGRESS","version",0)));when(jdbc.queryForList(startsWith("SELECT s.*,i.quantity"),eq(11L),eq(7L))).thenReturn(List.of(Map.of("id",11L,"item_id",3L,"sequence_no",1,"quantity",100,"measurement_mode","PIECE","output_category","SEMI","execution_closed",0)));when(jdbc.queryForObject(startsWith("SELECT COUNT(*)"),eq(Integer.class),eq(11L))).thenReturn(0);when(jdbc.queryForMap(startsWith("SELECT COUNT(DISTINCT CASE WHEN ev.id"),eq(11L))).thenReturn(Map.of("batches",0,"quantity",0));assertTrue(assertThrows(ServiceException.class,()->service.completeStep(7,11,command())).getMessage().contains("必需工序"));verify(jdbc,never()).update(anyString(),any(Object[].class));}
}
