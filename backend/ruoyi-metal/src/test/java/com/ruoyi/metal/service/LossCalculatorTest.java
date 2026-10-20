package com.ruoyi.metal.service;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class LossCalculatorTest {
 BigDecimal d(String value){return new BigDecimal(value);}
 @Test void exampleAndBoundary(){var r=LossCalculator.calculate(d("100"),d("90"),5,d("1.5"),d("0.5"));assertEquals(0,r.loss().compareTo(d("10")));assertEquals(0,r.average().compareTo(d("2")));assertFalse(r.abnormal());assertTrue(LossCalculator.calculate(d("100"),d("89.999999"),5,d("1.5"),d("0.5")).abnormal());}
 @Test void rejectsInvalidMassOrQuantity(){assertThrows(Exception.class,()->LossCalculator.calculate(d("100"),d("101"),5,d("0"),d("0")));assertThrows(Exception.class,()->LossCalculator.calculate(d("0"),d("0"),5,d("0"),d("0")));assertThrows(Exception.class,()->LossCalculator.calculate(d("100"),d("90"),0,d("0"),d("0")));}
 @Test void allocationKeepsTailAndTotal(){var values=LossCalculator.allocate(d("1"),List.of(d("1"),d("1"),d("1")));assertEquals(List.of(d("0.333333"),d("0.333333"),d("0.333334")),values);assertEquals(0,values.stream().reduce(BigDecimal.ZERO,BigDecimal::add).compareTo(d("1")));}
 @Test void comparesBeforeDisplayRounding(){var r=LossCalculator.calculate(d("10"),d("9.999999"),3,d("0"),d("0"));assertEquals(0,r.average().compareTo(d("0")));assertTrue(r.abnormal());}
}
