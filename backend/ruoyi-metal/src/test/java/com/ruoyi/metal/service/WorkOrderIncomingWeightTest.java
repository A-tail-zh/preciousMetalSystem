package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.metal.catalog.WorkOrderRequest;
import com.ruoyi.metal.security.FactoryScope;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkOrderIncomingWeightTest {
    @Test void optionalNonnegativeWeightsAreRoundedWithoutLosingZero(){
        assertNull(WorkOrderService.incomingWeight(null,"PRODUCTION","MATERIAL"));
        assertEquals(new BigDecimal("0.00"),WorkOrderService.incomingWeight(BigDecimal.ZERO,"PRODUCTION","SHORTAGE"));
        assertEquals(new BigDecimal("12.35"),WorkOrderService.incomingWeight(new BigDecimal("12.345"),"PRODUCTION","DIRECT"));
        for(String bad:List.of("-0.001","100000000000000","99999999999999.999","1.1234567"))
            assertThrows(ServiceException.class,()->WorkOrderService.incomingWeight(new BigDecimal(bad),"PRODUCTION","MATERIAL"));
        assertThrows(ServiceException.class,()->WorkOrderService.incomingWeight(BigDecimal.ZERO,"RAW_PROCESSING","DIRECT"));
    }

    @Test void changesRequireAReasonAndKeepBeforeAndAfterIncludingBlankAndZero(){
        assertNull(WorkOrderService.incomingChange(null,null,null));
        assertNull(WorkOrderService.incomingChange("12.30",new BigDecimal("12.3"),null));
        assertThrows(ServiceException.class,()->WorkOrderService.incomingChange(null,BigDecimal.ZERO," "));
        assertThrows(ServiceException.class,()->WorkOrderService.incomingChange("1",null,"长".repeat(301)));
        assertEquals("客户来料净重：未登记 → 0.00g；原因：补登记",WorkOrderService.incomingChange(null,BigDecimal.ZERO," 补登记 "));
        assertEquals("客户来料净重：100.00g → 未登记；原因：撤销误填",WorkOrderService.incomingChange("100",null,"撤销误填"));
    }

    @Test void oldRequestFingerprintsRemainCompatibleAndNewWeightChangesTheFingerprint(){
        var legacy=new WorkOrderRequest(null,null,"uuid","客户",1L,2L,3L,LocalDate.of(2026,11,4),List.of(),9L,1,"MATERIAL","PRODUCTION");
        assertEquals("WorkOrderRequest[id=null, version=null, requestId=uuid, customer=客户, factoryId=1, productionLineId=2, ownerId=3, orderDate=2026-11-04, items=[], customerId=9, customerVersion=1, processingType=MATERIAL, taskType=PRODUCTION]",legacy.fingerprintInput());
        var registered=new WorkOrderRequest(null,null,"uuid","客户",1L,2L,3L,LocalDate.of(2026,11,4),List.of(),9L,1,"MATERIAL","PRODUCTION",BigDecimal.ZERO,null);
        assertNotEquals(legacy.fingerprintInput(),registered.fingerprintInput());
        assertTrue(registered.fingerprintInput().contains("customerIncomingWeight=0"));
    }

    private Map<String,Object> header(String status){
        var row=new HashMap<String,Object>();
        row.put("id","8");row.put("factoryId","1");row.put("version",3);row.put("status",status);
        row.put("taskType","PRODUCTION");row.put("processingType","MATERIAL");row.put("hasBusinessReferences",true);
        row.put("customerIncomingWeight","100.00");return row;
    }

    @Test void standaloneCorrectionWorksAfterIssueAndCompletionWithoutChangingStockOrStatus(){
        for(String status:List.of("IN_PROGRESS","COMPLETED","CLOSED_PARTIAL")){
            var jdbc=mock(JdbcTemplate.class);var scope=mock(FactoryScope.class);
            when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(header(status)));
            when(jdbc.update(anyString(),any(Object[].class))).thenReturn(1);
            var service=new WorkOrderService(jdbc,null,scope,null);
            try(var security=mockStatic(SecurityUtils.class)){
                security.when(SecurityUtils::getUsername).thenReturn("planner");security.when(SecurityUtils::getUserId).thenReturn(7L);
                var user=new SysUser();user.setNickName("计划员");var login=new LoginUser();login.setUser(user);security.when(SecurityUtils::getLoginUser).thenReturn(login);
                service.changeIncomingWeight(8L,new WorkOrderRequest.IncomingWeightChange(3,new BigDecimal("150.125"),"客户补料"));
            }
            verify(scope,times(2)).check(1L);verify(scope).lockFactories(List.of(1L));
            verify(jdbc).update("UPDATE metal_work_order SET customer_incoming_weight=?,version=version+1,update_by=?,update_time=NOW() WHERE work_order_id=? AND version=? AND del_flag='0'",new BigDecimal("150.13"),"planner",8L,3);
            verify(jdbc).update("INSERT INTO metal_work_order_review(work_order_id,action,from_status,to_status,reason,actor_id,actor_name) VALUES(?,?,?,?,?,?,?)",8L,"INCOMING_CHANGE",status,status,"客户来料净重：100.00g → 150.13g；原因：客户补料",7L,"计划员");
            verify(jdbc,never()).update(contains("metal_inventory"),any(Object[].class));
        }
    }

    @Test void clearingARegistrationIsDifferentFromRecordingZero(){
        var jdbc=mock(JdbcTemplate.class);when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(header("COMPLETED")));
        when(jdbc.update(anyString(),any(Object[].class))).thenReturn(1);
        try(var security=mockStatic(SecurityUtils.class)){
            security.when(SecurityUtils::getUserId).thenReturn(7L);var user=new SysUser();user.setNickName("计划员");var login=new LoginUser();login.setUser(user);security.when(SecurityUtils::getLoginUser).thenReturn(login);
            new WorkOrderService(jdbc,null,mock(FactoryScope.class),null).changeIncomingWeight(8L,new WorkOrderRequest.IncomingWeightChange(3,null,"误填"));
        }
        verify(jdbc).update(startsWith("UPDATE metal_work_order SET customer_incoming_weight="),isNull(),isNull(),eq(8L),eq(3));
    }

    @Test void staleVersionsAndForeignFactoriesCannotWrite(){
        var jdbc=mock(JdbcTemplate.class);var scope=mock(FactoryScope.class);
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(header("COMPLETED")));
        var service=new WorkOrderService(jdbc,null,scope,null);
        assertThrows(ServiceException.class,()->service.changeIncomingWeight(8L,new WorkOrderRequest.IncomingWeightChange(2,BigDecimal.ONE,"更正")));
        verify(jdbc,never()).update(anyString(),any(Object[].class));
        doThrow(new ServiceException("无权访问",403)).when(scope).check(1L);
        assertThrows(ServiceException.class,()->service.changeIncomingWeight(8L,new WorkOrderRequest.IncomingWeightChange(3,BigDecimal.ONE,"更正")));
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
}
