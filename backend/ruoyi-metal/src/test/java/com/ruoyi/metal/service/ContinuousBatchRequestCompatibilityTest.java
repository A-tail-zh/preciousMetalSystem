package com.ruoyi.metal.service;

import java.util.*;
import java.time.LocalDate;
import com.ruoyi.metal.catalog.OutboundSubmissionRequests.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContinuousBatchRequestCompatibilityTest {
    private record LegacyHandover(String requestId,Integer version,LocalDate orderDate,Boolean physicalChecked,
                                  String residualMode,String reason,List<com.ruoyi.metal.catalog.WarehouseFlowRequests.HandoverDocument> documents) {}
    @Test void addingWipDoesNotChangeSuccessfulHistoricalHandoverFingerprints(){
        var date=LocalDate.of(2026,11,4);
        var old=new LegacyHandover("request",3,date,true,"NONE",null,List.of());
        var compatible=new com.ruoyi.metal.catalog.WarehouseFlowRequests.Handover("request",3,date,true,"NONE",null,List.of());
        assertEquals(WarehouseWorkflowService.json(List.of(7L,old)),WarehouseWorkflowService.json(List.of(7L,compatible)));
    }
    private Request old(){return new Request("request",1L,"PRODUCTION_OUT",LocalDate.of(2026,11,4),List.of(),2L,3L,null,4L,5L,6L,null,null,List.of(new Group(7L,null,List.of())));}
    @Test void oldRequestFingerprintRetainsTheOriginalRecordShape(){
        assertEquals("Request[requestId=request, factoryId=1, type=PRODUCTION_OUT, orderDate=2026-11-04, operatorIds=[], recipientId=2, keeperId=3, reviewerId=null, workOrderId=4, itemId=5, processId=6, batchId=null, batchVersion=null, groups=[Group[warehouseId=7, peopleOverride=null, lines=[]]]]",old().toString());
    }
    @Test void oldFlowJsonDoesNotGainNullPlanningFields(){
        String json=WarehouseWorkflowService.json(old());assertFalse(json.contains("batchAction"));assertFalse(json.contains("plannedQuantity"));
    }
    @Test void previewRetainsNewIntentAndThisPartQuantity(){
        var r=new Preview(1L,4L,5L,6L,null,null,old().groups(),"NEW",60).request();
        assertEquals("NEW",r.batchAction());assertEquals(60,r.plannedQuantity());assertTrue(WarehouseWorkflowService.json(r).contains("\"plannedQuantity\":60"));assertNotEquals(old().toString(),r.toString());
    }
    @Test void supplementaryRequestDoesNotAcceptAnotherPlanAllocation(){
        var r=new Request(null,1L,"PRODUCTION_OUT",null,null,null,null,null,4L,5L,6L,8L,0,old().groups(),"APPEND",60);
        assertThrows(com.ruoyi.common.exception.ServiceException.class,()->OutboundSubmissionRules.validate(r,true));
    }
}
