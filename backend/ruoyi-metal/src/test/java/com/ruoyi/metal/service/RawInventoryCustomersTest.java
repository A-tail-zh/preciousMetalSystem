package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ruoyi.metal.catalog.WarehouseRequests.Line;
import com.ruoyi.common.exception.ServiceException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RawInventoryCustomersTest {
    private Line line(Long id,Integer version){return new Line(null,null,"RAW",1L,null,BigDecimal.ONE,0L,null,null,null,"伪造公司",null,null,null,null,null,null,null,id,version);}
    @Test void validatesFactoryVersionAndDeletionWithoutTrustingClientName(){
        var jdbc=mock(JdbcTemplate.class);var service=new RawInventoryCustomers(jdbc);
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(Map.of("customer_id",2L,"factory_id",1L,"company_name","真实公司","version",3)));
        assertEquals("真实公司",service.selected(1L,line(2L,3),false).get("company_name"));
        assertThrows(ServiceException.class,()->service.selected(1L,line(null,null),false));
        assertThrows(ServiceException.class,()->service.selected(1L,line(2L,2),false));
        assertEquals(403,assertThrows(ServiceException.class,()->service.selected(9L,line(2L,3),false)).getCode());
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of());
        assertThrows(ServiceException.class,()->service.selected(1L,line(2L,3),true));
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void inheritanceCannotChangeCustomer(){
        assertDoesNotThrow(()->RawInventoryCustomers.inherited(line(null,null),2L));
        assertDoesNotThrow(()->RawInventoryCustomers.inherited(line(2L,null),2L));
        assertThrows(ServiceException.class,()->RawInventoryCustomers.inherited(line(3L,null),2L));
    }
    @Test void precisionAndLegacyFingerprintsKeepCustomerSemantics(){
        var legacy=new Line(null,null,"RAW",1L,null,BigDecimal.ONE,0L,null,null,null,"伪造公司",null,null);
        assertEquals("Line[inventoryId=null, sourceLineId=null, category=RAW, referenceId=1, batchNo=null, netWeight=1, quantity=0, grossWeight=null, impurity=null, purity=null, organization=伪造公司, purpose=null, remark=null]",legacy.toString());
        assertNotEquals(legacy.toString(),line(2L,3).toString());
        assertEquals(2L,WarehousePrecision.line(line(2L,3)).customerId());
        assertEquals(3,WarehousePrecision.line(line(2L,3)).customerVersion());
    }
    @Test void rawTemplatesContainOnlyWeightAndRequireCustomerId(){
        for(String type:List.of("RAW_IN","RAW_RETURN","EXCHANGE_OUT","PROCESS_RAW_RETURN")){
            var columns=WarehouseTemplateLayout.raw(type);
            assertTrue(columns.stream().noneMatch(c->c.label().contains("数量")||c.label().contains("件")));
        }
        var raw=WarehouseTemplateLayout.raw("RAW_IN");
        assertTrue(raw.stream().anyMatch(c->c.key().equals("customerId")));
        assertDoesNotThrow(()->WarehouseTemplateLayout.identify(raw.stream().map(WarehouseTemplateLayout.Column::label).toList(),"RAW_IN"));
        assertThrows(ServiceException.class,()->WarehouseTemplateLayout.identify(WarehouseTemplateLayout.legacy().stream().map(WarehouseTemplateLayout.Column::label).toList(),"RAW_IN"));
    }
}
