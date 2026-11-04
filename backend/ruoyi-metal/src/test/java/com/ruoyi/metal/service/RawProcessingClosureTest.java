package com.ruoyi.metal.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.KeyHolder;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.metal.security.FactoryScope;

class RawProcessingClosureTest {
    private static final class Fixture {
        final JdbcTemplate jdbc=mock(JdbcTemplate.class);
        final FactoryScope scope=mock(FactoryScope.class);
        final SemiFlowService service=spy(new SemiFlowService(jdbc,scope));
        final Map<String,Object> work=new HashMap<>(Map.of("factory_id",1L,"version",2,"status","IN_PROGRESS","task_type","RAW_PROCESSING","processing_policy_version",2));
        final List<String> writes=new ArrayList<>();
        boolean stagesOpen=true;
        boolean productionOccurred=true;
        boolean openBatch=false;
        Fixture() throws Exception {
            when(scope.isAdmin()).thenReturn(true);
            when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(call->{
                String sql=call.getArgument(0);
                if(sql.startsWith("SELECT * FROM metal_work_order WHERE"))return List.of(work);
                if(sql.startsWith("SELECT item_id FROM metal_work_order_item"))return List.of(Map.of("item_id",10L));
                if(sql.startsWith("SELECT b.completed_quantity")) {
                    boolean raw="RAW_PROCESSING".equals(work.get("task_type"));
                    BigDecimal returned=!raw || sql.contains("'RAW_PROCESS_IN'")?new BigDecimal("95"):BigDecimal.ZERO;
                    return List.of(Map.of("report_id",11L,"voided",false,"report_issued",new BigDecimal("100"),
                            "report_returned",new BigDecimal("95"),"report_loss",new BigDecimal("5"),
                            "report_quantity",raw?0:9,"issued",new BigDecimal("100"),"returned",returned,
                            "output_quantity",raw?0:9,"completed_quantity",raw?0:9));
                }
                if(sql.startsWith("SELECT i.item_id"))return List.of(new HashMap<>(Map.of("item_id",10L,"planned_quantity",0,
                        "completed_quantity",0,"material_snapshot","{\"name\":\"铂块\"}","planned_input_weight",new BigDecimal("120"))));
                if(sql.startsWith("SELECT s.item_id"))return List.of(new HashMap<>(Map.of("item_id",10L,"process_id",20L,"sequence_no",1L,
                        "measurement_mode","WEIGHT","weight_stage_closed",false)));
                return List.of();
            });
            when(jdbc.queryForObject(anyString(),eq(Long.class),any(Object[].class))).thenAnswer(call->{
                String sql=call.getArgument(0);
                if(sql.contains("metal_work_order_step s") && sql.contains("weight_stage_closed"))return stagesOpen?1L:0L;
                if(sql.contains("metal_warehouse_order WHERE")&&sql.contains("PRODUCTION_OUT"))return productionOccurred?1L:0L;
                if(sql.contains("metal_process_batch WHERE")&&sql.contains("status='OPEN'"))return openBatch?1L:0L;
                return 0L;
            });
            when(jdbc.queryForObject(anyString(),eq(BigDecimal.class),any(Object[].class))).thenAnswer(call->{
                String sql=call.getArgument(0);
                if(sql.startsWith("SELECT COALESCE(SUM(l.net_weight-"))return new BigDecimal("100");
                return sql.contains("'RAW_PROCESS_IN'")?new BigDecimal("95"):BigDecimal.ZERO;
            });
            doReturn(Map.of("items",List.of(new HashMap<>(Map.of("item_id",10L,"cancelled_quantity",1))),
                    "progress",List.of(),"stocks",List.of())).when(service).overview(1L);
            doAnswer(call->{
                var connection=mock(Connection.class);var statement=mock(PreparedStatement.class);
                when(connection.prepareStatement(anyString(),anyInt())).thenAnswer(sql->{writes.add(sql.getArgument(0));return statement;});
                call.<PreparedStatementCreator>getArgument(0).createPreparedStatement(connection);
                call.<KeyHolder>getArgument(1).getKeyList().add(new HashMap<>(Map.of("GENERATED_KEY",1L)));
                return 1;
            }).when(jdbc).update(any(PreparedStatementCreator.class),any(KeyHolder.class));
            when(jdbc.update(anyString(),any(Object[].class))).thenAnswer(call->{writes.add(call.getArgument(0));return 1;});
        }
        void close() {
            try(var security=mockStatic(SecurityUtils.class)) {
                security.when(SecurityUtils::getUserId).thenReturn(1L);
                service.close(1L,UUID.randomUUID().toString(),2,"核对原料实物后停止后续处理",true,false);
            }
        }
    }

    @Test void rawPrimaryOutputIsIncludedInHistoryBeforeControlledPartialClose() throws Exception {
        var f=new Fixture();
        doReturn(Map.of("items",List.of(new HashMap<String,Object>()),"progress",List.of(),"stocks",List.of())).when(f.service).overview(1L);
        assertDoesNotThrow(f::close);
        verify(f.jdbc).update(startsWith("UPDATE metal_work_order SET status="),eq("CLOSED_PARTIAL"),eq(1L));
        assertTrue(f.writes.stream().noneMatch(sql->sql.contains("completed_quantity")||sql.contains("cancelled_quantity")));
    }

    @Test void rawPartialCloseStillNeedsBusinessSettledBatchesAndUnfinishedStages() throws Exception {
        var f=new Fixture();f.openBatch=true;assertThrows(ServiceException.class,f::close);
        f.openBatch=false;f.productionOccurred=false;assertThrows(ServiceException.class,f::close);
        f.productionOccurred=true;f.stagesOpen=false;assertThrows(ServiceException.class,f::close);
        assertTrue(f.writes.isEmpty());
    }

    @Test void legacyProductPartialCloseRetainsPieceRules() throws Exception {
        var f=new Fixture();f.work.put("task_type","PRODUCTION");f.work.put("processing_policy_version",1);
        assertDoesNotThrow(f::close);
        doReturn(Map.of("items",List.of(Map.of("cancelled_quantity",0)),"progress",List.of(),"stocks",List.of())).when(f.service).overview(1L);
        assertThrows(ServiceException.class,f::close);
    }

    @Test void rawOverviewUsesWeightsAndNeverInventsCancelledPieces() throws Exception {
        var f=new Fixture();doCallRealMethod().when(f.service).overview(1L);
        var overview=f.service.overview(1L);
        var item=(Map<?,?>)((List<?>)overview.get("items")).get(0);
        assertEquals(new BigDecimal("120"),item.get("planned_input_weight"));
        assertEquals(new BigDecimal("100"),item.get("committed_input_weight"));
        for(String field:List.of("planned_quantity","completed_quantity","cancelled_quantity"))assertNull(item.get(field),field);
        var progress=(Map<?,?>)((List<?>)overview.get("progress")).get(0);
        assertEquals(new BigDecimal("95"),progress.get("output_weight"));
        for(String field:List.of("local_quantity","skipped_quantity","external_quantity","covered_quantity"))assertNull(progress.get(field),field);
    }
}
