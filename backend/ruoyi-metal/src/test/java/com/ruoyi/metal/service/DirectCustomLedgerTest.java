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

class DirectCustomLedgerTest {
    static class Ledger extends StorageLedgerService {
        final List<Map<String,Object>> sources=new ArrayList<>();
        final List<String> debts=new ArrayList<>();
        final List<Map<String,Object>> returns=new ArrayList<>();
        final List<Map<String,Object>> traces=new ArrayList<>();
        final String mode;
        String processing="DIRECT_CUSTOM";
        Ledger(String mode){this(mock(JdbcTemplate.class),mode);}
        Ledger(JdbcTemplate jdbc,String mode){
            super(jdbc,mock(FactoryScope.class));this.mode=mode;
            when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(call->{String sql=call.getArgument(0);if(sql.contains("SELECT a.* FROM metal_storage_issue_allocation"))return sources;if(sql.startsWith("SELECT * FROM metal_storage_return_allocation"))return traces;if(sql.startsWith("SELECT * FROM metal_storage_event"))return List.of(Map.of("id",1L,"action","RETURN"));return List.of();});
            when(jdbc.update(startsWith("UPDATE metal_storage_issue_allocation"),any(Object[].class))).thenAnswer(call->{
                var a=sources.stream().filter(s->s.get("id").equals(call.getArgument(3))).findFirst().orElseThrow();
                int sign=((String)call.getArgument(0)).contains("returned_weight-?")?-1:1;
                a.put("returned_weight",((BigDecimal)a.get("returned_weight")).add(((BigDecimal)call.getArgument(1)).multiply(BigDecimal.valueOf(sign))));
                a.put("returned_quantity",((Long)a.get("returned_quantity"))+sign*(Long)call.getArgument(2));return 1;
            });
            when(jdbc.update(startsWith("INSERT INTO metal_storage_return_allocation"),any(Object[].class))).thenAnswer(call->{traces.add(Map.of("issue_allocation_id",call.getArgument(2),"weight",call.getArgument(3),"quantity",call.getArgument(4)));return 1;});
        }
        void source(long owner,String weight,long quantity){var a=new HashMap<String,Object>();a.put("id",(long)sources.size()+1);a.put("customer_id",20L);a.put("work_order_id",30L);a.put("source_owner_id",owner);a.put("received_weight",new BigDecimal(weight));a.put("received_quantity",quantity);a.put("returned_weight",BigDecimal.ZERO);a.put("returned_quantity",0L);sources.add(a);}
        @Override void lock(long f){} @Override void customer(long f,long c){} @Override void work(long f,long w,long c){}
        @Override Event event(long f,long d,long l,String action,Map<String,Object> input){return new Event(1,null);}
        @Override Map<String,Object> done(Event e,Map<String,Object> result){return result;}
        @Override Map<String,Object> one(String sql,Object...args){return sql.contains("processing_type")?Map.of("processing_type",processing):Map.of("warehouse_id",1);}
        @Override Map<String,Object> stock(long f,long id){return Map.of("id",id,"warehouse_id",1,"measurement_mode",mode,"identity_key","key","identity_snapshot","{}","category","RAW");}
        @Override long insert(String sql,Object...args){if(sql.startsWith("INSERT INTO metal_storage_debt("))debts.add(args[6].toString());return 1;}
        @Override void ownership(long e,long f,long s,long owner,BigDecimal weight,long quantity,String reason){if(reason.equals("RETURN"))returns.add(Map.of("owner",owner,"weight",weight,"quantity",quantity));}
        @Override void physical(long e,long f,long s,BigDecimal w,long q,String reason){}
        @Override void entry(long e,long f,long s,Long o,String ledger,BigDecimal w,long q,String reason){}
        @Override void reconcile(long f,long s){}
    }
    Map<String,Object> issue(long owner){return new HashMap<>(Map.of("stockId",1,"customerId",20,"workOrderId",30,"sentWeight","100.00","receivedWeight","90.00","allocations",List.of(Map.of("ownerId",owner,"weight","100.00","receivedWeight","90.00","lossBearer","FACTORY"))));}
    Map<String,Object> returned(String sent,String received){return new HashMap<>(Map.of("stockId",1,"issueLineId",3,"sentWeight",sent,"netWeight",received,"lossBearer","CUSTOMER"));}
    @Test void directOwnFactoryAndBorrowedInputsNeverCreateAdvance(){
        for(long source:new long[]{0,20,40}){var ledger=new Ledger("WEIGHT");ledger.postIssue(1,2,3,issue(source));assertFalse(ledger.debts.contains("ADVANCE"));assertEquals(source==40?List.of("BORROW"):source==20?List.of("COMPENSATION"):List.of(),ledger.debts);}
    }
    @Test void processingTypeComesFromOrderNotCaller(){
        var ledger=new Ledger("WEIGHT");ledger.processing="SHORTAGE_CUSTOM";var input=issue(40);input.put("processingType","DIRECT_CUSTOM");ledger.postIssue(1,2,3,input);assertEquals(List.of("BORROW","ADVANCE"),ledger.debts);
    }
    @Test void borrowedMaterialReturnsToFactoryWithoutSettlingBorrow(){
        var ledger=new Ledger("WEIGHT");ledger.source(40,"60.00",0);ledger.source(20,"40.00",0);
        var first=ledger.postReturn(1,4,5,returned("30.00","25.00"));assertEquals("25.00",first.get("factoryWeight"));
        var second=ledger.postReturn(1,6,7,returned("40.00","40.00"));assertEquals("30.00",second.get("factoryWeight"));assertEquals("10.00",second.get("customerWeight"));
        assertEquals(new BigDecimal("60.00"),ledger.sources.get(0).get("returned_weight"));assertTrue(ledger.debts.isEmpty());
        assertThrows(ServiceException.class,()->ledger.postReturn(1,8,9,returned("31.00","31.00")));
    }
    @Test void directPieceReturnKeepsWholePiecesAndActualSplitWeight(){
        var ledger=new Ledger("PIECE");ledger.source(0,"20.00",2);ledger.source(20,"30.00",3);
        var input=returned("30.00","30.00");input.put("quantity",3);input.put("factoryWeight","20.00");
        var result=ledger.postReturn(1,4,5,input);assertEquals(2L,result.get("factoryQuantity"));assertEquals(1L,result.get("customerQuantity"));assertEquals("20.00",result.get("factoryWeight"));
        var last=returned("20.00","20.00");last.put("quantity",2);var again=ledger.postReturn(1,6,7,last);assertEquals(0L,again.get("factoryQuantity"));assertEquals("20.00",again.get("customerWeight"));
    }
    @Test void mixedPieceReturnRequiresActualFactoryWeight(){
        var ledger=new Ledger("PIECE");ledger.source(0,"20.00",2);ledger.source(20,"30.00",3);var input=returned("30.00","30.00");input.put("quantity",3);assertThrows(ServiceException.class,()->ledger.postReturn(1,4,5,input));
        input.put("factoryWeight","30.00");assertThrows(ServiceException.class,()->ledger.postReturn(1,4,5,input));
    }
    @Test void factoryReturnLossDoesNotCreateCustomerCompensation(){
        var ledger=new Ledger("WEIGHT");ledger.source(40,"60.00",0);ledger.source(20,"40.00",0);var input=returned("30.00","25.00");input.put("lossBearer","FACTORY");ledger.postReturn(1,4,5,input);assertTrue(ledger.debts.isEmpty());assertEquals(new BigDecimal("30.00"),ledger.sources.get(0).get("returned_weight"));
    }
    @Test void pieceTracingUsesActualSourceWeightsInsteadOfIndependentGreedyWeight(){
        var ledger=new Ledger("PIECE");ledger.source(0,"20.00",2);ledger.source(20,"30.00",3);var input=returned("30.00","30.00");input.put("quantity",3);input.put("factoryWeight","18.00");ledger.postReturn(1,4,5,input);
        assertEquals(new BigDecimal("18.00"),ledger.sources.get(0).get("returned_weight"));assertEquals(new BigDecimal("12.00"),ledger.sources.get(1).get("returned_weight"));
        var next=returned("18.00","18.00");next.put("quantity",2);var result=ledger.postReturn(1,6,7,next);assertEquals("18.00",result.get("customerWeight"));assertEquals(new BigDecimal("30.00"),ledger.sources.get(1).get("returned_weight"));
    }
    @Test void reversingReturnRestoresOriginalFactoryAllowance(){
        var ledger=new Ledger("WEIGHT");ledger.source(40,"60.00",0);ledger.source(20,"40.00",0);ledger.postReturn(1,4,5,returned("30.00","25.00"));ledger.reverseDocument(1,4,8);
        assertEquals(0,((BigDecimal)ledger.sources.get(0).get("returned_weight")).signum());var result=ledger.postReturn(1,9,10,returned("60.00","60.00"));assertEquals("60.00",result.get("factoryWeight"));assertTrue(ledger.debts.isEmpty());
    }
}
