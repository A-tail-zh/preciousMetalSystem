package com.ruoyi.framework.audit;

import java.util.*;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Expected-failing review reproductions; no database, filesystem fallback or production writes. */
class AuditCaptureIntegrityTest {
    @Test void factoryLookupOutageMustReachFileFallback() {
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList(contains("SELECT factory_name"),any(Object[].class))).thenThrow(new DataAccessResourceFailureException("isolated simulated outage"));
        when(jdbc.update(anyString(),any(Object[].class))).thenThrow(new DataAccessResourceFailureException("outbox also unavailable"));
        var recorder=new AuditRecorder(jdbc,mock(PlatformTransactionManager.class));
        var c=new AuditContext();c.path="/metal/work-order";c.verb="POST";c.actorId=2L;c.actorFactory=7L;
        try(var files=mockStatic(AuditRecorder.class)) {
            files.when(()->AuditRecorder.fallback(anyMap())).thenAnswer(a->null);files.clearInvocations();
            assertDoesNotThrow(()->recorder.failure(c,"SYSTEM_ERROR","isolated outage"));
            files.verify(()->AuditRecorder.fallback(anyMap()));
        }
    }
    @Test void semiReleasePathMustNotSnapshotUnrelatedWarehouse() {
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(a->{String q=a.getArgument(0);return q.contains("FROM metal_warehouse WHERE warehouse_id")?List.of(new LinkedHashMap<>(Map.of("warehouse_id",77L,"factory_id",99L,"name","unrelated warehouse"))):List.of();});
        var c=new AuditContext();c.path="/metal/warehouse/inventory/77/semi-release";c.verb="POST";c.actorId=1L;
        new AuditRecorder(jdbc,mock(PlatformTransactionManager.class)).snapshot(c,77L,null);
        assertFalse(c.factories.contains(99L),"unrelated warehouse factory must not enter release audit scope");
        assertNotEquals("metal_warehouse",c.objectType);
    }
    @Test void multiParameterAdjustmentMustRetainActualRequestBody() throws Throwable {
        var recorder=mock(AuditRecorder.class);when(recorder.snapshot(any(),any(),any())).thenReturn(Map.of());
        var p=mock(ProceedingJoinPoint.class);when(p.getArgs()).thenReturn(new Object[]{11L,Map.of("reason","isolated reason","version",2,"requestId","fixture-id")});when(p.proceed()).thenReturn("99");
        var c=new AuditContext();c.path="/metal/inventory/11/adjust";c.verb="POST";c.title="库存调整";
        AuditContext.CURRENT.set(c);TransactionSynchronizationManager.setActualTransactionActive(true);
        try{new AuditServiceAspect(recorder).around(p);assertTrue(c.request.contains("isolated reason"),"captured actual request="+c.request);}
        finally{TransactionSynchronizationManager.clear();AuditContext.CURRENT.remove();}
    }
}
