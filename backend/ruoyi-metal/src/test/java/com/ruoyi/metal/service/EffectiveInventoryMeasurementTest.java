package com.ruoyi.metal.service;

import java.lang.reflect.*;
import java.math.BigDecimal;
import java.util.*;
import com.ruoyi.common.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EffectiveInventoryMeasurementTest {
    private Class<?> policy() {
        return assertDoesNotThrow(()->Class.forName("com.ruoyi.metal.service.InventoryMeasurement"),
                "Inventory measurement must distinguish real v3 PIECE origins from legacy RAW defaults");
    }
    private Object invoke(String name,Class<?>[] types,Object...args) throws Exception {
        try { return policy().getMethod(name,types).invoke(null,args); }
        catch (InvocationTargetException e) {
            if(e.getCause() instanceof Exception error)throw error;
            throw e;
        }
    }
    private String mode(Map<String,Object> row) throws Exception {
        return (String)invoke("mode",new Class<?>[]{Map.class},row);
    }
    private void enrich(JdbcTemplate jdbc,List<Map<String,Object>> rows) throws Exception {
        assertSame(rows,invoke("enrich",new Class<?>[]{JdbcTemplate.class,List.class},jdbc,rows));
    }
    private Map<String,Object> raw(long id) {
        return new HashMap<>(Map.of("inventory_id",id,"category","RAW","measurement_mode","PIECE","net_weight",BigDecimal.TEN,"quantity",5L));
    }
    private Map<String,Object> origin(long id,String mode,int marker) {
        return Map.of("batch_id",id,"measurement_mode",mode,"product_snapshot","{\"measurementPolicyVersion\":"+marker+"}");
    }
    private JdbcTemplate database(List<Map<String,Object>> stocks,List<Map<String,Object>> batches) {
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(call->{
            String sql=call.getArgument(0);
            if(sql.contains("FROM metal_inventory"))return stocks;
            if(sql.contains("FROM metal_process_batch"))return batches;
            throw new AssertionError(sql);
        });
        return jdbc;
    }

    @Test void unprovenRawPieceDefaultsRemainWeightAndDoNotMutateStoredMode() throws Exception {
        var row=raw(1L);
        assertEquals("WEIGHT",mode(row));
        assertEquals(Boolean.TRUE,invoke("weight",new Class<?>[]{Map.class},row));
        enrich(database(List.of(Map.of("inventory_id",1L)),List.of()),List.of(row));
        assertEquals("WEIGHT",row.get("effective_measurement_mode"));
        assertEquals(0,((Number)row.get("measurement_policy_version")).intValue());
        assertEquals("PIECE",row.get("measurement_mode"));
    }

    @ParameterizedTest @CsvSource({"1,PIECE,WEIGHT","2,PIECE,WEIGHT","3,PIECE,PIECE","4,PIECE,PIECE","3,WEIGHT,WEIGHT"})
    void rawEffectiveModeComesFromItsSavedOriginBatchAndProductMarker(int marker,String batchMode,String expected) throws Exception {
        var row=raw(1L);
        var jdbc=database(List.of(Map.of("inventory_id",1L,"origin_batch_id",10L)),List.of(origin(10L,batchMode,marker)));
        enrich(jdbc,List.of(row));
        assertEquals(expected,mode(row));
        assertEquals(marker,((Number)row.get("measurement_policy_version")).intValue());
        assertEquals("PIECE",row.get("measurement_mode"));
    }

    @Test void materialSnapshotCannotInventAProductPieceOrigin() throws Exception {
        var row=raw(1L);
        var batch=new HashMap<String,Object>(origin(10L,"PIECE",2));
        batch.put("material_snapshot","{\"measurementPolicyVersion\":3}");
        enrich(database(List.of(Map.of("inventory_id",1L,"origin_batch_id",10L)),List.of(batch)),List.of(row));
        assertEquals("WEIGHT",mode(row));
    }

    @Test void sourceLinesUseTheirInventoryIdInsteadOfTheirLineIdOrUntrustedOriginProjection() throws Exception {
        var row=raw(1L);row.put("id",999L);row.put("origin_batch_id",88L);
        var jdbc=database(List.of(Map.of("inventory_id",1L,"origin_batch_id",10L)),List.of(origin(10L,"PIECE",3),origin(88L,"WEIGHT",2)));
        enrich(jdbc,List.of(row));
        assertEquals("PIECE",mode(row));
        verify(jdbc).queryForList(contains("FROM metal_inventory"),eq(1L));
    }

    @Test void listIdsAndDirectBatchOriginsAreEnrichedInBatchesAndMissingCategoriesAreSkipped() throws Exception {
        var rows=new ArrayList<Map<String,Object>>();
        var inventory=new ArrayList<Map<String,Object>>();
        for(long id=1;id<=100;id++){
            rows.add(new HashMap<>(Map.of("id",id,"category","RAW","measurement_mode","PIECE")));
            inventory.add(Map.of("inventory_id",id,"origin_batch_id",10L));
        }
        var direct=new HashMap<String,Object>(Map.of("category","RAW","origin_batch_id",11L,"measurement_mode","PIECE"));rows.add(direct);
        var unknown=new HashMap<String,Object>(Map.of("id",999L,"measurement_mode","PIECE"));rows.add(unknown);
        var semi=new HashMap<String,Object>(Map.of("id",1000L,"category","SEMI","measurement_mode","WEIGHT"));rows.add(semi);
        var jdbc=database(inventory,List.of(origin(10L,"PIECE",3),origin(11L,"WEIGHT",3)));
        enrich(jdbc,rows);
        for(int i=0;i<100;i++)assertEquals("PIECE",mode(rows.get(i)));
        assertEquals("WEIGHT",mode(direct));
        assertFalse(unknown.containsKey("effective_measurement_mode"));
        assertEquals("WEIGHT",mode(semi));
        verify(jdbc,times(2)).queryForList(anyString(),any(Object[].class));
    }

    @ParameterizedTest @CsvSource({"1,PIECE,WEIGHT","2,PIECE,WEIGHT","3,PIECE,PIECE","3,WEIGHT,WEIGHT"})
    void rawOutputModeUsesSavedItemSnapshotBeforeStockExists(int marker,String batchMode,String expected) throws Exception {
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(),eq(String.class),any(Object[].class))).thenReturn("{\"measurementPolicyVersion\":"+marker+"}");
        assertEquals(expected,invoke("outputMode",new Class<?>[]{JdbcTemplate.class,Map.class,String.class},jdbc,Map.of("item_id",9L,"measurement_mode",batchMode),"RAW"));
    }

    @Test void outputModeIgnoresCallerDerivedFieldsAndReadsTheRealItemMarker() throws Exception {
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(),eq(String.class),any(Object[].class))).thenReturn("{\"measurementPolicyVersion\":2}");
        var batch=Map.<String,Object>of("item_id",9L,"measurement_mode","PIECE","effective_measurement_mode","PIECE","measurement_policy_version",3,"product_snapshot","{\"measurementPolicyVersion\":3}");
        assertEquals("WEIGHT",invoke("outputMode",new Class<?>[]{JdbcTemplate.class,Map.class,String.class},jdbc,batch,"RAW"));
        verify(jdbc).queryForObject(contains("product_snapshot"),eq(String.class),eq(9L));
    }

    @Test void nonRawModesAndOutputsKeepTheirSavedMeasurementWithoutOriginQueries() throws Exception {
        var jdbc=mock(JdbcTemplate.class);
        assertEquals("PIECE",mode(Map.of("category","SEMI","measurement_mode","PIECE")));
        assertEquals("WEIGHT",mode(Map.of("category","FINISHED","measurement_mode","WEIGHT")));
        assertEquals("PIECE",invoke("outputMode",new Class<?>[]{JdbcTemplate.class,Map.class,String.class},jdbc,Map.of("measurement_mode","PIECE"),"FINISHED"));
        verifyNoInteractions(jdbc);
    }

    @Test void nonRawEnrichmentPublishesEffectiveModeWithoutLookingUpOrigins() throws Exception {
        var jdbc=mock(JdbcTemplate.class);
        var semi=new HashMap<String,Object>(Map.of("id",1L,"category","SEMI","measurement_mode","WEIGHT"));
        var finished=new HashMap<String,Object>(Map.of("id",2L,"category","FINISHED","measurement_mode","PIECE"));
        enrich(jdbc,List.of(semi,finished));
        assertEquals("WEIGHT",semi.get("effective_measurement_mode"));
        assertEquals("PIECE",finished.get("effective_measurement_mode"));
        assertEquals("WEIGHT",semi.get("measurement_mode"));
        assertEquals("PIECE",finished.get("measurement_mode"));
        verifyNoInteractions(jdbc);
    }

    @Test void sourceLineMarkersPreventAnUnrelatedInventoryWithTheSameNumericIdFromGrantingPieces() throws Exception {
        for(String marker:List.of("line_id","source_line_id","source_type")){
            var row=new HashMap<String,Object>(Map.of("id",44L,"category","RAW","measurement_mode","PIECE",marker,"source_type".equals(marker)?"PRODUCTION_OUT":99L));
            var jdbc=database(List.of(Map.of("inventory_id",44L,"origin_batch_id",10L)),List.of(origin(10L,"PIECE",3)));
            enrich(jdbc,List.of(row));
            assertEquals("WEIGHT",mode(row),"A source-line id is not an inventory id: "+marker);
            assertEquals(0,((Number)row.get("measurement_policy_version")).intValue());
            verifyNoInteractions(jdbc);
        }
    }

    @Test void aKnownOriginBatchOnASourceLineIsResolvedDirectlyWithoutItsLineIdBeingUsedAsStock() throws Exception {
        var row=new HashMap<String,Object>(Map.of("id",44L,"line_id",99L,"origin_batch_id",11L,"category","RAW","measurement_mode","PIECE"));
        var jdbc=database(List.of(Map.of("inventory_id",44L,"origin_batch_id",10L)),List.of(origin(10L,"PIECE",3),origin(11L,"WEIGHT",3)));
        enrich(jdbc,List.of(row));
        assertEquals("WEIGHT",mode(row));
        assertEquals(3,((Number)row.get("measurement_policy_version")).intValue());
        verify(jdbc,never()).queryForList(contains("FROM metal_inventory"),any(Object[].class));
        verify(jdbc).queryForList(contains("FROM metal_process_batch"),eq(11L));
    }

    @Test void rawPieceReservationProtectsBothHeldPiecesAndHeldWeight() throws Exception {
        var row=raw(1L);
        var jdbc=database(List.of(Map.of("inventory_id",1L,"origin_batch_id",10L)),List.of(origin(10L,"PIECE",3)));
        doReturn(List.of(Map.of("weight",BigDecimal.ONE,"quantity",4L))).when(jdbc).queryForList(contains("FROM metal_stock_reservation"),any(Object[].class));
        enrich(jdbc,List.of(row));
        assertThrows(ServiceException.class,()->WarehouseReservations.balance(jdbc,row,BigDecimal.ONE.negate(),-2L));
        assertDoesNotThrow(()->WarehouseReservations.balance(jdbc,row,BigDecimal.ONE.negate(),-1L));
        assertThrows(ServiceException.class,()->WarehouseReservations.balance(jdbc,row,BigDecimal.TEN.negate(),0L));
    }
}
