package com.ruoyi.framework.audit;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerAuditTest {
    @Test void customerSnapshotsResolvePrimaryKeyFactoryAndMaskPhone() {
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(call->List.of(new LinkedHashMap<>(Map.of("customer_id",9L,"factory_id",7L,"contact_phone","13800138000"))));
        var recorder=new AuditRecorder(jdbc,mock(PlatformTransactionManager.class));
        var context=new AuditContext();context.path="/metal/customer";context.verb="POST";context.actorId=1L;
        var result=recorder.snapshot(context,Map.of(),"9");
        assertEquals("metal_customer",context.objectType);assertEquals(Set.of(7L),context.factories);
        assertEquals("138****8000",((Map<?,?>)result.get("9")).get("contact_phone"));
        verify(jdbc).queryForList("SELECT * FROM metal_customer WHERE customer_id=?",9L);
    }
    @Test void rejectedCrossFactoryOperationDoesNotCaptureForeignCustomer() {
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(new LinkedHashMap<>(Map.of("customer_id",9L,"factory_id",8L))));
        var context=new AuditContext();context.path="/metal/customer/9";context.verb="DELETE";context.actorId=2L;context.actorFactory=7L;
        assertTrue(new AuditRecorder(jdbc,mock(PlatformTransactionManager.class)).snapshot(context,Map.of(),null).isEmpty());assertTrue(context.factories.isEmpty());
    }
}
