package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ConfigurablePieceInputTest {
    private Map<String,Object> batch(int marker){return Map.of("batch_id",20L,"item_id",10L,"product_snapshot","{\"measurementPolicyVersion\":"+marker+"}");}
    private Map<String,Object> line(String type,String mode,long quantity,String weight){
        return new HashMap<>(Map.of("inventory_id",1L,"category","SEMI","type",type,"measurement_mode",mode,"quantity",quantity,"reference_quantity",999L,"net_weight",new BigDecimal(weight)));
    }
    private JdbcTemplate database(List<Map<String,Object>> lines){
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(call->{
            String sql=call.getArgument(0);
            if(sql.contains("metal_warehouse_order_line"))return lines;
            if(sql.startsWith("SELECT inventory_id,origin_batch_id"))return List.of(Map.of("inventory_id",1L,"origin_batch_id",101L),Map.of("inventory_id",2L,"origin_batch_id",102L),Map.of("inventory_id",3L,"origin_batch_id",103L));
            if(sql.startsWith("SELECT b.batch_id,b.measurement_mode"))return List.of(Map.of("batch_id",101L,"measurement_mode","PIECE","product_snapshot","{\"measurementPolicyVersion\":3}"),Map.of("batch_id",102L,"measurement_mode","PIECE","product_snapshot","{\"measurementPolicyVersion\":2}"),Map.of("batch_id",103L,"measurement_mode","WEIGHT","product_snapshot","{\"measurementPolicyVersion\":3}"));
            throw new AssertionError(sql);
        });
        return jdbc;
    }
    private long netIssued(JdbcTemplate jdbc,Map<String,Object> batch){
        return SemiFlowService.netIssued(jdbc,batch);
    }
    private boolean weightInput(JdbcTemplate jdbc,Map<String,Object> batch){
        return WeightStageProgressService.hasWeightInput(jdbc,batch);
    }

    @Test void continuousPieceInputDeductsUnusedReturnsAndNeverCountsReferencePieces()throws Exception{
        var jdbc=database(List.of(line("PRODUCTION_OUT","PIECE",8,"80"),line("SEMI_RETURN","PIECE",3,"30"),line("PRODUCTION_OUT","WEIGHT",0,"20")));
        assertEquals(5,netIssued(jdbc,batch(3)));
    }

    @Test void returnedWeightInputNoLongerBreaksTheActivePieceSegment()throws Exception{
        var jdbc=database(List.of(line("PRODUCTION_OUT","WEIGHT",0,"20"),line("SEMI_RETURN","WEIGHT",0,"20"),line("PRODUCTION_OUT","PIECE",8,"80")));
        assertFalse(weightInput(jdbc,batch(3)));
        var actual=database(List.of(line("PRODUCTION_OUT","WEIGHT",0,"20"),line("SEMI_RETURN","WEIGHT",0,"19.999999")));
        assertTrue(weightInput(actual,batch(3)));
    }

    @Test void oldMarkerUsesTheExistingPieceAndWeightQueries()throws Exception{
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(),eq(Long.class),eq(20L))).thenReturn(7L);
        when(jdbc.queryForObject(anyString(),eq(BigDecimal.class),eq(20L))).thenReturn(BigDecimal.ONE);
        assertEquals(7,netIssued(jdbc,batch(2)));assertTrue(weightInput(jdbc,batch(2)));
        verify(jdbc,never()).queryForList(anyString(),any(Object[].class));
    }

    @Test void rawEffectivePiecesFollowTheSavedOriginAndUnusedRawReturns()throws Exception{
        var issue=line("PRODUCTION_OUT","PIECE",8,"80");issue.put("category","RAW");
        var returned=line("RAW_RETURN","PIECE",2,"20");returned.put("category","RAW");
        var jdbc=database(List.of(issue,returned));
        assertEquals(6,netIssued(jdbc,batch(3)));assertFalse(weightInput(jdbc,batch(3)));
    }

    @Test void historicalRawAndWeightReferenceCountsNeverCreateEffectivePieces()throws Exception{
        var old=line("PRODUCTION_OUT","PIECE",99,"10");old.put("inventory_id",2L);old.put("category","RAW");
        var weight=line("PRODUCTION_OUT","WEIGHT",0,"20");weight.put("inventory_id",3L);weight.put("category","RAW");
        var forged=line("PRODUCTION_OUT","PIECE",999,"30");forged.put("inventory_id",4L);forged.put("category","RAW");forged.put("effective_measurement_mode","PIECE");forged.put("measurement_policy_version",3);
        var jdbc=database(List.of(old,weight,forged));
        assertEquals(0,netIssued(jdbc,batch(3)));assertTrue(weightInput(jdbc,batch(3)));
    }
}
