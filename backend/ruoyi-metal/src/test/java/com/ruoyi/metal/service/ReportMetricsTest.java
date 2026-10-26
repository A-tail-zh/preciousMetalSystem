package com.ruoyi.metal.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ReportMetricsTest {
    @Test void summaryQueriesShareOneReadOnlySnapshot() throws Exception {
        var tx=WarehouseService.class.getMethod("summaries",Long.class).getAnnotation(org.springframework.transaction.annotation.Transactional.class);
        assertNotNull(tx);assertTrue(tx.readOnly());assertEquals(org.springframework.transaction.annotation.Isolation.REPEATABLE_READ,tx.isolation());
    }
    @Test void rateIsPercentageWithSixDecimals() { assertEquals("5.000000", ReportMetrics.percentage(new BigDecimal("5"), new BigDecimal("100"), true)); }
    @Test void invalidOrMissingBasisNeverBecomesZero() {
        assertNull(ReportMetrics.percentage(BigDecimal.ONE, BigDecimal.ZERO, true));
        assertNull(ReportMetrics.percentage(null, BigDecimal.TEN, true));
        assertNull(ReportMetrics.percentage(BigDecimal.ONE, BigDecimal.TEN, false));
        assertNull(ReportMetrics.percentage(BigDecimal.TEN, BigDecimal.ONE, true));
        assertNull(ReportMetrics.percentage(BigDecimal.ONE.negate(), BigDecimal.TEN, true));
    }
    @Test void thresholdsUseHistoricalValuesAndNeverAverage() {
        var same=ReportMetrics.thresholds(Arrays.asList("5", "5.000000"));
        assertEquals("5.000000",same.get("thresholdValue"));
        var varied=ReportMetrics.thresholds(List.of("5", "6"));
        assertNull(varied.get("thresholdValue"));assertEquals("5.000000",varied.get("thresholdMin"));assertEquals("6.000000",varied.get("thresholdMax"));assertNotNull(varied.get("thresholdWarning"));
    }
    @Test void missingThresholdCannotBeReplacedByCurrentConfiguration() {
        var result=ReportMetrics.thresholds(Arrays.asList("5",null));assertNull(result.get("thresholdValue"));assertNull(result.get("thresholdMin"));assertNotNull(result.get("thresholdWarning"));
    }
    private Map<String,Object> batch(String loss,String input,String status){var b=new LinkedHashMap<String,Object>();b.put("status",status);b.put("state",status);b.put("settledLossWeight",loss);b.put("settledCompletedQuantity",1L);b.put("netInputWeight",input);b.put("reportThresholdValue","5");return b;}
    @Test void aggregateUsesTotalWeightAndExcludesOpenAndVoidBatches() {
        var a=batch("5","100","SETTLED");var b=batch("15","300","SETTLED");var open=batch(null,"1000","OPEN");var revoked=batch("999","1000","VOID");
        var result=ProcessConsumptionService.summarize(List.of(a,b,open,revoked));assertEquals("5.000000",result.get("lossRatePercent"));assertEquals("400.000000",result.get("settledNetInputWeight"));
    }
    @Test void anyInconsistentSettledBatchMakesAggregateRateUnavailable() {var a=batch("5","100","SETTLED");a.put("state","REVIEW");assertNull(ProcessConsumptionService.summarize(List.of(a)).get("lossRatePercent"));}
    @Test void allThreeReturnTypesAreSubtractedBeforeCalculatingRate() {
        var b=new LinkedHashMap<String,Object>();b.put("status","SETTLED");b.put("rawIssuedWeight","100");b.put("semiIssuedWeight","100");b.put("rawReturnedWeight","10");b.put("semiReturnedWeight","20");b.put("processRawReturnedWeight","30");b.put("outputWeight","130");b.put("outputQuantity",10);b.put("documentCount",5);b.put("reportId",1);b.put("reportIssuedWeight","200");b.put("reportReturnedWeight","190");b.put("reportLossWeight","10");b.put("reportAverageLoss","1");b.put("reportCompletedQuantity",10);
        var result=ProcessConsumptionService.calculateBatch(b);assertEquals("140.000000",result.get("netInputWeight"));assertEquals("7.142857",result.get("lossRatePercent"));
    }
}
