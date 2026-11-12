package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.util.*;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.metal.security.FactoryScope;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StorageBatchSliceServiceTest {
    @Test void multiplePartitionsNeverChooseAnImplicitSource(){
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList(startsWith("SELECT * FROM metal_storage_batch_slice"),eq(1L),eq(2L))).thenReturn(List.of(Map.of("id",3L),Map.of("id",4L)));
        assertThrows(ServiceException.class,()->new StorageBatchSliceService(jdbc,mock(FactoryScope.class)).resolve(1,2,Map.of(),false));
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void explicitPartitionChecksStockVersionAndProductionIdentity(){
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList(startsWith("SELECT * FROM metal_storage_batch_slice"),eq(1L),eq(2L),eq(3L))).thenReturn(List.of(Map.of("id",3L,"stock_id",2L,"batch_id",4L,"version",5)));
        var slices=new StorageBatchSliceService(jdbc,mock(FactoryScope.class));
        assertThrows(ServiceException.class,()->slices.resolve(1,2,Map.of("stockSliceId",3,"sliceVersion",4),true));
        assertThrows(ServiceException.class,()->slices.resolve(1,2,Map.of("stockSliceId",3,"sliceVersion",5,"sourceBatchId",8),true));
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void inheritedIssueUsesThePostedSnapshotInsteadOfRecomputingAChangedStockPool(){
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList(startsWith("SELECT id,CAST(result AS CHAR) result FROM metal_storage_event"),eq(2L))).thenReturn(List.of(Map.of("id",9L,"result","{\"stockId\":1,\"receivedWeight\":\"50.00\",\"sourceCustomerWeight\":\"20.00\",\"sourceFactoryWeight\":\"30.00\"}")));
        var event=Map.<String,Object>of("stock_id",1L,"line_id",2L,"origin_order_id",7L,"identity_snapshot","{\"sourceExecutionId\":8}");
        assertEquals(List.of(new BigDecimal("20.00"),new BigDecimal("30.00")),new ProductionSourceBalances(jdbc).input(event,7));
        verify(jdbc,never()).queryForList(startsWith("SELECT e.id,e.action,CAST(e.result AS CHAR) result,s.weight_delta"),eq(1L));
    }
    @Test void fixedOutputCannotBeRetroactivelyReassignedAfterReturningItsConsumedSource(){
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT order_id FROM metal_production_execution WHERE id=?",Long.class,1L)).thenReturn(7L);
        when(jdbc.queryForObject("SELECT batch_id FROM metal_production_execution WHERE id=?",Long.class,1L)).thenReturn(10L);
        when(jdbc.queryForObject("SELECT processing_type FROM metal_production_order WHERE id=?",String.class,7L)).thenReturn("DIRECT_CUSTOM");
        when(jdbc.queryForList(startsWith("SELECT ev.*,st.origin_order_id"),eq(1L))).thenReturn(List.of(Map.of("customer_weight","40.00","factory_weight","60.00","document_id",2L,"line_id",3L)));
        when(jdbc.queryForList(startsWith("SELECT source_owner_id,customer_id,returned_weight"),eq(2L),eq(3L))).thenReturn(List.of(Map.of("source_owner_id",0L,"customer_id",20L,"returned_weight","50.00")));
        when(jdbc.queryForList(startsWith("SELECT id,weight,customer_weight,factory_weight FROM metal_production_execution_event"),eq(1L))).thenReturn(List.of(Map.of("id",4L,"weight","30.00","customer_weight","12.00","factory_weight","18.00")));
        var error=assertThrows(ServiceException.class,()->new ProductionSourceBalances(jdbc).remaining(1));
        assertTrue(error.getMessage().contains("不能改写已确认产出来源"));
    }
}
