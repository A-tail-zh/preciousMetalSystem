package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.util.*;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.metal.security.FactoryScope;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class StorageLedgerServiceTest {
    static class InboundLedger extends StorageLedgerService {
        final String category,materialForm;
        InboundLedger(String category){this(category,"METAL");}
        InboundLedger(String category,String materialForm){super(mock(JdbcTemplate.class),mock(FactoryScope.class));this.category=category;this.materialForm=materialForm;}
        @Override Map<String,Object> one(String sql,Object...args){
            assertEquals(1L,args[args.length-1],"Every source lookup remains factory scoped");
            if(sql.contains("metal_production_order_step"))return Map.of("product_id",7,"order_id",8,"output_category",category,"measurement_mode","PIECE","product_snapshot","{\"metalTypeId\":9,\"name\":\"产品\",\"purity\":\"99.123456\"}","process_name","工序");
            if(sql.contains("metal_material m"))return Map.of("material_id",6,"metal_type_id",9,"measurement_mode","WEIGHT","material_form",materialForm);
            return Map.of("name","金属","warehouse_id",2);
        }
    }
    @Test void optionalPurityAcceptsBlankAndBoundariesWithoutGuessing(){
        for(Object value:Arrays.asList(null,"","  "))assertNull(StorageLedgerService.optionalPurity(value));
        for(String value:List.of("0","0.00","99.99","100","100.00"," 99.99 "))assertEquals(new BigDecimal(value.trim()).setScale(2).toPlainString(),StorageLedgerService.optionalPurity(value));
        for(String value:List.of("-1","100.01","1.001","99.000000","abc","NaN","1e2","+1"))assertEquals("纯度须为0至100，最多两位小数",assertThrows(ServiceException.class,()->StorageLedgerService.optionalPurity(value)).getMessage());
    }
    @Test void receiptsNormalizeMissingEmptyAndNullPurityIdentically(){
        for(String type:List.of("RAW_IN","CUSTOMER_IN","PROCESS_RAW_IN","RECOVERY_RETURN","SEMI_IN","PRODUCT_IN")){
            String category=type.equals("SEMI_IN")?"SEMI":type.equals("PRODUCT_IN")?"PRODUCT":"RAW";String key=null;
            for(String shape:List.of("missing","null","empty","spaces")){
                var input=productionReceipt(category);input.put("materialId",6);input.remove("purity");
                input.put("productionIdentity",Map.of("originOrderId","8","originStepId","3","sourceExecutionId","4","completedProcessIds",List.of("5"),"form","METAL"));
                if(!shape.equals("missing"))input.put("purity",shape.equals("null")?null:shape.equals("empty")?"":"  ");
                if(type.equals("CUSTOMER_IN"))input.remove("productionIdentity");
                var normalized=new InboundLedger(category,type.equals("CUSTOMER_IN")?"UNTREATED":"METAL").normalizeInbound(1,type,input);
                assertNull(normalized.get("purity"));assertNull(((Map<?,?>)normalized.get("identity")).get("purity"));
                if(key==null)key=(String)normalized.get("identityKey");else assertEquals(key,normalized.get("identityKey"));
            }
        }
    }
    Map<String,Object> productionReceipt(String category){return new HashMap<>(Map.of("warehouseId",2,"productId",7,"purity","99.00","category",category,"productionIdentity",Map.of("originOrderId","8","originStepId","3","sourceExecutionId","4","completedProcessIds",List.of("5"))));}
    @Test void factoryReceiptsIgnoreLegacyAndForgedExternalFields(){
        for(String category:List.of("SEMI","PRODUCT"))for(String reference:List.of("","fake external receipt")){
            var input=productionReceipt(category);input.put("externalSource",Map.of("sourceReference",reference,"completedProcessIds",List.of()));
            var result=new InboundLedger(category).normalizeInbound(1,category+"_IN",input);
            assertFalse(result.containsKey("externalSource"));assertEquals(8,result.get("originOrderId"));
            var identity=(Map<?,?>)result.get("identity");assertEquals("4",identity.get("sourceExecutionId"));assertFalse(identity.containsKey("externalSourceReference"));
        }
    }
    @Test void externalReceiptRequiresEvidenceEvenWithProductionIdentity(){
        var input=productionReceipt("SEMI");var ledger=new InboundLedger("SEMI");
        assertEquals("外来半成品须登记真实来源凭据",assertThrows(ServiceException.class,()->ledger.normalizeInbound(1,"EXTERNAL_SEMI_IN",input)).getMessage());
        input.put("externalSource",Map.of("sourceReference","","completedProcessIds",List.of("5")));
        assertEquals("外来半成品须登记真实来源凭据",assertThrows(ServiceException.class,()->ledger.normalizeInbound(1,"EXTERNAL_SEMI_IN",input)).getMessage());
    }
    @Test void factoryReceiptCannotUseExternalEvidenceWithoutExecution(){
        var input=productionReceipt("SEMI");input.remove("productionIdentity");input.put("externalSource",Map.of("sourceReference","external receipt","completedProcessIds",List.of("5")));
        assertEquals("厂内产出须关联有效工单和加工批次",assertThrows(ServiceException.class,()->new InboundLedger("SEMI").normalizeInbound(1,"SEMI_IN",input)).getMessage());
    }
    @Test void inboundChecksFactoryBeforeResolvingSource(){
        var jdbc=mock(JdbcTemplate.class);var scope=mock(FactoryScope.class);doThrow(new ServiceException("cross factory")).when(scope).check(2L);
        assertThrows(ServiceException.class,()->new StorageLedgerService(jdbc,scope).normalizeInbound(2,"SEMI_IN",productionReceipt("SEMI")));verifyNoInteractions(jdbc);
    }
    /** Exercise posting policy with persistence replaced, not duplicate its decision logic. */
    static class Ledger extends StorageLedgerService {
        final List<Object[]> debts=new ArrayList<>(); final String mode;
        Ledger(String mode){super(mock(JdbcTemplate.class),mock(FactoryScope.class));this.mode=mode;}
        @Override void lock(long f){} @Override void customer(long f,long c){} @Override void work(long f,long w,long c){}
        @Override Event event(long f,long d,long l,String a,Map<String,Object> i){return new Event(1,null);}
        @Override Map<String,Object> done(Event e,Map<String,Object> r){return r;}
        @Override Map<String,Object> one(String sql,Object...args){return Map.of("warehouse_id",1);}
        @Override Map<String,Object> stock(long f,long id){return Map.of("id",id,"warehouse_id",1,"measurement_mode",mode,"identity_key","key","identity_snapshot","{}","category","RAW");}
        @Override long insert(String sql,Object...args){if(sql.startsWith("INSERT INTO metal_storage_debt("))debts.add(args);return debts.size()+1;}
        @Override void bindSlice(long e,long f,long s,Map<String,Object> input){}
        @Override Long boundSlice(long e,long f){return null;}
        @Override void ownership(long e,long f,long s,long o,BigDecimal w,long q,String r){}
        @Override void physical(long e,long f,long s,BigDecimal w,long q,String r){}
        @Override void entry(long e,long f,long s,Long o,String l,BigDecimal w,long q,String r){}
        @Override void reconcile(long f,long s){}
    }
    Map<String,Object> issue(long owner,String bearer,boolean pieces){
        var allocation=new HashMap<String,Object>(Map.of("ownerId",owner,"weight","100.00","receivedWeight","90.00","quantity",pieces?10:0,"receivedQuantity",pieces?10:0));
        if(bearer!=null)allocation.put("lossBearer",bearer);
        return new HashMap<>(Map.of("stockId",1,"customerId",20,"workOrderId",30,"sentWeight","100.00","receivedWeight","90.00","sentQuantity",pieces?10:0,"receivedQuantity",pieces?10:0,"allocations",List.of(allocation)));
    }
    @Test void factoryBearsOtherCustomerLossWithoutDoubleCompensation(){
        var l=new Ledger("WEIGHT");l.postIssue(1,2,3,issue(40,"FACTORY",false));
        assertEquals(2,l.debts.size());assertDebt(l.debts.get(0),40,0,"BORROW","100.00");assertDebt(l.debts.get(1),0,20,"ADVANCE","90.00");
    }
    @Test void customerBearsBorrowedLossOwesFullSentAmount(){
        var l=new Ledger("WEIGHT");l.postIssue(1,2,3,issue(40,"CUSTOMER",false));assertDebt(l.debts.get(1),0,20,"ADVANCE","100.00");
    }
    @Test void ownMaterialLossCreatesOnlyFactoryCompensation(){
        var l=new Ledger("WEIGHT");l.postIssue(1,2,3,issue(20,"FACTORY",false));assertEquals(1,l.debts.size());assertDebt(l.debts.get(0),20,0,"COMPENSATION","10.00");
    }
    @Test void unchangedPieceCountDoesNotCreateWeightCompensation(){
        var l=new Ledger("PIECE");l.postIssue(1,2,3,issue(20,"FACTORY",true));assertTrue(l.debts.isEmpty());
    }
    @Test void handoverDifferenceRequiresExplicitBearer(){
        var l=new Ledger("WEIGHT");assertThrows(ServiceException.class,()->l.postIssue(1,2,3,issue(20,null,false)));assertTrue(l.debts.isEmpty());
    }
    @Test void mismatchedAllocationTotalsRejectPosting(){
        var l=new Ledger("WEIGHT");var input=issue(20,"CUSTOMER",false);input.put("sentWeight","101.00");assertThrows(ServiceException.class,()->l.postIssue(1,2,3,input));
    }
    @Test void lineageAndDisplayNamesDoNotChangeRepaymentIdentity(){
        var a=new HashMap<String,Object>(Map.of("category","SEMI","productId","1","purity","99.00","form","METAL","completedProcessIds",List.of("4","5"),"measurementMode","WEIGHT"));var b=new HashMap<>(a);b.put("name","renamed");b.put("originCustomerWeight","25.00");b.put("originFactoryWeight","75.00");assertEquals(StorageLedgerService.identityKey(a),StorageLedgerService.identityKey(b));b.put("completedProcessIds",List.of("4"));assertNotEquals(StorageLedgerService.identityKey(a),StorageLedgerService.identityKey(b));
    }
    @Test void precisionAndMeasurementAreStrict(){assertThrows(ServiceException.class,()->StorageLedgerService.amount("1.001"));assertThrows(ServiceException.class,()->StorageLedgerService.amount("-1"));assertThrows(ServiceException.class,()->StorageLedgerService.count(Map.of("quantity",1),"quantity",false));assertEquals(new BigDecimal("0.00"),StorageLedgerService.amount("0"));}
    @Test void fullDispatchLossPostsCompensationWithoutRemainingReceipt(){var l=new Ledger("WEIGHT");l.postDispatch(1,2,3,Map.of("stockId",1,"customerId",20,"sentWeight","100.00","receivedWeight","0.00","lossBearer","FACTORY"));assertEquals(1,l.debts.size());assertDebt(l.debts.get(0),20,0,"COMPENSATION","100.00");}
    @Test void forgedFactoryRejectedBeforeReadingInventory(){var jdbc=mock(JdbcTemplate.class);var scope=mock(FactoryScope.class);when(scope.resolveFactory(2L)).thenThrow(new ServiceException("cross factory"));var ledger=new StorageLedgerService(jdbc,scope);assertThrows(ServiceException.class,()->ledger.inventory(2L,Map.of(),1,16));verifyNoInteractions(jdbc);}
    @Test void replayRejectsChangedContent(){var jdbc=mock(JdbcTemplate.class);var input=Map.<String,Object>of("weight","10.00");when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(Map.of("id",1,"request_hash",StorageLedgerService.hash(input),"result","{\"stockId\":\"4\"}")));var ledger=new StorageLedgerService(jdbc,mock(FactoryScope.class));assertEquals("4",ledger.event(1,2,3,"INBOUND",input).replay().get("stockId"));assertThrows(ServiceException.class,()->ledger.event(1,2,3,"INBOUND",Map.of("weight","11.00")));}
    @Test void pieceSettlementUsesPieceFractionNotReturnedPhysicalWeight(){var jdbc=mock(JdbcTemplate.class);var ledger=new StorageLedgerService(jdbc,mock(FactoryScope.class));ledger.settle(1,2,Map.of("id",3,"measurement_mode","PIECE","remaining_weight",new BigDecimal("100.00"),"remaining_quantity",10),new BigDecimal("12.00"),2,"RETURN");verify(jdbc).update(startsWith("UPDATE metal_storage_debt SET"),eq(new BigDecimal("20.00")),eq(2L),eq(3L),eq(2L));}
    void assertDebt(Object[] args,long creditor,long debtor,String kind,String weight){assertEquals(creditor,args[4]);assertEquals(debtor,args[5]);assertEquals(kind,args[6]);assertEquals(new BigDecimal(weight),args[11]);}
 @org.junit.jupiter.api.Test void optionalWeightQuantityIsZeroButPieceQuantityStaysStrict(){var input=new java.util.HashMap<String,Object>();input.put("quantity",null);org.junit.jupiter.api.Assertions.assertEquals(0,StorageLedgerService.count(input,"quantity",false));org.junit.jupiter.api.Assertions.assertThrows(com.ruoyi.common.exception.ServiceException.class,()->StorageLedgerService.count(input,"quantity",true));input.put("quantity","1");org.junit.jupiter.api.Assertions.assertThrows(com.ruoyi.common.exception.ServiceException.class,()->StorageLedgerService.count(input,"quantity",false));}
}
