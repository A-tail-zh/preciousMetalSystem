package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.util.*;
import com.ruoyi.metal.security.FactoryScope;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ConfigurableProgressOverviewTest {
    @SuppressWarnings("unchecked")
    private List<Map<String,Object>> progress(int marker){
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(call->{
            String sql=call.getArgument(0);
            if(sql.startsWith("SELECT * FROM metal_work_order"))return List.of(Map.of("factory_id",7L,"processing_policy_version",3,"task_type","PRODUCTION"));
            if(sql.startsWith("SELECT i.item_id,i.quantity"))return List.of(new HashMap<>(Map.of("item_id",10L,"planned_quantity",10L,"completed_quantity",10L,"product_snapshot","{\"measurementPolicyVersion\":"+marker+"}")));
            if(sql.startsWith("SELECT s.item_id,s.process_id")){
                var steps=new ArrayList<Map<String,Object>>();
                for(int i=1;i<=3;i++)steps.add(new HashMap<>(Map.of("item_id",10L,"process_id",100L+i,"sequence_no",i,"measurement_mode",i==2?"WEIGHT":"PIECE","weight_stage_closed",true)));
                return steps;
            }
            return List.of();
        });
        when(jdbc.queryForObject(anyString(),eq(Long.class),any(Object[].class))).thenAnswer(call->{
            String sql=call.getArgument(0);
            if(sql.startsWith("SELECT COALESCE(SUM(completed_quantity)")){
                long process=((Number)call.getArgument(3)).longValue();
                return process==101?3L:process==103?10L:0L;
            }
            return 0L;
        });
        when(jdbc.queryForObject(anyString(),eq(BigDecimal.class),any(Object[].class))).thenReturn(new BigDecimal("70"));
        return (List<Map<String,Object>>)new SemiFlowService(jdbc,mock(FactoryScope.class)).overview(1L).get("progress");
    }

    @Test void weightConversionShowsMassWithoutFabricatingEffectivePieceProgress(){
        var rows=progress(3);
        assertEquals(3L,rows.get(0).get("local_quantity"));assertEquals(3L,rows.get(0).get("covered_quantity"));
        assertEquals(10L,rows.get(2).get("local_quantity"));
        for(String key:List.of("local_quantity","external_quantity","skipped_quantity","covered_quantity"))assertNull(rows.get(1).get(key),key);
        assertEquals(new BigDecimal("70"),rows.get(1).get("output_weight"));
    }

    @Test void versionTwoRetainsItsSavedProgressPresentation(){
        var rows=progress(2);
        assertNull(rows.get(1).get("local_quantity"));assertEquals(0L,rows.get(1).get("covered_quantity"));
        assertEquals(3L,rows.get(0).get("local_quantity"));
    }
}
