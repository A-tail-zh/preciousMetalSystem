package com.ruoyi.metal.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.metal.catalog.OutboundSubmissionRequests;
import com.ruoyi.metal.catalog.WarehouseRequests.Line;
import com.ruoyi.metal.security.FactoryScope;

class SemiProcessingV2Test {
    private static final class Fixture {
        final JdbcTemplate jdbc=mock(JdbcTemplate.class);
        final FactoryScope scope=mock(FactoryScope.class);
        final SemiFlowService semi=new SemiFlowService(jdbc,scope);
        final SemiSourcePolicyService policy=new SemiSourcePolicyService(jdbc,scope);
        final Map<String,Object> stock=new HashMap<>(Map.of("inventory_id",1L,"category","SEMI","reference_id",7L,
                "factory_id",1L,"origin_batch_id",100L,"ownership_status","NORMAL","organization","客户","specification","10cm",
                "metal_type_id_snapshot",2L,"smelting_status","PASSED"));
        final Map<String,Object> source=new HashMap<>(Map.of("batch_id",100L,"item_id",10L,"sequence_no",3L,
                "status","SETTLED","work_order_id",1L,"factory_id",1L,"product_id",7L,
                "product_snapshot","{\"specification\":\"10cm\",\"metalTypeId\":2}","customer","客户","work_status","CLOSED_PARTIAL"));
        final Map<String,Object> target=new HashMap<>(Map.of("item_id",20L,"sequence_no",1L,"work_order_id",2L,
                "processing_policy_version",2,"process_role","POST_PROCESS","output_stage","SEMI","batch_id",200L,
                "metal_type_id",2L,"process_id",30L));
        final Map<String,Object> item=new HashMap<>(Map.of("item_id",20L,"work_order_id",2L,"factory_id",1L,
                "customer","客户","product_id",7L,"product_snapshot","{\"specification\":\"10cm\",\"metalTypeId\":2}"));
        final Map<String,Object> release=new HashMap<>(Map.of("release_id",9L,"inventory_id",1L,"work_order_id",1L,"status","ACTIVE"));
        Fixture() {
            when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(call->{
                String sql=call.getArgument(0);
                if(sql.contains("metal_warehouse_order_line"))return List.of();
                if(sql.startsWith("SELECT b.*") || sql.startsWith("SELECT * FROM metal_process_batch"))return List.of(source);
                if(sql.startsWith("SELECT i.*"))return List.of(item);
                if(sql.startsWith("SELECT * FROM metal_semi_release"))return List.of(release);
                if(sql.startsWith("SELECT sequence_no,process_id"))return List.of(Map.of("sequence_no",1,"process_id",11),Map.of("sequence_no",2,"process_id",12),Map.of("sequence_no",3,"process_id",13));
                if(sql.startsWith("SELECT process_id"))return java.util.stream.LongStream.rangeClosed(1,((Number)call.getArgument(2)).longValue()).mapToObj(sequence->Map.<String,Object>of("process_id",10+sequence)).toList();
                if(sql.startsWith("SELECT execution_snapshot"))return List.of(new HashMap<String,Object>());
                if(sql.contains("used_quantity") || sql.contains("metal_route_skip_use"))return List.of();
                if(sql.startsWith("SELECT s.*"))return List.of(target);
                throw new AssertionError(sql);
            });
            when(jdbc.queryForObject(anyString(),eq(Long.class),any(Object[].class))).thenAnswer(call->call.<String>getArgument(0).startsWith("SELECT quantity")?12L:0L);
        }
    }

    @Test void qualifiedSameProductSemiCanEnterTheFirstV2PostProcess() {
        var f=new Fixture();assertSame(f.source,f.semi.validateSource(f.stock,f.target,9L));
        assertEquals(12,f.semi.entryCapacity(20,1));
        verify(f.jdbc,never()).update(anyString(),any(Object[].class));
    }

    @Test void firstEntryStillRequiresEveryOwnershipReleaseAndSnapshotCheck() {
        var f=new Fixture();assertThrows(ServiceException.class,()->f.semi.validateSource(f.stock,f.target,null));
        f.release.put("status","REVOKED");assertThrows(ServiceException.class,()->f.semi.validateSource(f.stock,f.target,9L));
        f.release.put("status","ACTIVE");f.item.put("customer","别的客户");assertThrows(ServiceException.class,()->f.semi.validateSource(f.stock,f.target,9L));
        f.item.put("customer","客户");f.stock.put("factory_id",2L);assertThrows(ServiceException.class,()->f.semi.validateSource(f.stock,f.target,9L));
        f.stock.put("factory_id",1L);f.item.put("product_id",8L);assertThrows(ServiceException.class,()->f.semi.validateSource(f.stock,f.target,9L));
        f.item.put("product_id",7L);f.item.put("product_snapshot","{\"specification\":\"20cm\"}");assertThrows(ServiceException.class,()->f.semi.validateSource(f.stock,f.target,9L));
    }

