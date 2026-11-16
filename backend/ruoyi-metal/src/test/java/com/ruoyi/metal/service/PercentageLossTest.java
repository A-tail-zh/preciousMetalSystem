package com.ruoyi.metal.service;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.ruoyi.common.exception.ServiceException;
import static org.junit.jupiter.api.Assertions.*;
class PercentageLossTest {
 private BigDecimal d(String v){return new BigDecimal(v);}
 @Test void threeReturnsAreExcludedFromInput(){var r=LossCalculator.percentage(d("120"),d("20"),d("95"),d("5"));assertEquals(d("5"),r.loss());assertFalse(r.abnormal());assertEquals("5.00",r.rate().toPlainString());}
 @Test void comparesBeforeDisplayRounding(){assertTrue(LossCalculator.percentage(d("10000"),d("0"),d("9499.99"),d("5")).abnormal());assertFalse(LossCalculator.percentage(d("10000"),d("0"),d("9500"),d("5")).abnormal());}
 @Test void zeroAndHundredPercentAreValid(){assertFalse(LossCalculator.percentage(d("10"),d("0"),d("10"),d("0")).abnormal());assertTrue(LossCalculator.percentage(d("10"),d("0"),d("9.99"),d("0")).abnormal());assertFalse(LossCalculator.percentage(d("10"),d("0"),d("0"),d("100")).abnormal());}
 @Test void weightReportsKeepPercentThresholdAndDetectCorruptSnapshot(){
  var b=new HashMap<String,Object>();b.put("status","SETTLED");b.put("measurementMode","WEIGHT");b.put("reportId",1);b.put("reportVoided",false);b.put("rawIssuedWeight","100");b.put("outputWeight","95");b.put("outputQuantity",0);b.put("documentCount",2);b.put("reportIssuedWeight","100");b.put("reportReturnedWeight","95");b.put("reportLossWeight","5");b.put("reportCompletedQuantity",0);b.put("reportAverageLoss","0");b.put("reportPrecisionVersion",2);b.put("lossUnit","PERCENT");b.put("reportNetInputWeight","100");b.put("reportLossRatePercent","5");b.put("reportThresholdValue","1");
  var valid=ProcessConsumptionService.calculateBatch(new HashMap<>(b));assertEquals("SETTLED",valid.get("state"));assertEquals("1.00",ProcessConsumptionService.summarize(List.of(valid)).get("thresholdValue"));
  b.put("reportLossRatePercent","1");var invalid=ProcessConsumptionService.calculateBatch(b);assertEquals("REVIEW",invalid.get("state"));assertNull(invalid.get("lossRatePercent"));
 }
 @Test void rejectsInvalidWeightAndRate(){for(String rate:new String[]{"-0.01","100.01"})assertThrows(ServiceException.class,()->LossCalculator.percentage(d("10"),d("0"),d("9"),d(rate)));assertThrows(ServiceException.class,()->LossCalculator.percentage(d("10"),d("10"),d("0"),d("5")));assertThrows(ServiceException.class,()->LossCalculator.percentage(d("10"),d("0"),d("11"),d("5")));}
}
