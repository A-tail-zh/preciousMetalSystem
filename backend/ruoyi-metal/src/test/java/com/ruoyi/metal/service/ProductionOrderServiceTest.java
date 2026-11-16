package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ruoyi.metal.security.FactoryScope;
import com.ruoyi.common.exception.ServiceException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductionOrderServiceTest {
    @Test void successfulEditReplayPrecedesChecksAgainstLaterOrderChanges() throws Exception {
        var jdbc=mock(JdbcTemplate.class);var scope=mock(FactoryScope.class);
        when(scope.isAdmin()).thenReturn(true);when(scope.resolveFactory(8L)).thenReturn(8L);
        var input=new ProductionOrderService.Create(8L,UUID.randomUUID().toString(),3L,"DIRECT_CUSTOM",null,1L,"2026-11-10",2L,List.of(new ProductionOrderService.Item(4L,1,List.of())),List.of());
        String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(com.alibaba.fastjson2.JSON.toJSONBytes(List.of(7L,0,input))));
        when(jdbc.queryForList(startsWith("SELECT action,request_hash"),any(Object[].class))).thenReturn(List.of(Map.of("action","edit","request_hash",hash,"result","{\"id\":\"7\",\"version\":1}")));
        when(jdbc.queryForList(startsWith("SELECT customer_id,CAST(arrivals_snapshot"),any(Object[].class))).thenThrow(new ServiceException("工单已被后续操作改变",409));
        try(var security=mockStatic(com.ruoyi.common.utils.SecurityUtils.class)){
            security.when(com.ruoyi.common.utils.SecurityUtils::getUserId).thenReturn(1L);
            var result=new ProductionOrderService(jdbc,scope).edit(7L,new ProductionOrderService.Edit(0,input));
            assertEquals("7",result.get("id"));assertEquals(true,result.get("replayed"));
            assertTrue(mockingDetails(jdbc).getInvocations().stream().allMatch(i->i.getMethod().getName().equals("queryForList")&&i.getArgument(0).toString().startsWith("SELECT action,request_hash")));
        }
    }
    @Test void nonArrivalModesRequireCustomerAndRejectInitialArrivalBeforeWrites(){
        var jdbc=mock(JdbcTemplate.class);var scope=mock(FactoryScope.class);
        when(scope.isAdmin()).thenReturn(true);when(scope.resolveFactory(8L)).thenReturn(8L);
        var service=new ProductionOrderService(jdbc,scope);
        for(String type:List.of("DIRECT_CUSTOM","SHORTAGE_CUSTOM")){
            var missing=new ProductionOrderService.Create(8L,UUID.randomUUID().toString(),null,type,null,1L,"2026-11-10",2L,List.of());
            assertEquals("生产工单必须选择客户",assertThrows(ServiceException.class,()->service.create(missing)).getMessage());
            var arrival=new ProductionOrderService.Create(8L,UUID.randomUUID().toString(),3L,type,null,1L,"2026-11-10",2L,List.of(),List.of(Map.of("netWeight","10")));
            assertEquals("直接定制和欠料定制不填写初始来料明细",assertThrows(ServiceException.class,()->service.create(arrival)).getMessage());
            assertThrows(ServiceException.class,()->ProductionOrderService.weight("10",type));
        }
        verifyNoInteractions(jdbc);
    }
    @Test void arrivalPurityIsOptionalWithAccurateValidation(){
        var arrival=new HashMap<String,Object>(Map.of("materialId","1","warehouseId","2","reviewerId","3","netWeight","10.00","form","METAL"));
        assertNull(ProductionOrderService.normalizeArrivals(List.of(arrival)).get(0).get("purity"));
        for(Object value:Arrays.asList(null,""," ","0","99.99","100.00")){
            arrival.put("purity",value);assertEquals(StorageLedgerService.optionalPurity(value),ProductionOrderService.normalizeArrivals(List.of(arrival)).get(0).get("purity"));
        }
        for(String value:List.of("-1","100.01","1.001","invalid")){
            arrival.put("purity",value);assertEquals("纯度须为0至100，最多两位小数",assertThrows(ServiceException.class,()->ProductionOrderService.normalizeArrivals(List.of(arrival))).getMessage());
        }
    }
    @Test void decimalWeightPreservesPrecisionAndNullVersusZero(){
        assertNull(ProductionOrderService.weight("", "MATERIAL_CUSTOM"));
        assertEquals(new BigDecimal("0.00"),ProductionOrderService.weight("0","MATERIAL_CUSTOM"));
        assertEquals(new BigDecimal("99999999999999.12"),ProductionOrderService.weight("99999999999999.12","MATERIAL_CUSTOM"));
        for(String invalid:List.of("-1","1e3","1.123","1.230","100000000000000","NaN"))assertThrows(ServiceException.class,()->ProductionOrderService.weight(invalid,"MATERIAL_CUSTOM"));
        assertThrows(ServiceException.class,()->ProductionOrderService.weight("0","DIRECT_CUSTOM"));
        assertThrows(ServiceException.class,()->ProductionOrderService.weight(null,null));
    }
    @Test void datesAreStrictAndTimesOptional(){
        assertNull(ProductionOrderService.time(null));
        assertNull(ProductionOrderService.time(""));
        assertEquals(2026,ProductionOrderService.time("2026-11-09 12:13:14").getYear());
        for(String invalid:List.of("2026-02-30 00:00:00","2026-11-09T12:00:00","0999-01-01 00:00:00"))assertThrows(ServiceException.class,()->ProductionOrderService.time(invalid));
        assertThrows(ServiceException.class,()->ProductionOrderService.date("2026-02-30"));
    }
    @Test void factoryIsResolvedBeforeAnyListOrDetailRead(){
        JdbcTemplate jdbc=mock(JdbcTemplate.class);FactoryScope scope=mock(FactoryScope.class);when(scope.isAdmin()).thenReturn(true);
        when(scope.resolveFactory(8L)).thenThrow(new ServiceException("跨厂拒绝",403));var service=new ProductionOrderService(jdbc,scope);
        assertThrows(ServiceException.class,()->service.list(8L,1,16));assertThrows(ServiceException.class,()->service.detail(123L,8L));
        verifyNoInteractions(jdbc);
    }
    @Test void legacyWorkOrderReferencesStillProtectCatalogKinds(){
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList(contains("metal_work_order"),any(Object[].class))).thenReturn(List.of(Map.of("id",1L)));
        assertThrows(ServiceException.class,()->WorkOrderReferences.factory(jdbc,7L));
        assertThrows(ServiceException.class,()->WorkOrderReferences.product(jdbc,7L));
        assertThrows(ServiceException.class,()->WorkOrderReferences.process(jdbc,7L));
        assertThrows(ServiceException.class,()->WorkOrderReferences.lineMove(jdbc,7L));
        assertThrows(ServiceException.class,()->WorkOrderReferences.person(jdbc,7L));
    }
    @Test void mutationsResolveFactoryBeforeReadingOrWriting(){
        JdbcTemplate jdbc=mock(JdbcTemplate.class);FactoryScope scope=mock(FactoryScope.class);when(scope.isAdmin()).thenReturn(true);
        when(scope.resolveFactory(8L)).thenThrow(new ServiceException("跨厂拒绝",403));var service=new ProductionOrderService(jdbc,scope);
        var input=new ProductionOrderService.Create(8L,UUID.randomUUID().toString(),null,"DIRECT_CUSTOM",null,1L,"2026-11-09",2L,List.of());
        assertThrows(ServiceException.class,()->service.editContext(1L,8L));
        assertThrows(ServiceException.class,()->service.edit(1L,new ProductionOrderService.Edit(0,input)));
        assertThrows(ServiceException.class,()->service.delete(new ProductionOrderService.Delete(8L,UUID.randomUUID().toString(),List.of(new ProductionOrderService.Target(1L,0)))));
        assertThrows(ServiceException.class,()->service.operation(UUID.randomUUID().toString(),8L,"remove"));
        verifyNoInteractions(jdbc);
    }
}
