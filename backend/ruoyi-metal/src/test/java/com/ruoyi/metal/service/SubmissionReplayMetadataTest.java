package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.metal.catalog.WarehouseRequests.Line;
import com.ruoyi.metal.security.FactoryScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SubmissionReplayMetadataTest {
    private static final String REQUEST = "bd2c7980-7a1d-43f0-b65b-ac5e6053f408";

    private static String fingerprint(Class<?> service, Object request) throws Exception {
        var method = service.getDeclaredMethod("hash", service == WarehouseWorkflowService.class ? Object.class : request.getClass());
        method.setAccessible(true);
        return (String) method.invoke(null, request);
    }

    private record Modern(JdbcTemplate jdbc, FactoryScope scope, Map<String,Object> saved,
                          WarehouseWorkflowService service) {}

    private Modern modern(String hash) {
        var jdbc = mock(JdbcTemplate.class);
        var scope = mock(FactoryScope.class);
        when(scope.isAdmin()).thenReturn(true);
        var saved = new HashMap<String,Object>(Map.of("flow_id",99L,"factory_id",2L,"request_actor",7L,"request_hash",hash,"status","WAITING"));
        when(jdbc.queryForList(eq("SELECT * FROM metal_warehouse_flow WHERE request_id=?"),any(Object[].class))).thenReturn(List.of(saved));
        when(jdbc.queryForList(eq("SELECT * FROM metal_warehouse_flow WHERE flow_id=?"),any(Object[].class))).thenReturn(List.of(saved));
        var warehouse = mock(WarehouseService.class);
        when(warehouse.clean(anyMap())).thenAnswer(call -> new LinkedHashMap<>(call.<Map<String,Object>>getArgument(0)));
        when(warehouse.cleaned(anyList())).thenReturn(List.of());
        return new Modern(jdbc,scope,saved,new WarehouseWorkflowService(jdbc,scope,warehouse,mock(WarehouseImportService.class),mock(OutboundDraftService.class)));
    }

    private com.ruoyi.metal.catalog.InboundSubmissionRequests.Request inboundRequest() {
        var line = new Line(null,null,"RAW",1L,null,BigDecimal.ONE,0L,null,null,null,null,null,null);
        return new com.ruoyi.metal.catalog.InboundSubmissionRequests.Request(REQUEST,2L,"RAW_IN",LocalDate.of(2026,11,3),List.of(7L),8L,null,null,null,null,null,null,null,false,null,
                List.of(new com.ruoyi.metal.catalog.InboundSubmissionRequests.Group(3L,null,List.of(line))));
    }

    @Test void modernCreateMarksOnlyTheValidatedSuccessfulReplay() {
        try (var security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUserId).thenReturn(7L);
            var f = modern("same-hash");
            var replay = f.service.create(REQUEST,2L,List.of(),"same-hash");
            assertEquals(Boolean.TRUE,replay.get("reused"));
            assertEquals("99",replay.get("id"));
            verify(f.scope).check(2L);
            verify(f.scope).lockFactories(List.of(2L));
            verify(f.jdbc,never()).update(anyString(),any(Object[].class));
            assertFalse(f.service.detail(99L).containsKey("reused"),"GET detail is not a submission replay");
        }
    }

    @Test void modernInboundPropagatesTheActualCreateReplayToTheSubmissionEnvelope() throws Exception {
        try (var security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUserId).thenReturn(7L);
            var request = inboundRequest();
            var f = modern(fingerprint(WarehouseWorkflowService.class,List.of("MULTI_IN",request)));
            var result = f.service.inbound(request);
            assertEquals(Boolean.TRUE,result.get("reused"));
            assertEquals(Boolean.TRUE,((Map<?,?>)result.get("flow")).get("reused"));
            assertEquals("99",result.get("flowId"));
            verify(f.scope).check(2L);
        }
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void modernReplayStillRejectsDifferentActorOrContent(boolean changedActor) {
        try (var security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUserId).thenReturn(changedActor ? 8L : 7L);
            var f = modern("saved-hash");
            assertEquals(409,assertThrows(ServiceException.class,()->f.service.create(REQUEST,2L,List.of(),changedActor ? "saved-hash" : "different-hash")).getCode());
            verify(f.jdbc,never()).queryForList(eq("SELECT * FROM metal_warehouse_flow WHERE flow_id=?"),any(Object[].class));
            verify(f.jdbc,never()).update(anyString(),any(Object[].class));
        }
    }

    @Test void modernReplayStillChecksTheSavedFactoryBeforeReturningMetadata() {
        try (var security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUserId).thenReturn(7L);
            var f = modern("same-hash");
            f.saved.put("factory_id",3L);
            doThrow(new ServiceException("not this factory",403)).when(f.scope).check(3L);
            assertEquals(403,assertThrows(ServiceException.class,()->f.service.create(REQUEST,2L,List.of(),"same-hash")).getCode());
        }
    }

    private record Legacy(JdbcTemplate jdbc, FactoryScope scope, Map<String,Object> saved,
                          Supplier<Map<String,Object>> submit, Supplier<Map<String,Object>> detail) {}

    private Legacy legacy(boolean outbound) throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        var scope = mock(FactoryScope.class);
        when(scope.isAdmin()).thenReturn(true);
        var warehouse = mock(WarehouseService.class);
        String table = outbound ? "metal_outbound_submission" : "metal_inbound_submission";
        var saved = new HashMap<String,Object>(Map.of("submission_id",99L,"factory_id",2L,"request_actor",7L));
        Supplier<Map<String,Object>> submit;
        Supplier<Map<String,Object>> detail;
        if (outbound) {
            var line = new Line(1L,null,null,null,null,BigDecimal.ONE,0L,null,null,null,null,null,null);
            var request = new com.ruoyi.metal.catalog.OutboundSubmissionRequests.Request(REQUEST,2L,"PRODUCTION_OUT",LocalDate.of(2026,11,3),List.of(),7L,8L,null,10L,11L,12L,9L,0,
                    List.of(new com.ruoyi.metal.catalog.OutboundSubmissionRequests.Group(3L,null,List.of(line))));
            saved.put("request_hash",fingerprint(OutboundSubmissionService.class,request));
            saved.put("batch_id",9L);
            when(jdbc.queryForMap(anyString(),any(Object[].class))).thenReturn(Map.of("batch_no","B9","version",0,"status","OPEN"));
            var service = new OutboundSubmissionService(jdbc,scope,warehouse,mock(OutboundDraftService.class));
            submit = () -> service.submit(request);
            detail = () -> service.detail(99L);
        } else {
            var request = inboundRequest();
            saved.put("request_hash",fingerprint(InboundSubmissionService.class,request));
            var service = new InboundSubmissionService(jdbc,scope,warehouse);
            submit = () -> service.submit(request);
            detail = () -> service.detail(99L);
        }
        when(jdbc.queryForObject(contains("SELECT COUNT(*) FROM metal_warehouse_order"),eq(Long.class),any(Object[].class))).thenReturn(0L);
        when(jdbc.queryForList(eq("SELECT * FROM "+table+" WHERE request_id=? FOR UPDATE"),any(Object[].class))).thenReturn(List.of(saved));
        when(jdbc.queryForList(eq("SELECT * FROM "+table+" WHERE submission_id=?"),any(Object[].class))).thenReturn(List.of(saved));
        return new Legacy(jdbc,scope,saved,submit,detail);
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void legacySuccessfulReplayIsMarkedWithoutPostingAgain(boolean outbound) throws Exception {
        try (var security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUserId).thenReturn(7L);
            var f = legacy(outbound);
            var replay = f.submit.get();
            assertEquals(Boolean.TRUE,replay.get("reused"));
            assertEquals("99",replay.get("submissionId"));
            verify(f.scope,times(2)).check(2L);
            verify(f.jdbc,never()).update(anyString(),any(Object[].class));
        }
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void legacyReplayStillRejectsDifferentActorAndContent(boolean outbound) throws Exception {
        try (var security = mockStatic(SecurityUtils.class)) {
            var f = legacy(outbound);
            security.when(SecurityUtils::getUserId).thenReturn(8L);
            assertEquals(409,assertThrows(ServiceException.class,f.submit::get).getCode());
            security.when(SecurityUtils::getUserId).thenReturn(7L);
            f.saved.put("request_hash","different-hash");
            assertEquals(409,assertThrows(ServiceException.class,f.submit::get).getCode());
            verify(f.jdbc,never()).update(anyString(),any(Object[].class));
        }
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void legacyReplayStillChecksTheSavedFactoryBeforeReturningMetadata(boolean outbound) throws Exception {
        try (var security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUserId).thenReturn(7L);
            var f = legacy(outbound);
            f.saved.put("factory_id",3L);
            doThrow(new ServiceException("not this factory",403)).when(f.scope).check(3L);
            assertEquals(403,assertThrows(ServiceException.class,f.submit::get).getCode());
        }
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void legacyGetDetailDoesNotInventASubmissionReplayFlag(boolean outbound) throws Exception {
        var f = legacy(outbound);
        assertFalse(f.detail.get().containsKey("reused"));
    }
}
