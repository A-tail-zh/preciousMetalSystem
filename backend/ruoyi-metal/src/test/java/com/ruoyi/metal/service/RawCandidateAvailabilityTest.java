package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.util.*;
import com.ruoyi.metal.security.FactoryScope;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RawCandidateAvailabilityTest {
    private List<Map<String,Object>> candidates(int marker,long heldPieces,String heldWeight){
        return candidates(marker,heldPieces,heldWeight,false);
    }
    private List<Map<String,Object>> candidates(int marker,long heldPieces,String heldWeight,boolean singleResolution){
        var jdbc=mock(JdbcTemplate.class);
        var stock=new HashMap<String,Object>();
        stock.putAll(Map.of("inventory_id",1L,"category","RAW","reference_id",50L,"factory_id",7L,"origin_batch_id",100L,"measurement_mode","PIECE","material_ownership","FACTORY","net_weight",new BigDecimal("100"),"quantity",marker>=3?10L:0L));
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(call->{
            String sql=call.getArgument(0);
            if(sql.startsWith("SELECT * FROM metal_work_order"))return List.of(Map.of("factory_id",7L,"status","IN_PROGRESS","processing_type","DIRECT"));
            if(sql.startsWith("SELECT s.*,i.work_order_id"))return List.of(new HashMap<>(Map.of("processing_policy_version",3,"work_order_id",1L,"sequence_no",2L,"process_role","PREPARATION","metal_type_id",1L,"product_snapshot","{\"measurementPolicyVersion\":3,\"metalTypeId\":1}")));
            if(sql.startsWith("SELECT i.*,m.material_type"))return List.of(stock);
            if(sql.startsWith("SELECT inventory_id,origin_batch_id"))return List.of(Map.of("inventory_id",1L,"origin_batch_id",100L));
            if(sql.startsWith("SELECT b.batch_id,b.measurement_mode"))return List.of(Map.of("batch_id",100L,"measurement_mode","PIECE","product_snapshot","{\"measurementPolicyVersion\":"+marker+"}"));
            if(sql.startsWith("SELECT m.material_type"))return List.of(Map.of("material_type","金属料","metal_type_id",1L));
            if(sql.contains("metal_process_raw_return_line"))return List.of();
            if(sql.contains("FROM metal_stock_reservation"))return List.of(Map.of("quantity",heldPieces,"weight",new BigDecimal(heldWeight)));
            if(sql.startsWith("SELECT b.*"))return List.of(Map.of("batch_id",100L,"item_id",10L,"process_id",9L,"sequence_no",1L,"status","SETTLED","work_order_id",1L));
            if(sql.startsWith("SELECT order_no"))return List.of(Map.of("order_no","WO1"));
            if(sql.startsWith("SELECT process_name"))return List.of(Map.of("process_name","前处理"));
            throw new AssertionError(sql);
        });
        when(jdbc.queryForMap(anyString(),any(Object[].class))).thenAnswer(call->{
            String sql=call.getArgument(0);
            if(sql.startsWith("SELECT item_id,work_order_id"))return Map.of("item_id",10L,"work_order_id",1L,"factory_id",7L,"sequence_no",1L,"status","SETTLED");
            if(sql.startsWith("SELECT material_id"))return Map.of();
            throw new AssertionError(sql);
        });
        var rows=new SemiFlowService(jdbc,mock(FactoryScope.class)).candidates(7,1,1,10,11);
        if(singleResolution)verify(jdbc,times(1)).queryForList(startsWith("SELECT inventory_id,origin_batch_id"),any(Object[].class));
        return rows;
    }

    @Test void fullyReservedRawPiecesAreNotOfferedEvenWhenWeightRemains(){assertTrue(candidates(3,10,"50").isEmpty());}
    @Test void fullyReservedRawWeightIsNotOfferedEvenWhenPiecesRemain(){assertTrue(candidates(3,5,"100").isEmpty());}
    @Test void partiallyReservedRawPiecesRetainBothAvailableBalances(){
        var rows=candidates(3,9,"99");assertEquals(1,rows.size());
        assertEquals(1L,rows.get(0).get("available_quantity"));assertEquals(new BigDecimal("1"),rows.get(0).get("available_weight"));
    }
    @Test void historicalRawStillUsesAvailableWeightWithZeroEffectivePieces(){
        var rows=candidates(2,0,"99");assertEquals(1,rows.size());assertEquals("WEIGHT",rows.get(0).get("effective_measurement_mode"));
    }
    @Test void bulkRawOriginResolutionIsReusedDuringCandidateValidation(){assertEquals(1,candidates(3,9,"99",true).size());}
}