    @Test void firstEntryRejectsUnverifiedWrongMetalOpenAndSkippedSources() {
        var f=new Fixture();f.stock.put("smelting_status","UNVERIFIED");assertThrows(ServiceException.class,()->f.semi.validateSource(f.stock,f.target,9L));
        f.stock.put("smelting_status","PASSED");f.stock.put("metal_type_id_snapshot",1L);assertThrows(ServiceException.class,()->f.semi.validateSource(f.stock,f.target,9L));
        f.stock.put("metal_type_id_snapshot",2L);f.source.put("status","OPEN");assertThrows(ServiceException.class,()->f.semi.validateSource(f.stock,f.target,9L));
        f.source.put("status","SETTLED");f.source.put("work_status","IN_PROGRESS");assertThrows(ServiceException.class,()->f.semi.validateSource(f.stock,f.target,9L));
        f.source.put("work_status","CLOSED_PARTIAL");f.source.put("execution_snapshot","{\"processed\":[1,3],\"skipped\":[2]}");assertThrows(ServiceException.class,()->f.semi.validateSource(f.stock,f.target,9L));
    }

    @Test void firstEntryCannotBeUsedForV1OrLaterTargetSteps() {
        var f=new Fixture();f.target.put("processing_policy_version",1);assertThrows(ServiceException.class,()->f.semi.validateSource(f.stock,f.target,9L));
        f.target.put("processing_policy_version",2);f.target.put("sequence_no",2);assertThrows(ServiceException.class,()->f.semi.validateSource(f.stock,f.target,9L));
        f.target.put("sequence_no",1);f.target.put("process_role","SMELTING");assertThrows(ServiceException.class,()->f.semi.validateSource(f.stock,f.target,9L));
    }

    @Test void firstEntryDoesNotCopySourceRouteOrGrantPrecedingWorkCredit() {
        var f=new Fixture();var trace=f.policy.decide(f.source,f.target,null,null);
        assertEquals(List.of(1L),trace.get("processed"));assertEquals(List.of(),trace.get("skipped"));assertEquals(List.of(),trace.get("newSkipped"));
        assertEquals(0,f.semi.credit(20,1));
        assertThrows(ServiceException.class,()->f.policy.decide(f.source,f.target,4L,1));
        f.source.put("work_order_id",2L);assertThrows(ServiceException.class,()->f.policy.decide(f.source,f.target,null,null));
    }

    @Test void qualificationBindWritesOnlyExecutionProfileAndSeparateEvidence() {
        var f=new Fixture();try(var security=mockStatic(SecurityUtils.class)) {
        security.when(SecurityUtils::getUserId).thenReturn(7L);f.policy.bind(f.stock,f.target,null,null,88L);
        var sql=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(f.jdbc).update(sql.capture(),anyString(),eq(200L));
        assertEquals("UPDATE metal_process_batch SET execution_snapshot=? WHERE batch_id=?",sql.getValue());
        verify(f.jdbc).update(startsWith("INSERT INTO metal_semi_qualification_use"),eq(88L),eq(1L),eq(100L),eq(20L),eq(200L),anyString(),eq(7L));
        verify(f.jdbc,never()).update(contains("completed_quantity"),any(Object[].class));
        verify(f.jdbc,never()).update(startsWith("INSERT INTO metal_route_skip_use"),any(Object[].class));
        }
    }

    @Test void firstEntryCapacityDeductsActualNetIssuesAndPendingPieces() {
        var f=new Fixture();when(f.jdbc.queryForObject(contains("l.quantity"),eq(Long.class),any(Object[].class))).thenReturn(4L);
        when(f.jdbc.queryForObject(contains("metal_stock_reservation"),eq(Long.class),any(Object[].class))).thenReturn(3L);
        assertEquals(5L,f.semi.entryCapacity(20,1));
        f.target.put("processing_policy_version",1);assertEquals(0L,f.semi.entryCapacity(20,1));
    }

