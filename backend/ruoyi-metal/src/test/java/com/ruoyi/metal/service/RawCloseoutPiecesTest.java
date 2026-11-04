package com.ruoyi.metal.service;

import java.lang.reflect.*;
import java.math.BigDecimal;
import java.util.*;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.metal.catalog.WarehouseFlowRequests.Closeout;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RawCloseoutPiecesTest {
    private Map<String,Object> stock(long id,String mode,String weight,long quantity){
        return new HashMap<>(Map.of("inventory_id",id,"category","RAW","measurement_mode",mode,"effective_measurement_mode",mode,"measurement_policy_version",3,"net_weight",new BigDecimal(weight),"quantity",quantity));
    }
    @SuppressWarnings("unchecked")
    private Map<Long,Long> allocate(List<Map<String,Object>> stocks,Map<Long,BigDecimal> weights,Map<Long,Long> pieces)throws Exception {
        var type=assertDoesNotThrow(()->Class.forName("com.ruoyi.metal.service.RawCloseoutPieces"));
        var method=assertDoesNotThrow(()->type.getMethod("allocate",List.class,Map.class,Map.class));
        try{return (Map<Long,Long>)method.invoke(null,stocks,weights,pieces);}
        catch(InvocationTargetException e){if(e.getCause() instanceof Exception error)throw error;throw e;}
    }
    private Map<Long,BigDecimal> weight(long id,String weight){return Map.of(id,new BigDecimal(weight));}

    @Test void absentMapAutomaticallyTakesAllPiecesOnlyWithAllAvailableWeight()throws Exception {
        var stock=stock(1L,"PIECE","10",5L);
        assertEquals(Map.of(1L,5L),allocate(List.of(stock),weight(1L,"10.00"),null));
        assertThrows(ServiceException.class,()->allocate(List.of(stock),weight(1L,"4"),null));
    }
    @Test void partialWeightRequiresExplicitPositivePiecesWithoutProportionalInference()throws Exception {
        var stock=stock(1L,"PIECE","10",5L);
        assertEquals(Map.of(1L,1L),allocate(List.of(stock),weight(1L,"4"),Map.of(1L,1L)));
        assertEquals(Map.of(1L,3L),allocate(List.of(stock),weight(1L,"4"),Map.of(1L,3L)));
    }
    @Test void partialWeightCannotTakeEveryPieceAndFullWeightCannotLeavePieces(){
        var stock=stock(1L,"PIECE","10",5L);
        assertThrows(ServiceException.class,()->allocate(List.of(stock),weight(1L,"4"),Map.of(1L,5L)));
        assertThrows(ServiceException.class,()->allocate(List.of(stock),weight(1L,"10"),Map.of(1L,4L)));
    }
    @Test void heldBalancesAreExcludedFromFullAllocation()throws Exception {
        var stock=stock(1L,"PIECE","10",5L);stock.put("available_weight",new BigDecimal("6"));stock.put("available_quantity",3L);
        assertEquals(Map.of(1L,3L),allocate(List.of(stock),weight(1L,"6"),null));
        assertThrows(ServiceException.class,()->allocate(List.of(stock),weight(1L,"7"),Map.of(1L,2L)));
        assertThrows(ServiceException.class,()->allocate(List.of(stock),weight(1L,"5"),Map.of(1L,4L)));
    }
    @Test void duplicateResidualLotsNeverDuplicateAnInventoryQuantity()throws Exception {
        var stock=stock(1L,"PIECE","10",5L);
        assertEquals(Map.of(1L,5L),allocate(List.of(stock,new HashMap<>(stock)),weight(1L,"10"),null));
        assertEquals(Map.of(1L,2L),allocate(List.of(stock,new HashMap<>(stock)),weight(1L,"4"),Map.of(1L,2L)));
    }
    @Test void unknownAndUnselectedRequestedIdsAreRejected(){
        var one=stock(1L,"PIECE","10",5L);var two=stock(2L,"PIECE","20",9L);
        assertThrows(ServiceException.class,()->allocate(List.of(one,two),weight(1L,"4"),Map.of(1L,2L,2L,1L)));
        assertThrows(ServiceException.class,()->allocate(List.of(one),weight(1L,"4"),Map.of(1L,2L,99L,1L)));
        assertThrows(ServiceException.class,()->allocate(List.of(one),weight(99L,"4"),null));
    }
    @Test void explicitQuantitiesForWeightStocksAndReferenceCountsAreRejected()throws Exception {
        var stock=stock(1L,"WEIGHT","10",0L);stock.put("reference_quantity",100L);
        assertEquals(Map.of(1L,0L),allocate(List.of(stock),weight(1L,"4"),null));
        assertThrows(ServiceException.class,()->allocate(List.of(stock),weight(1L,"4"),Map.of(1L,1L)));
    }
    @Test void legacyRawPieceDefaultDoesNotBecomePieceCapacity()throws Exception {
        var stock=stock(1L,"PIECE","10",0L);stock.put("measurement_policy_version",2);
        assertEquals(Map.of(1L,0L),allocate(List.of(stock),weight(1L,"4"),null));
        assertThrows(ServiceException.class,()->allocate(List.of(stock),weight(1L,"4"),Map.of(1L,1L)));
    }
    @Test void zeroNegativeAndExcessPiecesOrWeightAreRejected(){
        var stock=stock(1L,"PIECE","10",5L);
        for(long quantity:List.of(0L,-1L,6L))assertThrows(ServiceException.class,()->allocate(List.of(stock),weight(1L,"4"),Map.of(1L,quantity)));
        for(String quantity:List.of("0","-1","11"))assertThrows(ServiceException.class,()->allocate(List.of(stock),weight(1L,quantity),Map.of(1L,2L)));
    }
    @Test void multipleWarehousesKeepEachInventoryBalanceIndependent()throws Exception {
        var one=stock(1L,"PIECE","10",5L);one.put("warehouse_id",10L);var two=stock(2L,"PIECE","20",9L);two.put("warehouse_id",11L);
        assertEquals(Map.of(1L,2L,2L,3L),allocate(List.of(one,two),Map.of(1L,new BigDecimal("4"),2L,new BigDecimal("7")),Map.of(1L,2L,2L,3L)));
    }
    @Test void providedMapMustCoverEverySelectedPieceStock(){
        assertThrows(ServiceException.class,()->allocate(List.of(stock(1L,"PIECE","10",5L)),weight(1L,"10"),Map.of()));
    }
    @Test void closeoutOptionalPieceMapKeepsTheOriginalJavaConstructor()throws Exception {
        var original=new Closeout("request",0,"RETURN",7L,null,BigDecimal.ONE,"reason");
        assertNull(assertDoesNotThrow(()->Closeout.class.getMethod("pieceQuantities")).invoke(original));
        var constructor=assertDoesNotThrow(()->Closeout.class.getConstructor(String.class,Integer.class,String.class,Long.class,Long.class,BigDecimal.class,String.class,Map.class));
        var updated=constructor.newInstance("request",0,"RETURN",7L,null,BigDecimal.ONE,"reason",Map.of(1L,2L));
        assertEquals(Map.of(1L,2L),Closeout.class.getMethod("pieceQuantities").invoke(updated));
    }
}
