package com.ruoyi.framework.audit;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ruoyi.metal.service.ProductionOrderService;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductionOrderAuditTest {
    @Test void snapshotContainsOnlyIdentityAndStatusAndMarksReplay(){
        var jdbc=mock(JdbcTemplate.class);var c=new AuditContext();c.actorId=2L;c.actorFactory=7L;
        var row=Map.<String,Object>of("id",3L,"factory_id",7L,"order_no","GD20261109000001","status","CREATED");
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(row));
        var input=new ProductionOrderService.Create(7L,UUID.randomUUID().toString(),null,"DIRECT_CUSTOM",null,4L,"2026-11-09",2L,List.of());
        var snapshot=new ProductionOrderAuditSnapshot(jdbc).snapshot(c,input,Map.of("replayed",true));
        assertEquals(row,snapshot.get("3"));assertTrue(c.replayed);assertEquals(Set.of(7L),c.factories);assertEquals("metal_production_order",c.objectType);
        c.actorFactory=8L;clearInvocations(jdbc);assertTrue(new ProductionOrderAuditSnapshot(jdbc).snapshot(c,input,null).isEmpty());verifyNoInteractions(jdbc);
    }
    @Test void mutationSnapshotCannotReadAnotherFactory(){
        var jdbc=mock(JdbcTemplate.class);var c=new AuditContext();c.actorId=2L;c.actorFactory=7L;c.path="/metal/production-order/batch-delete";
        var input=new ProductionOrderService.Delete(8L,UUID.randomUUID().toString(),List.of(new ProductionOrderService.Target(3L,0)));
        assertTrue(new ProductionOrderAuditSnapshot(jdbc).snapshot(c,input,null).isEmpty());verifyNoInteractions(jdbc);
        assertTrue(ProductionOrderAuditSnapshot.handles("/metal/production-order/3"));assertTrue(ProductionOrderAuditSnapshot.handles(c.path));
    }
}
