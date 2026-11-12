package com.ruoyi.metal.service;

import java.util.*;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.metal.security.FactoryScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductionDocumentBindingTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final ProductionExecutionService service=new ProductionExecutionService(jdbc,mock(FactoryScope.class));
    private Map<String,Object> execution(String category) {
        var e=new HashMap<String,Object>(Map.of("id",6L,"order_id",7L,"order_item_id",22L,"order_step_id",23L,"processor_id",81L,"status","OPEN","output_category",category));
        e.put("batch_id",88L);
        when(jdbc.queryForList(startsWith("SELECT lifecycle FROM metal_production_batch"),eq(9L),eq(88L))).thenReturn(List.of(Map.of("lifecycle","ACTIVE")));
        when(jdbc.queryForList(startsWith("SELECT e.*"),eq(6L),eq(9L))).thenReturn(List.of(e));
        when(jdbc.queryForList(startsWith("SELECT * FROM metal_production_order"),eq(7L),eq(9L))).thenReturn(List.of(Map.of("flow_version",1,"status","IN_PROGRESS")));
        return e;
    }
    private Map<String,Object> document(String type) {
        return new HashMap<>(Map.of("type",type,"orderId","7","executionId","6","orderItemId","","orderStepId","","processorId",""));
    }

    @ParameterizedTest @ValueSource(strings={"RAW:PROCESS_RAW_IN","SEMI:SEMI_IN","SEMI:PROCESS_RAW_IN","PRODUCT:PRODUCT_IN"})
    void missingLegacyLinksAreDerivedFromFrozenBatch(String value) {
        var pair=value.split(":");execution(pair[0]);var doc=document(pair[1]);
        service.prepareDocument(9L,doc);
        assertEquals("22",doc.get("orderItemId"));assertEquals("23",doc.get("orderStepId"));assertEquals("81",doc.get("processorId"));
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }

    @Test void rawAlternativeIsLimitedToSemiOutputAndDoesNotAllowOtherReceiptTypes() {
        for(String category:List.of("RAW","SEMI","PRODUCT"))for(String type:List.of("PROCESS_RAW_IN","SEMI_IN","PRODUCT_IN")){
            boolean expected=category.equals("SEMI")?!type.equals("PRODUCT_IN"):category.equals("RAW")?type.equals("PROCESS_RAW_IN"):type.equals("PRODUCT_IN");
            assertEquals(expected,ProductionExecutionService.receiptTypeAllowed(category,type),category+":"+type);
        }
    }

    @Test void missingOrderIdCanUseLegacyProductionOrderId() {
        execution("RAW");var doc=document("PROCESS_RAW_IN");doc.put("orderId","");doc.put("productionOrderId","7");
        service.prepareDocument(9L,doc);assertEquals("7",doc.get("orderId"));
    }

    @Test void missingOrderIdIsDerivedFromTheScopedBatch() {
        execution("RAW");var doc=document("PROCESS_RAW_IN");doc.remove("orderId");
        service.prepareDocument(9L,doc);assertEquals("7",doc.get("orderId"));assertEquals("7",doc.get("productionOrderId"));
    }

    @Test void wrongReceiptTypeNamesTheExpectedType() {
        execution("RAW");var error=assertThrows(ServiceException.class,()->service.prepareDocument(9L,document("SEMI_IN")));
        assertEquals(409,error.getCode());assertTrue(error.getMessage().contains("加工原材料入库"));
    }

    @ParameterizedTest @ValueSource(strings={"processorId:加工人员编号","orderItemId:产品组编号","orderStepId:工序编号","executionId:加工批次编号","orderId:生产工单编号"})
    void invalidIdentifiersNameTheField(String value) {
        execution("RAW");var pair=value.split(":");var doc=document("PROCESS_RAW_IN");doc.put(pair[0],"abc");
        var error=assertThrows(ServiceException.class,()->service.prepareDocument(9L,doc));
        assertEquals(400,error.getCode());assertTrue(error.getMessage().contains(pair[1]));
    }

    @Test void foreignOrderAndForgedProcessorAreRejected() {
        execution("RAW");var doc=document("PROCESS_RAW_IN");doc.put("orderId","8");
        assertEquals(409,assertThrows(ServiceException.class,()->service.prepareDocument(9L,doc)).getCode());
        doc.put("orderId","7");doc.put("processorId","82");
        assertTrue(assertThrows(ServiceException.class,()->service.prepareDocument(9L,doc)).getMessage().contains("加工人员编号"));
    }

    @Test void closedBatchIsRejected() {
        execution("RAW").put("status","CLOSED");
        assertTrue(assertThrows(ServiceException.class,()->service.prepareDocument(9L,document("PROCESS_RAW_IN"))).getMessage().contains("已结清"));
    }

    @Test void nonproductionReceiptDoesNotReadBatchData() {
        service.prepareDocument(9L,document("RAW_IN"));verifyNoInteractions(jdbc);
    }

    @Test void weightReceiptAcceptsEmptyQuantityButRejectsNonzeroQuantity() {
        var e=Map.<String,Object>of("measurement_mode","WEIGHT");
        assertEquals(0,ProductionExecutionService.productionQuantity("PROCESS_RAW_IN",e,Map.of("quantity","")));
        assertTrue(assertThrows(ServiceException.class,()->ProductionExecutionService.productionQuantity("PROCESS_RAW_IN",e,Map.of("quantity","2"))).getMessage().contains("计重工序"));
        assertEquals(2,ProductionExecutionService.productionQuantity("PRODUCTION_OUT",e,Map.of("quantity","2")));
    }

    @Test void fractionalPieceAndReferenceCountsHaveSpecificMessages() {
        var piece=assertThrows(ServiceException.class,()->ProductionExecutionService.productionQuantity("SEMI_IN",Map.of("measurement_mode","PIECE"),Map.of("quantity","1.5")));
        assertEquals("件数须为整数",piece.getMessage());
        var reference=assertThrows(ServiceException.class,()->ProductionExecutionService.productionQuantity("PRODUCT_IN",Map.of("measurement_mode","WEIGHT"),Map.of("referenceQuantity","1.5")));
        assertEquals("参考完工件数须为整数",reference.getMessage());
    }
}
