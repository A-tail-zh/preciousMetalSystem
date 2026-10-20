package com.ruoyi.metal.service;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class VolumeCalculatorTest {
 private final BigDecimal pi=new BigDecimal("3.14");
 @Test void confirmedFormulas(){
  assertEquals("1570.000000",VolumeCalculator.stored(VolumeCalculator.calculate("铱杆实心",Map.of("diameter","10","length","20"),pi)).toPlainString());
  assertEquals("251.200000",VolumeCalculator.stored(VolumeCalculator.calculate("铱杆空心",Map.of("outerDiameter","12","innerDiameter","10","length","20"),pi)).toPlainString());
  assertEquals("69.080000",VolumeCalculator.stored(VolumeCalculator.calculate("铱环",Map.of("outerDiameter","12","innerDiameter","10","height","2"),pi)).toPlainString());
  assertEquals("785.000000",VolumeCalculator.stored(VolumeCalculator.calculate("铱坩埚平底",Map.of("innerDiameter","10","innerHeight","20","wallThickness","1","bottomThickness","2"),pi)).toPlainString());
 }
 @Test void invalidDimensionsAndUnsupportedCategoriesAreRejected(){assertThrows(RuntimeException.class,()->VolumeCalculator.calculate("铱杆空心",Map.of("outerDiameter","10","innerDiameter","12","length","20"),pi));assertThrows(RuntimeException.class,()->VolumeCalculator.calculate("铱坩埚弧底",Map.of(),pi));assertThrows(RuntimeException.class,()->VolumeCalculator.calculate("铱环",Map.of("outerDiameter","12"),pi));}
 @Test void quotationMultipliesConfiguredPriceAndQuantity(){
  var rule=new com.ruoyi.metal.catalog.QuoteRule();rule.setPiValue(pi);rule.setBasePrice(new BigDecimal("22.65"));
  var service=new QuoteRuleService(null){@Override public com.ruoyi.metal.catalog.QuoteRule get(){return rule;}};
  var input=new com.ruoyi.metal.catalog.CalculationRequest("铱杆实心",Map.of("diameter","10","length","20"),2);
  var result=service.calculate(input);assertEquals("1570.000000",result.get("volume"));assertEquals("35560.500000",result.get("unitPrice"));assertEquals("71121.000000",result.get("totalPrice"));
  rule.setBasePrice(new BigDecimal("7"));assertEquals("21980.000000",service.calculate(input).get("totalPrice"));
 }
}
