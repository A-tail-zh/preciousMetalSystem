package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.util.*;
import com.ruoyi.common.exception.ServiceException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StorageRepaymentTotalsTest {
    static class Ledger extends StorageLedgerServiceTest.Ledger {
        int transfers,settlements;
        Ledger(String mode){super(mode);}
        @Override Map<String,Object> one(String sql,Object...args){return Map.of("identity_key","key","debtor_id",20L,"creditor_id",0L);}
        @Override void ownership(long e,long f,long s,long o,BigDecimal w,long q,String r){transfers++;}
        @Override void settle(long e,long f,Map<String,Object> debt,BigDecimal w,long q,String r){settlements++;}
    }
    Map<String,Object> input(boolean piece){return new HashMap<>(Map.of("stockId",1,"ownerId",20,"netWeight","100.00","quantity",piece?10:0,"debtAllocations",List.of(Map.of("debtId",1,"weight","40.00","quantity",piece?4:0),Map.of("debtId",2,"weight","60.00","quantity",piece?6:0))));}
    @Test void mismatchedDocumentTotalsRejectBeforeAnyTransfer(){
        for(boolean piece:List.of(false,true))for(String field:piece?List.of("netWeight","quantity","actualNetWeight","actualQuantity"):List.of("netWeight","actualNetWeight","actualQuantity")){
            var ledger=new Ledger(piece?"PIECE":"WEIGHT");var body=input(piece);body.put(field,field.contains("Weight")?"1.00":1);
            assertThrows(ServiceException.class,()->ledger.postRepayment(1,2,3,body),field);
            assertEquals(0,ledger.transfers,field);assertEquals(0,ledger.settlements,field);
        }
    }
    @Test void matchingMultiDebtTotalsAllowBothMeasurementModes(){
        for(boolean piece:List.of(false,true)){var ledger=new Ledger(piece?"PIECE":"WEIGHT");var body=input(piece);body.put("actualNetWeight","100.00");body.put("actualQuantity",piece?10:0);var result=ledger.postRepayment(1,2,3,body);assertEquals("100.00",result.get("weight"));assertEquals(4,ledger.transfers);assertEquals(2,ledger.settlements);}
    }
}
