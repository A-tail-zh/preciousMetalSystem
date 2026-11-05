package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PartialHandoverRulesTest {
    private BigDecimal d(String value) { return new BigDecimal(value); }
    @Test void jdbcAggregateNumbersAreReadExactly() {
        assertEquals(150L,WarehouseWorkflowService.n(d("150.0")));
        assertEquals(0L,WarehouseWorkflowService.n(0.0d));
        assertThrows(ArithmeticException.class,()->WarehouseWorkflowService.n(d("1.5")));
    }
    @Test void retainedMaterialIsNotConsumedAgainOrCountedAsLoss() {
        assertEquals(d("750"), PartialHandoverRules.consumed(d("1000"), d("250"), d("700")));
        assertEquals(d("250"), PartialHandoverRules.consumed(d("250"), d("0"), d("230")));
        assertThrows(RuntimeException.class, () -> PartialHandoverRules.consumed(d("250"), d("30"), d("230")));
    }
    @Test void sourceAllocationsRetainExactTotalWithoutNegativeRoundingRemainders() {
        var amounts = PartialHandoverRules.proportion(d("1.00"), List.of(d("1"), d("1"), d("1")));
        assertEquals(d("1.000000"), amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        assertTrue(amounts.stream().allMatch(x -> x.signum() >= 0));
        assertEquals(List.of(d("0.000000"), d("0.000000")), PartialHandoverRules.proportion(d("0"), List.of(d("0"), d("0"))));
    }
}
