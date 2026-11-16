package com.ruoyi.metal.service;
import java.math.BigDecimal;import java.util.List;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class SemiFlowRulesTest {
 @Test void routeRequiresExactPrefixAndNonemptySpecification(){assertTrue(SemiFlowRules.compatible(1L," 10cm ",List.of(1L,2L),1L,"10cm",List.of(1L,2L)));assertFalse(SemiFlowRules.compatible(1L,"10cm",List.of(1L),1L,"10cm",List.of(1L,2L)));assertFalse(SemiFlowRules.compatible(1L,"",List.of(1L),1L,"",List.of(1L)));assertFalse(SemiFlowRules.compatible(1L,"10cm",List.of(1L),2L,"10cm",List.of(1L)));}
 @Test void customerMustBeKnownAndExact(){assertTrue(SemiFlowRules.sameCustomer(" A ","A"));assertFalse(SemiFlowRules.sameCustomer("",""));assertFalse(SemiFlowRules.sameCustomer("A","B"));}
 @Test void returnSubtractsFromUsablePieces(){assertEquals(6,SemiFlowRules.netPieces(10,4));assertThrows(RuntimeException.class,()->SemiFlowRules.netPieces(3,4));}
 @Test void lossExcludesBothTypesOfReturn(){assertEquals(new BigDecimal("2.000000"),SemiFlowRules.loss(new BigDecimal("100"),BigDecimal.ZERO,new BigDecimal("40"),new BigDecimal("58")));}
 @Test void releaseCannotLeaveWeightWithoutPieces(){assertDoesNotThrow(()->SemiFlowRules.release(4,new BigDecimal("40"),2,new BigDecimal("20")));assertThrows(RuntimeException.class,()->SemiFlowRules.release(4,new BigDecimal("40"),4,new BigDecimal("39")));assertThrows(RuntimeException.class,()->SemiFlowRules.release(4,new BigDecimal("40"),2,new BigDecimal("40")));}
 @Test void partialCloseDoesNotInventFinishedPieces(){assertEquals(4,SemiFlowRules.cancelled(10,6));assertEquals(10,SemiFlowRules.cancelled(10,0));assertThrows(RuntimeException.class,()->SemiFlowRules.cancelled(10,11));}
}
