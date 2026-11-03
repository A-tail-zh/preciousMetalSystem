package com.ruoyi.metal.service;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class VolumeCalculatorTest {
 private final BigDecimal pi=new BigDecimal("3.14");
 @Test void confirmedFormulas(){
  assertEquals("1570.00",VolumeCalculator.stored(VolumeCalculator.calculate("铱杆实心",Map.of("diameter","10","length","20"),pi)).toPlainString());
  assertEquals("251.20",VolumeCalculator.stored(VolumeCalculator.calculate("铱杆空心",Map.of("outerDiameter","12","innerDiameter","10","length","20"),pi)).toPlainString());
  assertEquals("69.08",VolumeCalculator.stored(VolumeCalculator.calculate("铱环",Map.of("outerDiameter","12","innerDiameter","10","height","2"),pi)).toPlainString());
  assertEquals("785.00",VolumeCalculator.stored(VolumeCalculator.calculate("铱坩埚平底",Map.of("innerDiameter","10","innerHeight","20","wallThickness","1","bottomThickness","2"),pi)).toPlainString());
 }
 @Test void invalidDimensionsAndUnsupportedCategoriesAreRejected(){assertThrows(RuntimeException.class,()->VolumeCalculator.calculate("铱杆空心",Map.of("outerDiameter","10","innerDiameter","12","length","20"),pi));assertThrows(RuntimeException.class,()->VolumeCalculator.calculate("铱坩埚弧底",Map.of(),pi));assertThrows(RuntimeException.class,()->VolumeCalculator.calculate("铱环",Map.of("outerDiameter","12"),pi));}
 @Test void geometryDoesNotIncludeDensity(){assertEquals("3.14",VolumeCalculator.stored(VolumeCalculator.calculate("铱杆实心",Map.of("diameter","2","length","1"),pi)).toPlainString());}
 @Test void scientificAndSignedDimensionInputIsRejected(){for(String value:new String[]{"1e1","+10","NaN","0.0000001"})assertThrows(RuntimeException.class,()->VolumeCalculator.calculate("铱杆实心",Map.of("diameter",value,"length","20"),pi));}
}
