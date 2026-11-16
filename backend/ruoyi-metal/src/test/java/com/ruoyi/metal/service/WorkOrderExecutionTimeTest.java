package com.ruoyi.metal.service;

import java.sql.Connection;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class WorkOrderExecutionTimeTest {
    private Map<String,Object> plan() {
        var plan=new HashMap<String,Object>();
        plan.put("processId","3");
        plan.put("actualStartTime","2026-10-01 09:00:00");
        plan.put("actualEndTime","2026-10-01 10:00:00");
        return plan;
    }

    private void calculate(JdbcTemplate jdbc,Map<String,Object> plan)throws Exception {
        var service=new WorkOrderService(jdbc,null,null,null);
        var method=WorkOrderService.class.getDeclaredMethod("executionTimes",Long.class,List.class);
        method.setAccessible(true);
        method.invoke(service,1L,List.of(Map.of("id","2","plans",List.of(plan))));
    }

    @Test void noEffectiveIssuesClearBothLegacyTimes()throws Exception {
        var plan=plan();
        calculate(mock(JdbcTemplate.class),plan);
        assertNull(plan.get("actualStartTime"));
        assertNull(plan.get("actualEndTime"));
    }

    @Test void effectiveOpenBatchKeepsItsStartButClearsTheOldEnd()throws Exception {
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(Map.of("item_id",2L,"process_id",3L,"firstIssue",java.sql.Timestamp.valueOf("2026-11-03 11:00:00"),"openCount",1L)));
        var plan=plan();calculate(jdbc,plan);
        assertEquals("2026-11-03 11:00:00",plan.get("actualStartTime"));
        assertNull(plan.get("actualEndTime"));
    }

    // Connection-local temporary tables exercise the real SQL without altering registered fixture data.
    private void withIssue(String facts,String expectedStart)throws Exception {
        try(Connection connection=WarehouseIntegrationEnvironment.dataSource().getConnection()) {
            var jdbc=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
            jdbc.execute("CREATE TEMPORARY TABLE metal_process_batch(batch_id BIGINT,item_id BIGINT,process_id BIGINT,work_order_id BIGINT,status VARCHAR(16))");
            jdbc.execute("CREATE TEMPORARY TABLE metal_warehouse_order(order_id BIGINT,batch_id BIGINT,status VARCHAR(16),type VARCHAR(32),create_time DATETIME,confirmed_at DATETIME,warehouse_flow_id BIGINT)");
            jdbc.execute("CREATE TEMPORARY TABLE metal_work_report(batch_id BIGINT,voided BOOLEAN,create_time DATETIME)");
            jdbc.execute("CREATE TEMPORARY TABLE metal_warehouse_flow(flow_id BIGINT,status VARCHAR(16),posted_time DATETIME)");
            jdbc.execute("INSERT INTO metal_process_batch VALUES(4,2,3,1,'OPEN')");
            for(String sql:facts.split(";"))jdbc.execute(sql);
            var plan=plan();calculate(jdbc,plan);
            assertEquals(expectedStart,plan.get("actualStartTime"));
            assertNull(plan.get("actualEndTime"));
        }
    }

    @Test @EnabledIfEnvironmentVariable(named="METAL_FLOW_ISOLATED_JDBC_URL",matches="jdbc:mysql://(?:127\\.0\\.0\\.1|localhost):3321/(?:heavy_metal_flow|heavy_metal_factory_20261104)(?:\\?.*)?")
    void directHistoricalPostingUsesCreationTime()throws Exception {
        withIssue("INSERT INTO metal_warehouse_order VALUES(5,4,'POSTED','PRODUCTION_OUT','2026-11-03 09:00:00',NULL,NULL)","2026-11-03 09:00:00");
    }

    @Test @EnabledIfEnvironmentVariable(named="METAL_FLOW_ISOLATED_JDBC_URL",matches="jdbc:mysql://(?:127\\.0\\.0\\.1|localhost):3321/(?:heavy_metal_flow|heavy_metal_factory_20261104)(?:\\?.*)?")
    void multiWarehouseStartsAtFinalPostingNotCreationOrFirstConfirmation()throws Exception {
        withIssue("INSERT INTO metal_warehouse_flow VALUES(6,'POSTED','2026-11-03 11:00:00');INSERT INTO metal_warehouse_order VALUES(5,4,'POSTED','PRODUCTION_OUT','2026-11-03 08:00:00','2026-11-03 10:00:00',6),(7,4,'POSTED','PRODUCTION_OUT','2026-11-03 09:00:00','2026-11-03 11:00:00',6)","2026-11-03 11:00:00");
    }

    @Test @EnabledIfEnvironmentVariable(named="METAL_FLOW_ISOLATED_JDBC_URL",matches="jdbc:mysql://(?:127\\.0\\.0\\.1|localhost):3321/(?:heavy_metal_flow|heavy_metal_factory_20261104)(?:\\?.*)?")
    void comparesEffectiveTimesAcrossHistoricalAndWorkflowIssues()throws Exception {
        withIssue("INSERT INTO metal_warehouse_flow VALUES(6,'POSTED','2026-11-03 11:00:00');INSERT INTO metal_warehouse_order VALUES(5,4,'POSTED','PRODUCTION_OUT','2026-11-03 08:00:00','2026-11-03 11:00:00',6),(7,4,'POSTED','PRODUCTION_OUT','2026-11-03 10:00:00',NULL,NULL)","2026-11-03 10:00:00");
    }

    @Test @EnabledIfEnvironmentVariable(named="METAL_FLOW_ISOLATED_JDBC_URL",matches="jdbc:mysql://(?:127\\.0\\.0\\.1|localhost):3321/(?:heavy_metal_flow|heavy_metal_factory_20261104)(?:\\?.*)?")
    void workflowWithoutPostingEvidenceDoesNotFallBackToCreation()throws Exception {
        withIssue("INSERT INTO metal_warehouse_flow VALUES(6,'POSTED',NULL);INSERT INTO metal_warehouse_order VALUES(5,4,'POSTED','PRODUCTION_OUT','2026-11-03 08:00:00','2026-11-03 10:00:00',6)",null);
    }

    @Test @EnabledIfEnvironmentVariable(named="METAL_FLOW_ISOLATED_JDBC_URL",matches="jdbc:mysql://(?:127\\.0\\.0\\.1|localhost):3321/(?:heavy_metal_flow|heavy_metal_factory_20261104)(?:\\?.*)?")
    void pendingAndVoidedIssuesCannotKeepLegacyTimes()throws Exception {
        withIssue("INSERT INTO metal_warehouse_order VALUES(5,4,'WAITING_OUT','PRODUCTION_OUT','2026-11-03 08:00:00',NULL,NULL),(7,4,'VOID','PRODUCTION_OUT','2026-11-03 09:00:00',NULL,NULL)",null);
    }
}
