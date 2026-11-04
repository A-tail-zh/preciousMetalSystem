package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import com.ruoyi.metal.catalog.*;
import com.ruoyi.metal.security.FactoryScope;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class WorkOrderConfigurableSnapshotTest {
    @Test void newlyCapturedProductUsesConfigurableMarkerWithoutChangingLossOrMetalFields() throws Exception {
        var p=new ProductRecord();p.setCode("P20");p.setName("产品");p.setCategory("铱杆实心");p.setDimensions(Map.of("diameter","1"));p.setMetalTypeId(9L);
        var method=WorkOrderService.class.getDeclaredMethod("snapshot",ProductRecord.class);method.setAccessible(true);
        var snapshot=(Map<?,?>)method.invoke(new WorkOrderService(null,null,null,null),p);
        assertEquals(3,snapshot.get("measurementPolicyVersion"));
        assertEquals("9",snapshot.get("metalTypeId"));assertEquals("P20",snapshot.get("code"));
    }

    @Test void retainedOrderKeepsEverySavedMarkerUnitAndFullPrecisionEvenWhenCurrentCatalogChanges() throws Exception {
        for(Integer marker:Arrays.asList(null,1,2,3)){
            var jdbc=mock(JdbcTemplate.class);var products=mock(ProductService.class);
            when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(call->{
                String sql=call.getArgument(0);
                if(sql.startsWith("SELECT factory_id,production_line_name"))return List.of(Map.of("factory_id",1L,"name","产线"));
                if(sql.startsWith("SELECT product_id"))return List.of(Map.of("product_id",20L));
                if(sql.startsWith("SELECT process_id"))return List.of(Map.of("process_id",30L));
                if(sql.startsWith("SELECT metal_type_id"))return List.of(Map.of("metal_type_id",9L));
                if(sql.startsWith("SELECT COALESCE(NULLIF(nick_name"))return List.of(Map.of("name","负责人"));
                throw new AssertionError(sql);
            });
            var snapshot=new LinkedHashMap<String,Object>(Map.of("name","历史产品","metalTypeId","9","lossRateBasis","NET_INPUT"));
            if(marker!=null)snapshot.put("measurementPolicyVersion",marker);
            var step=new LinkedHashMap<String,Object>(Map.of("processId","30","processName","历史融定","measurementMode","WEIGHT","lossUnit","G_PER_PIECE","loss","9.123456","processRole","SMELTING","outputStage","FINISHED"));
            var previous=Map.<String,Object>of("id","10","productId","20","snapshot",snapshot,"plans",List.of(step));
            var start=LocalDateTime.of(2026,11,4,9,0);
            var item=new WorkOrderRequest.Item(10L,20L,5,List.of(new WorkOrderRequest.Plan(30L,start,start.plusHours(1),null)));
            var request=new WorkOrderRequest(1L,0,null,null,1L,2L,3L,start.toLocalDate(),List.of(item),null,null,null,"PRODUCTION");
            var service=new WorkOrderService(jdbc,null,mock(FactoryScope.class),products);
            var prepare=WorkOrderService.class.getDeclaredMethod("prepare",WorkOrderRequest.class,List.class,int.class,String.class);prepare.setAccessible(true);
            var prepared=((List<?>)prepare.invoke(service,request,List.of(previous),3,"PRODUCTION")).get(0);
            var savedSnapshot=prepared.getClass().getDeclaredMethod("snapshot");savedSnapshot.setAccessible(true);
            var savedSteps=prepared.getClass().getDeclaredMethod("steps");savedSteps.setAccessible(true);
            assertEquals(snapshot,savedSnapshot.invoke(prepared));assertEquals(List.of(step),savedSteps.invoke(prepared));
            assertEquals(marker,((Map<?,?>)savedSnapshot.invoke(prepared)).get("measurementPolicyVersion"));
            assertEquals("9.123456",((Map<?,?>)((List<?>)savedSteps.invoke(prepared)).get(0)).get("loss"));
            verifyNoInteractions(products);
            verify(jdbc,never()).queryForList(contains("JOIN metal_process_type"),any(Object[].class));
        }
    }
}
