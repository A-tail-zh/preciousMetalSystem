package com.ruoyi.metal.service;

import java.lang.reflect.*;
import java.util.*;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.metal.security.FactoryScope;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConfigurableWarehouseTemplateTest {
    @SuppressWarnings("unchecked")
    private List<WarehouseTemplateLayout.Column> columns(String mode,int marker,boolean rawTask,long factory)throws Exception {
        var jdbc=mock(JdbcTemplate.class);
        var batch=new HashMap<String,Object>(Map.of("batch_id",20L,"item_id",9L,"factory_id",factory,"measurement_mode",mode,"product_snapshot","{\"measurementPolicyVersion\":"+marker+"}"));batch.put("product_id",rawTask?null:4L);
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(batch));
        when(jdbc.queryForObject(anyString(),eq(String.class),any(Object[].class))).thenReturn(rawTask?"{}":batch.get("product_snapshot").toString());
        var service=new WarehouseImportService(jdbc,mock(FactoryScope.class));
        var method=assertDoesNotThrow(()->WarehouseImportService.class.getMethod("templateColumns",String.class,Long.class,Long.class));
        try{return (List<WarehouseTemplateLayout.Column>)method.invoke(service,"RAW_PROCESS_IN",1L,20L);}
        catch(InvocationTargetException e){if(e.getCause() instanceof Exception failure)throw failure;throw e;}
    }
    @Test void newRawPieceTemplateContainsQuantityAndParsesItsOwnHeaders()throws Exception {
        var columns=columns("PIECE",3,false,1L);
        assertTrue(columns.stream().anyMatch(c->c.key().equals("quantity")));assertFalse(columns.stream().anyMatch(c->c.key().equals("referenceQuantity")));
        assertTrue(WarehouseTemplateLayout.identify(columns.stream().map(WarehouseTemplateLayout.Column::label).toList(),"RAW_PROCESS_IN").containsKey("quantity"));
    }
    @Test void newProductRawWeightTemplateContainsOnlyReferencePiecesAndParsesItsOwnHeaders()throws Exception {
        var columns=columns("WEIGHT",3,false,1L);
        assertFalse(columns.stream().anyMatch(c->c.key().equals("quantity")));assertTrue(columns.stream().anyMatch(c->c.key().equals("referenceQuantity")));
        assertTrue(WarehouseTemplateLayout.identify(columns.stream().map(WarehouseTemplateLayout.Column::label).toList(),"RAW_PROCESS_IN").containsKey("referenceQuantity"));
    }
    @Test void rawTaskAndLegacyRawTemplatesKeepTheirOriginalColumns()throws Exception {
        assertEquals(WarehouseTemplateLayout.inbound("RAW_PROCESS_IN",true),columns("WEIGHT",3,true,1L));
        assertEquals(WarehouseTemplateLayout.inbound("RAW_PROCESS_IN",true),columns("PIECE",2,false,1L));
        assertFalse(WarehouseTemplateLayout.raw("RAW_IN").stream().anyMatch(c->Set.of("quantity","referenceQuantity").contains(c.key())));
    }
    @Test void templateContextMustBelongToTheRequestedFactory() {
        assertEquals(403,assertThrows(ServiceException.class,()->columns("PIECE",3,false,2L)).getCode());
    }
}
