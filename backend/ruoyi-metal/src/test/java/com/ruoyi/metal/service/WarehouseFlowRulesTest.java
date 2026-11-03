package com.ruoyi.metal.service;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WarehouseFlowRulesTest {
    private BigDecimal d(String value) { return new BigDecimal(value); }

    @Test void fixedAcceptanceSeparatesProcessingLossFromCustodyDifference() {
        var m = WarehouseFlowRules.mass(d("100"),d("96"),d("3"),d("98.8"));
        assertEquals(0,m.netInput().compareTo(d("97")));
        assertEquals(0,m.loss().compareTo(d("1")));
        assertEquals(0,m.handoverDifference().compareTo(d("0.2")));
        var rate = LossCalculator.percentage(d("100"),d("3"),d("96"),d("1"));
        assertEquals(0,rate.rate().compareTo(d("1.03")));
    }

    @Test void oppositeLineDifferencesNeverExcuseMissingReasons() {
        assertThrows(RuntimeException.class,()->WarehouseFlowRules.difference(d("10"),d("9"),""));
        assertThrows(RuntimeException.class,()->WarehouseFlowRules.difference(d("10"),d("11"),""));
        assertDoesNotThrow(()->WarehouseFlowRules.difference(d("10"),d("9"),"逐条复称差异"));
    }

    @Test void reservationsProtectBothPhysicalDimensionsAndWeightReferencesAreNotPieces() {
        assertThrows(RuntimeException.class,()->WarehouseFlowRules.available(d("100"),10,d("90"),9,d("11"),1,false));
        assertThrows(RuntimeException.class,()->WarehouseFlowRules.available(d("100"),10,d("90"),9,d("10"),2,false));
        assertDoesNotThrow(()->WarehouseFlowRules.available(d("100"),0,d("90"),0,d("10"),0,true));
    }

    @Test void missingLegacyCustomerNeverBecomesFactoryStock() {
        assertThrows(RuntimeException.class,()->WarehouseFlowRules.newOwner(null,null));
        assertEquals("CUSTOMER",WarehouseFlowRules.newOwner(null,9L));
        assertEquals("FACTORY",WarehouseFlowRules.newOwner("FACTORY",null));
        assertThrows(RuntimeException.class,()->WarehouseFlowRules.newOwner("FACTORY",9L));
    }

    @Test void extraReceiptIsSeparateWhileNegativeProcessingLossIsRejected() {
        assertEquals(0,WarehouseFlowRules.mass(d("100"),d("100"),d("0"),d("101")).handoverDifference().compareTo(d("-1")));
        assertThrows(RuntimeException.class,()->WarehouseFlowRules.mass(d("100"),d("101"),d("0"),d("100")));
        assertThrows(RuntimeException.class,()->WarehouseFlowRules.weight(d("0.001")));
        assertEquals(0,WarehouseFlowRules.weight(d("0.005")).compareTo(d("0.01")));
    }
}
