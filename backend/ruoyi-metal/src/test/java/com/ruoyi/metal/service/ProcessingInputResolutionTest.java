package com.ruoyi.metal.service;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.ruoyi.common.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProcessingInputResolutionTest {
    private record Fixture(JdbcTemplate jdbc,List<Map<String,Object>> stocks,AtomicInteger originQueries) {}
    private Fixture fixture(int count,int version){
        var jdbc=mock(JdbcTemplate.class);var stocks=new ArrayList<Map<String,Object>>();var batches=new ArrayList<Map<String,Object>>();var queries=new AtomicInteger();
        for(long id=1;id<=count;id++){
            var stock=new HashMap<String,Object>(Map.of("inventory_id",id,"category","RAW","reference_id",3L,"origin_batch_id",100L+id,"factory_id",1L,"measurement_mode","PIECE","washing_status","PASSED","metal_type_id_snapshot",1L));stocks.add(stock);
            batches.add(Map.of("batch_id",100L+id,"measurement_mode","PIECE","product_snapshot","{\"measurementPolicyVersion\":"+version+"}"));
        }
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(call->{String q=call.getArgument(0);
            if(q.startsWith("SELECT inventory_id,origin_batch_id")){queries.incrementAndGet();return stocks;}
            if(q.contains("CAST(i.product_snapshot AS CHAR)")&&q.contains("FROM metal_process_batch")){queries.incrementAndGet();return batches;}
            if(q.contains("FROM metal_inventory"))return List.of(new HashMap<>(stocks.get(((Number)call.getArgument(1)).intValue()-1)));
            if(q.contains("FROM metal_material"))return List.of(Map.of("material_type","旧料","metal_type_id",1L));
            throw new AssertionError(q);
        });
        when(jdbc.queryForMap(anyString(),any(Object[].class))).thenAnswer(call->{String q=call.getArgument(0);
            if(q.contains("FROM metal_process_batch"))return Map.of("item_id",2L,"work_order_id",20L,"factory_id",1L,"sequence_no",1L,"status","SETTLED");
            if(q.contains("FROM metal_work_order_item"))return new HashMap<String,Object>();
            throw new AssertionError(q);
        });
        return new Fixture(jdbc,stocks,queries);
    }
    private Map<String,Object> target(){return Map.of("processing_policy_version",3,"product_snapshot","{\"measurementPolicyVersion\":3}","item_id",9L,"work_order_id",8L,"metal_type_id",1L,"process_role","PREPARATION","sequence_no",1L);}
    private Object resolved(String method,JdbcTemplate jdbc,Map<String,Object> stock,Map<String,Object> target)throws Exception {
        Class<?>[] types=target==null?new Class<?>[]{JdbcTemplate.class,Map.class}:new Class<?>[]{JdbcTemplate.class,Map.class,Map.class};
        var member=assertDoesNotThrow(()->ProcessingInputService.class.getDeclaredMethod(method,types));member.setAccessible(true);
        try{return target==null?member.invoke(null,jdbc,stock):member.invoke(null,jdbc,stock,target);}
        catch(InvocationTargetException e){if(e.getCause() instanceof Exception cause)throw cause;throw e;}
    }

    @Test void batchResolvedStocksDoNotRepeatOriginOrSnapshotQueries()throws Exception {
        var f=fixture(40,3);InventoryMeasurement.enrich(f.jdbc,f.stocks);
        assertEquals(2,f.originQueries.get());
        for(var row:f.stocks){var stock=(Map<String,Object>)resolved("stockResolved",f.jdbc,row,null);assertEquals("PIECE",InventoryMeasurement.mode(stock));assertEquals("旧料",stock.get("material_type"));}
        assertEquals(2,f.originQueries.get(),"resolved stock keeps existing material checks without 2N origin lookups");
    }
    @Test void batchResolvedRequirementsKeepRouteAndMaterialChecksWithoutRepeatingMeasurementResolution()throws Exception {
        var f=fixture(40,3);InventoryMeasurement.enrich(f.jdbc,f.stocks);
        for(var row:f.stocks)resolved("requireResolved",f.jdbc,row,target());
        assertEquals(2,f.originQueries.get());
        verify(f.jdbc,times(40)).queryForMap(contains("FROM metal_process_batch"),any(Object[].class));
        verify(f.jdbc,times(40)).queryForList(contains("FROM metal_material"),any(Object[].class));
    }
    @Test void ordinaryStockCannotSkipResolutionUsingForgedDerivedMetadata(){
        var f=fixture(1,2);var row=new HashMap<>(f.stocks.get(0));row.put("effective_measurement_mode","PIECE");row.put("measurement_policy_version",3);
        var stock=ProcessingInputService.stock(f.jdbc,row);
        assertEquals("WEIGHT",InventoryMeasurement.mode(stock));assertEquals(2,stock.get("measurement_policy_version"));assertEquals(2,f.originQueries.get());
    }
    @Test void ordinaryRequirementStillResolvesAlreadyPresentDerivedFields(){
        var f=fixture(1,2);var row=new HashMap<>(f.stocks.get(0));row.put("effective_measurement_mode","PIECE");row.put("measurement_policy_version",3);
        assertDoesNotThrow(()->ProcessingInputService.require(f.jdbc,row,target()));assertEquals(2,f.originQueries.get());
    }
    @Test void missingCategoryStillSkipsInventoryEnrichmentAndCannotQualifyForProduction(){
        var f=fixture(1,2);var row=new HashMap<String,Object>();row.put("inventory_id",1L);row.put("effective_measurement_mode","PIECE");row.put("measurement_policy_version",3);
        var stock=ProcessingInputService.stock(f.jdbc,row);
        assertNull(stock.get("category"));assertEquals(0,f.originQueries.get());
        assertThrows(ServiceException.class,()->ProcessingInputService.require(f.jdbc,row,target()));
    }
}