    @Test void outboundDraftUsesFirstEntryQualificationAndKeepsActualPieceCount() {
        var f=new Fixture();f.target.put("factory_id",1L);f.target.put("status","IN_PROGRESS");
        f.stock.put("quantity",5L);f.stock.put("net_weight",new java.math.BigDecimal("50"));
        f.release.put("quantity",5L);f.release.put("net_weight",new java.math.BigDecimal("50"));
        doReturn(List.of(f.target)).when(f.jdbc).queryForList(startsWith("SELECT s.sequence_no"),any(Object[].class));
        doReturn(List.of()).when(f.jdbc).queryForList(startsWith("SELECT batch_id,version"),any(Object[].class));
        doReturn(List.of(Map.of("factory_id",1L,"status","0"))).when(f.jdbc).queryForList(startsWith("SELECT factory_id,status FROM metal_warehouse"),any(Object[].class));
        doReturn(List.of(f.stock)).when(f.jdbc).queryForList(startsWith("SELECT * FROM metal_inventory"),any(Object[].class));
        doReturn(List.of()).when(f.jdbc).queryForList(contains("FROM metal_stock_reservation"),any(Object[].class));
        var imports=mock(WarehouseImportService.class);
        when(imports.inspect(any())).thenReturn(List.of(Map.of("inventoryId","1","netWeight","10","quantity",1L,"releaseId",9L)));
        var draft=new OutboundDraftService(f.jdbc,f.scope,mock(WarehouseService.class),imports);
        var line=new Line(1L,null,"SEMI",7L,"LOT",java.math.BigDecimal.TEN,1L,null,null,null,null,null,null);
        var request=new OutboundSubmissionRequests.Preview(1L,2L,20L,30L,null,null,List.of(new OutboundSubmissionRequests.Group(2L,null,List.of(line)))).request();
        var preview=draft.inspectConfirmation(request);
        assertEquals(List.of(),preview.get("fieldErrors"));assertEquals(1L,preview.get("totalQuantity"));
        assertEquals("1",((Map<?,?>)preview.get("entrySummary")).get("filledQuantity"));
        f.stock.put("smelting_status","UNVERIFIED");
        assertFalse(((List<?>)draft.inspectConfirmation(request).get("fieldErrors")).isEmpty());
        verify(f.jdbc,never()).update(anyString(),any(Object[].class));
    }

    @Test void v2RawCandidatesUseSnapshotMetalAndPreferOwnRawOutputWithoutReserving() {
        var jdbc=mock(JdbcTemplate.class);var scope=mock(FactoryScope.class);
        var target=new HashMap<String,Object>(Map.of("item_id",20L,"work_order_id",2L,"sequence_no",2L,
                "processing_policy_version",2,"process_role","PREPARATION","product_snapshot","{\"metalTypeId\":2}"));
        var external=new HashMap<String,Object>(Map.of("inventory_id",1L,"reference_id",11L,"category","RAW",
                "factory_id",1L,"net_weight",java.math.BigDecimal.TEN,"quantity",0L,"material_ownership","FACTORY",
                "powder_origin","UNKNOWN","washing_status","REQUIRED","origin_policy_version",2));
        external.put("material_type","块料");external.put("metal_type_id",2L);
        var own=new HashMap<>(external);own.put("inventory_id",2L);own.put("reference_id",12L);own.put("origin_batch_id",100L);own.put("same_work_raw_output",1);
        var wrong=new HashMap<>(external);wrong.put("inventory_id",3L);wrong.put("reference_id",13L);wrong.put("metal_type_id",1L);
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(call->{
            String sql=call.getArgument(0);
            if(sql.startsWith("SELECT * FROM metal_work_order"))return List.of(Map.of("factory_id",1L,"status","IN_PROGRESS"));
            if(sql.startsWith("SELECT s.*,i.work_order_id"))return List.of(target);
            if(sql.contains("same_work_raw_output"))return List.of(external,own,wrong);
            if(sql.startsWith("SELECT m.material_type")){long reference=((Number)call.getArgument(1)).longValue();return List.of(Map.of("material_type","块料","metal_type_id",reference==13?1L:2L));}
            if(sql.contains("metal_process_raw_return_line")||sql.contains("metal_stock_reservation"))return List.of();
            if(sql.startsWith("SELECT b.*"))return List.of(Map.of("batch_id",100L,"work_order_id",2L,"item_id",20L,"process_id",10L,"sequence_no",1L,"status","SETTLED"));
            if(sql.startsWith("SELECT order_no"))return List.of(Map.of("order_no","WORK-2"));
            if(sql.startsWith("SELECT process_name"))return List.of(Map.of("process_name","前处理"));
            throw new AssertionError(sql);
        });
        when(jdbc.queryForMap(anyString(),any(Object[].class))).thenAnswer(call->call.<String>getArgument(0).startsWith("SELECT item_id")?Map.of("item_id",20L,"sequence_no",1L,"status","SETTLED"):new HashMap<String,Object>());
        var candidates=new SemiFlowService(jdbc,scope).candidates(1,2,2,20,30);
        assertEquals(List.of("2","1"),candidates.stream().map(x->x.get("id")).toList());
        for(var candidate:candidates)for(String field:List.of("powder_origin","washing_status","origin_policy_version","material_type","metal_type_id"))assertTrue(candidate.containsKey(field),field);
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
}
