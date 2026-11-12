package com.ruoyi.metal.service;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ruoyi.metal.security.FactoryScope;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ProductionIssueEligibilityTest {
 private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
 private final ProductionExecutionService service=new ProductionExecutionService(jdbc,mock(FactoryScope.class));
 private void settled(){
  when(jdbc.queryForObject(startsWith("SELECT COUNT(*) FROM metal_storage_document WHERE order_id"),eq(Integer.class),eq(21L))).thenReturn(0);
  when(jdbc.queryForList(startsWith("SELECT CAST(i.id AS CHAR) orderItemId"),eq(21L))).thenReturn(List.of(Map.of("plannedQuantity",1,"completedQuantity",1)));
  when(jdbc.queryForList(startsWith("SELECT s.*,i.quantity FROM"),eq(21L))).thenReturn(List.of(Map.of("id",62L,"item_id",25L,"sequence_no",1,"measurement_mode","PIECE","output_category","PRODUCT","quantity",1)));
  when(jdbc.queryForMap(startsWith("SELECT COUNT(DISTINCT CASE"),eq(62L))).thenReturn(Map.of("batches",1,"quantity",1));
 }
 @Test void settledAcceptedProductionDoesNotOfferMoreMaterial(){settled();assertFalse(service.productionIssueAllowed(21L,"IN_PROGRESS"));assertTrue(mockingDetails(jdbc).getInvocations().stream().noneMatch(i->i.getMethod().getName().equals("update")||Arrays.stream(i.getArguments()).anyMatch(a->a instanceof String&&((String)a).contains("FOR UPDATE"))));}
 @Test void acceptedProductDoesNotHideUnsettledMaterial(){settled();when(jdbc.queryForList(contains("AND status='OPEN'"),any(Object[].class))).thenReturn(List.of(Map.of("id",36L)));assertTrue(service.productionIssueAllowed(21L,"IN_PROGRESS"));}
 @Test void pendingReviewKeepsOrderAvailable(){settled();when(jdbc.queryForObject(startsWith("SELECT COUNT(*) FROM metal_storage_document WHERE order_id"),eq(Integer.class),eq(21L))).thenReturn(1);assertTrue(service.productionIssueAllowed(21L,"IN_PROGRESS"));}
 @Test void uncoveredRouteKeepsOrderAvailable(){settled();when(jdbc.queryForMap(startsWith("SELECT COUNT(DISTINCT CASE"),eq(62L))).thenReturn(Map.of("batches",0,"quantity",0));assertTrue(service.productionIssueAllowed(21L,"IN_PROGRESS"));}
 @Test void unissuedManualBatchKeepsOrderAvailable(){settled();when(jdbc.queryForList(startsWith("SELECT b.id FROM metal_production_batch"),eq(21L))).thenReturn(List.of(Map.of("id",17)));assertTrue(service.productionIssueAllowed(21L,"IN_PROGRESS"));}
 @Test void shortageKeepsOrderAvailable(){settled();when(jdbc.queryForList(startsWith("SELECT CAST(i.id AS CHAR) orderItemId"),eq(21L))).thenReturn(List.of(Map.of("plannedQuantity",2,"completedQuantity",1)));assertTrue(service.productionIssueAllowed(21L,"IN_PROGRESS"));}
 @Test void staleNewIssueIsRejectedBeforeCreatingBatch(){settled();when(jdbc.queryForList(startsWith("SELECT * FROM metal_production_order WHERE id"),eq(21L),eq(13516L))).thenReturn(List.of(Map.of("status","IN_PROGRESS")));var doc=new HashMap<String,Object>(Map.of("type","PRODUCTION_OUT","executionMode","NEW","orderId",21L,"orderItemId",25L,"orderStepId",62L,"processorId",1L));assertTrue(assertThrows(com.ruoyi.common.exception.ServiceException.class,()->service.ensureExecutionForOutbound(13516L,doc,List.of(Map.of("stockId",1L)))).getMessage().contains("不能新增生产领料"));verify(jdbc,never()).update(anyString(),any(Object[].class));}
 @Test void formalCompletionAlwaysDisallowsNewIssue(){assertFalse(service.productionIssueAllowed(21L,"COMPLETED"));assertFalse(service.productionIssueAllowed(21L,"EXCEPTION_COMPLETED"));verifyNoInteractions(jdbc);}
}
