package com.ruoyi.metal.service;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class OutboundLossBasisTest {
 private BigDecimal d(String n){return new BigDecimal(n);}
 @Test void outboundDenominatorAndExactThreshold(){
  var r=LossCalculator.percentage(d("100"),d("20"),d("70"),d("12"),"OUTBOUND_TOTAL");
  assertEquals(d("10"),r.loss());assertEquals(d("10.00"),r.rate());assertFalse(r.abnormal());
  assertFalse(LossCalculator.percentage(d("100"),d("20"),d("70"),d("10"),"OUTBOUND_TOTAL").abnormal());
  assertTrue(LossCalculator.percentage(d("100"),d("20"),d("69.999999"),d("10"),"OUTBOUND_TOTAL").abnormal());
  assertEquals(d("12.50"),LossCalculator.percentage(d("100"),d("20"),d("70"),d("12"),"NET_INPUT").rate());
 }
 private Map<String,Object> batch(String issued,String returned,String output,String basis){
  var b=new LinkedHashMap<String,Object>();b.put("status","SETTLED");b.put("rawIssuedWeight",issued);b.put("rawReturnedWeight",returned);b.put("outputWeight",output);b.put("outputQuantity",1);b.put("documentCount",3);b.put("reportId",1);b.put("reportIssuedWeight",issued);b.put("reportReturnedWeight",d(returned).add(d(output)));b.put("reportLossWeight","10");b.put("reportAverageLoss","10");b.put("reportCompletedQuantity",1);b.put("lossRateBasis",basis);b.put("reportLossRateBasis",basis);b.put("reportLossRateDenominatorWeight",basis.equals("OUTBOUND_TOTAL")?issued:d(issued).subtract(d(returned)));return ProcessConsumptionService.calculateBatch(b);
 }
 @Test void aggregatesTotalWeightsInsteadOfAveragingRates(){var s=ProcessConsumptionService.summarize(List.of(batch("100","20","70","OUTBOUND_TOTAL"),batch("200","20","170","OUTBOUND_TOTAL")));assertEquals("6.67",s.get("lossRatePercent"));assertEquals("300.000000",s.get("lossRateDenominatorWeight"));}
 @Test void mixedBasesAreSeparated(){var s=ProcessConsumptionService.summarize(List.of(batch("100","20","70","OUTBOUND_TOTAL"),batch("200","20","170","NET_INPUT")));assertNull(s.get("lossRatePercent"));assertEquals(2,((List<?>)s.get("lossRateGroups")).size());}
}
