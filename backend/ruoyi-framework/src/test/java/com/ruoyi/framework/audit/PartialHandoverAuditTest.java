package com.ruoyi.framework.audit;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PartialHandoverAuditTest {
    private static class Fixture {
        final JdbcTemplate jdbc=mock(JdbcTemplate.class);
        final AuditRecorder recorder=new AuditRecorder(jdbc,mock(PlatformTransactionManager.class));
        final AuditContext context=new AuditContext();
        final List<String> queries=new ArrayList<>();
        boolean created;long factory=7;int revision=1;
        Fixture() {
            context.path="/metal/warehouse-flows/handover/9";context.verb="POST";context.actorId=8L;context.actorFactory=7L;
            when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(call->{
                String sql=call.getArgument(0);queries.add(sql);
                if(sql.startsWith("SELECT flow_id id FROM metal_warehouse_flow WHERE request_id="))return created?List.of(Map.of("id",5000L)):List.of();
                if(sql.contains("WHERE batch_id=?")){
                    if(sql.contains("handover_policy_version=1"))return List.of();
                    var all=new ArrayList<Map<String,Object>>();for(long i=1;i<=1000;i++)all.add(Map.of("id",i));return all;
                }
                if(sql.startsWith("SELECT * FROM metal_warehouse_flow WHERE flow_id="))return List.of(new LinkedHashMap<>(Map.of("flow_id",5000L,"factory_id",factory,"handover_policy_version",2)));
                if(sql.startsWith("SELECT * FROM metal_handover_allocation"))return revision==1?List.of(Map.of("allocation_version",1,"voided",false,"allocated_weight","750")):List.of(Map.of("allocation_version",1,"voided",true,"allocated_weight","750"),Map.of("allocation_version",2,"voided",false,"allocated_weight","760"));
                if(sql.startsWith("SELECT w.* FROM metal_batch_wip_balance"))return List.of(Map.of("source_line_id",99L,"version",revision,"remaining_weight",revision==1?"250":"240"));
                return List.of();
            });
        }
    }
    @Test void creatingOneSegmentDoesNotSnapshotAThousandUnrelatedSegments() {
        var f=new Fixture();var body=Map.of("requestId","new-segment");
        assertTrue(f.recorder.snapshot(f.context,body,null).isEmpty());
        f.queries.clear();f.created=true;
        var after=f.recorder.snapshot(f.context,body,Map.of("id","5000"));
        assertEquals(Set.of("5000"),after.keySet());
        assertEquals(8,f.queries.size());
        assertEquals(1,f.queries.stream().filter(q->q.startsWith("SELECT * FROM metal_warehouse_flow WHERE flow_id=")).count());
        assertTrue(((Map<?,?>)after.get("5000")).containsKey("wipAllocations"));
    }
    @Test void revisionSnapshotsTheExactTargetAndKeepsPreviousAllocationVersion() {
        var f=new Fixture();var body=Map.of("requestId","revision","flowId",5000L);
        var before=f.recorder.snapshot(f.context,body,null);f.revision=2;
        var after=f.recorder.snapshot(f.context,body,Map.of("id","5000"));
        assertEquals(Set.of("5000"),before.keySet());assertEquals(before.keySet(),after.keySet());
        assertEquals(1,((List<?>)((Map<?,?>)before.get("5000")).get("wipAllocations")).size());
        assertEquals(2,((List<?>)((Map<?,?>)after.get("5000")).get("wipAllocations")).size());
    }
    @Test void postedForeignFlowIdCannotExposeAnotherFactoryWip() {
        var f=new Fixture();f.factory=9;
        assertTrue(f.recorder.snapshot(f.context,Map.of("requestId","forbidden","flowId",5000L),null).isEmpty());
        assertFalse(f.queries.stream().anyMatch(q->q.contains("metal_handover_allocation")));
        assertTrue(f.context.factories.isEmpty());
    }
}
