package com.ruoyi.metal.service;
import java.math.BigDecimal;import java.util.*;
import org.junit.jupiter.api.Test;import com.ruoyi.metal.catalog.CalculationRequest;
import static org.junit.jupiter.api.Assertions.*;
class MetalWeightCalculationTest {
 private QuoteRuleService service(String metal,String density){return new QuoteRuleService(null){
  @Override public Map<String,Object> volumeRule(){return Map.of("piValue","3.14","curvedBottomFactor","1.03","version",0);}
  @Override public String metalName(Long id){return metal;}
  @Override public Map<String,Object> rule(Long id){var m=new LinkedHashMap<String,Object>();m.put("piValue","3.14");m.put("density",density);return m;}
 };}
 @Test void metalAndShapeProduceWeightsWithoutCurrency(){var input=new CalculationRequest(null,Map.of("innerDiameter","0","outerDiameter","2","length","1"),3,1L,"杆");var r=service("铱","22.65").calculate(input);assertEquals(false,r.get("pricingEnabled"));assertEquals("6.28",r.get("volume"));assertEquals("142.24",r.get("unitWeight"));assertEquals("426.72",r.get("totalWeight"));assertFalse(r.containsKey("unitPrice"));assertFalse(r.containsKey("totalPrice"));}
 @Test void anotherMetalUsesItsOwnDensity(){var r=service("铂","21.45").calculate(new CalculationRequest(null,Map.of("innerDiameter","0","outerDiameter","2","length","1"),2,2L,"杆"));assertEquals("铂杆",r.get("category"));assertEquals("134.71",r.get("unitWeight"));assertEquals("269.42",r.get("totalWeight"));}
 @Test void missingConfigurationAndConflictingLegacyCategoryReject(){assertThrows(RuntimeException.class,()->service("铂",null).calculate(new CalculationRequest(null,Map.of("innerDiameter","0","outerDiameter","2","length","1"),2,2L,"杆")));assertThrows(RuntimeException.class,()->service("铂","21.45").calculate(new CalculationRequest("铱杆实心",Map.of("innerDiameter","0","outerDiameter","2","length","1"),2,2L,"杆")));}
 @Test void legacyNewCalculationsAndTinyDimensionsReject(){assertThrows(RuntimeException.class,()->service("铱","22.65").calculate(new CalculationRequest("铱杆实心",Map.of("diameter","2","length","1"),1)));assertThrows(RuntimeException.class,()->service("铱","22.65").calculate(new CalculationRequest(null,Map.of("innerDiameter","0","outerDiameter","0.004","length","1"),1,1L,"杆")));}
}
