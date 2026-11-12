package com.ruoyi.metal.service;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.KeyHolder;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.metal.security.FactoryScope;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StorageReviewReassignmentTest {
 @Test void reviewManagersCanLoadTaskSpecificCandidatesWithoutWarehousePermissions(){
  var jdbc=fixture("RAW_IN");var scope=mock(FactoryScope.class);when(scope.resolveFactory(7L)).thenReturn(7L);
  when(jdbc.queryForList(startsWith("SELECT CAST(id AS CHAR) id,CAST(document_id"),any(Object[].class))).thenReturn(List.of(new LinkedHashMap<>(Map.of("id","5","documentId","10","assigneeId","3"))));
  when(jdbc.queryForList(contains("FROM sys_user WHERE factory_id=? AND status='0' AND del_flag='0' ORDER BY user_id"),eq(7L))).thenReturn(List.of(Map.of("id","2","name","原单发起人"),Map.of("id","3","name","有复验权限"),Map.of("id","4","name","无复验权限")));
  when(jdbc.queryForObject(anyString(),eq(Integer.class),eq(2L),eq(7L),eq("metal:warehouse-order:accept"))).thenReturn(1);
  when(jdbc.queryForObject(anyString(),eq(Integer.class),eq(4L),eq(7L),eq("metal:warehouse-order:accept"))).thenReturn(0);
  try(var security=mockStatic(SecurityUtils.class)){
   security.when(SecurityUtils::getUserId).thenReturn(1L);security.when(()->SecurityUtils.hasPermi("metal:storage-review:manage")).thenReturn(true);
   var result=new StorageOrderService(jdbc,scope,mock(StorageLedgerService.class),mock(ProductionExecutionService.class)).task(5,7L);
   assertEquals(List.of(Map.of("id","2","name","原单发起人"),Map.of("id","3","name","有复验权限")),result.get("reviewers"));
   security.when(SecurityUtils::getUserId).thenReturn(3L);
   assertEquals(result.get("reviewers"),new StorageOrderService(jdbc,scope,mock(StorageLedgerService.class),mock(ProductionExecutionService.class)).task(5,7L).get("reviewers"));
   verify(jdbc,times(2)).queryForList(contains("FROM sys_user WHERE factory_id=? AND status='0' AND del_flag='0' ORDER BY user_id"),eq(7L));
  }
 }
 @Test void productionIssueCannotBeReassignedAwayFromItsProcessor(){
  var jdbc=fixture("PRODUCTION_OUT");
  try(var security=mockStatic(SecurityUtils.class)){
   security.when(SecurityUtils::getUserId).thenReturn(1L);
   var error=assertThrows(ServiceException.class,()->service(jdbc).reassign(5,body(4)));
   assertTrue(error.getMessage().contains("加工人员"));
   assertTrue(mockingDetails(jdbc).getInvocations().stream().noneMatch(i->i.getMethod().getName().equals("update")));
  }
 }
 @Test void inboundReviewCanStillBeReassignedToAnotherEligiblePerson(){
  var jdbc=fixture("RAW_IN");
  try(var security=mockStatic(SecurityUtils.class)){
   security.when(SecurityUtils::getUserId).thenReturn(1L);
   assertEquals("4",service(jdbc).reassign(5,body(4)).get("reviewerId"));
   verify(jdbc).update("UPDATE metal_storage_review_task SET assignee_id=?,version=version+1 WHERE factory_id=? AND id=?",4L,7L,5L);
  }
 }
 private Map<String,Object> body(long person){return Map.of("factoryId",7L,"requestId",UUID.randomUUID().toString(),"version",0,"reviewerId",person,"reason","人员调班");}
 private StorageOrderService service(JdbcTemplate jdbc){var scope=mock(FactoryScope.class);when(scope.isAdmin()).thenReturn(true);when(scope.resolveFactory(7L)).thenReturn(7L);return new StorageOrderService(jdbc,scope,mock(StorageLedgerService.class),mock(ProductionExecutionService.class));}
 private JdbcTemplate fixture(String type){
  var jdbc=mock(JdbcTemplate.class);
  when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(invocation->{
   String sql=invocation.getArgument(0);
   if(sql.startsWith("SELECT * FROM metal_storage_review_task"))return List.of(Map.of("version",0,"document_id",10L,"assignee_id",3L));
   if(sql.startsWith("SELECT CAST(id AS CHAR) id,CAST(factory_id"))return List.of(new LinkedHashMap<>(Map.of("id","10","type",type,"status","PENDING_REVIEW","version",0,"reviewRound",1,"creatorId","2","reviewerId","3","executionId","9","data","{}")));
   if(sql.startsWith("SELECT processor_id FROM metal_production_execution"))return List.of(Map.of("processor_id",3L));
   return List.of();
  });
  when(jdbc.queryForObject(anyString(),eq(Integer.class),any(Object[].class))).thenReturn(1);
  when(jdbc.update(any(PreparedStatementCreator.class),any(KeyHolder.class))).thenAnswer(invocation->{KeyHolder holder=invocation.getArgument(1);holder.getKeyList().add(Map.of("id",99L));return 1;});
  return jdbc;
 }
}
