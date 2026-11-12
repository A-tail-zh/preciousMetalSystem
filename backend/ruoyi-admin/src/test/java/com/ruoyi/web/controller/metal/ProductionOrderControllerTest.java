package com.ruoyi.web.controller.metal;

import java.util.*;
import org.junit.jupiter.api.Test;
import com.ruoyi.metal.service.ProductionOrderService;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductionOrderControllerTest {
    @Test void listReturnsTheProjectSuccessEnvelope(){
        var service=mock(ProductionOrderService.class);
        when(service.list(7L,1,16)).thenReturn(new ProductionOrderService.Page(List.of(Map.of("id","123")),1));
        var result=new ProductionOrderController(service).list(7L,1,16);
        assertEquals(200,result.getCode());assertEquals(1,result.getTotal());assertEquals("123",((Map<?,?>)result.getRows().get(0)).get("id"));
    }
}
