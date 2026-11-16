package com.ruoyi.metal.service;

import java.util.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import com.ruoyi.metal.catalog.CalculationRequest;
import static org.junit.jupiter.api.Assertions.*;

class QuotationPricingTest {
    private final Map<String,Object> prices=new HashMap<>(Map.of("density","22.65","powderPrice","2.00","nonPowderPrice","3.00","shortagePrice","4.00","lossPrice","7.00"));
    private QuoteRuleService service(){return new QuoteRuleService(null,FactoryCatalogTestFixtures.scope()){
        @Override public String metalName(Long id){return id==2L?"铂":"铱";}
        @Override public Map<String,Object> volumeRuleForMetal(Long id){return Map.of("piValue","3.14","curvedBottomFactor","1.03","version",3);}
        @Override public Map<String,Object> rule(Long id){if(id==2L){var other=new HashMap<>(prices);other.put("shortagePrice","5.00");other.put("density","21.45");return other;}return prices;}
    };}
    private CalculationRequest input(String type,String powder,String nonPowder,String loss,int quantity){
        return new CalculationRequest(null,Map.of("innerDiameter","0","outerDiameter","2","length","1"),quantity,1L,"杆",type,powder,nonPowder,loss);
    }
    @Test void materialAndDirectUseAggregateWeightsAndTheirOwnPrices(){
        for(String type:List.of("MATERIAL_CUSTOM","DIRECT_CUSTOM"))for(int quantity:List.of(1,2,99)){
            var result=service().calculate(input(type,"10","5","1",quantity));
            assertEquals("42.00",result.get("totalPrice"));assertEquals("20.00",result.get("powderAmount"));assertEquals("15.00",result.get("nonPowderAmount"));assertEquals("7.00",result.get("lossAmount"));
            assertEquals("10.00",result.get("powderWeight"));assertEquals("0.00",result.get("productAmount"));assertEquals(true,result.get("pricingEnabled"));assertFalse(result.containsKey("unitPrice"));
            assertEquals(type.equals("MATERIAL_CUSTOM")?"来料定制":"直接定制",result.get("processingTypeName"));
        }
    }
    @Test void shortageUsesTotalTheoreticalWeightAndShortagePriceForLoss(){
        prices.remove("lossPrice");prices.remove("powderPrice");prices.remove("nonPowderPrice");
        var result=service().calculate(input("SHORTAGE_CUSTOM",null,null,"1",2));
        assertEquals("284.48",result.get("totalWeight"));assertEquals("1137.92",result.get("productAmount"));assertEquals("4.00",result.get("lossAmount"));assertEquals("1141.92",result.get("totalPrice"));assertFalse(result.containsKey("powderWeight"));
        assertEquals(3,((Map<?,?>)result.get("volumeRule")).get("version"));
    }
    @Test void selectedMetalControlsDensityAndPrices(){
        var original=input("SHORTAGE_CUSTOM",null,null,"1",2);
        var other=new CalculationRequest(null,original.dimensions(),2,2L,"杆",original.processingType(),null,null,"1");
        var result=service().calculate(other);assertEquals("269.42",result.get("totalWeight"));assertEquals("1352.10",result.get("totalPrice"));assertEquals("铂",result.get("metalTypeName"));
    }
    @Test void roundsWeightsThenEachComponentBeforeSumming(){
        prices.put("powderPrice","0.50");prices.put("nonPowderPrice","0.50");prices.put("lossPrice","0.50");
        var result=service().calculate(input("MATERIAL_CUSTOM","0.005","0.005","0.005",2));
        assertEquals("0.01",result.get("powderWeight"));assertEquals("0.01",result.get("powderAmount"));assertEquals("0.03",result.get("totalPrice"));
    }
    @Test void explicitZeroWeightsAndPricesAreValid(){
        assertEquals("0.00",service().calculate(input("DIRECT_CUSTOM","0","0","0",2)).get("totalPrice"));
        for(String key:List.of("powderPrice","nonPowderPrice","shortagePrice","lossPrice"))prices.put(key,"0");
        assertEquals("0.00",service().calculate(input("DIRECT_CUSTOM","10","5","1",2)).get("totalPrice"));
        assertEquals("0.00",service().calculate(input("SHORTAGE_CUSTOM",null,null,"1",2)).get("totalPrice"));
    }
    @Test void missingOrInvalidWeightsAndTypesAreRejected(){
        for(String invalid:Arrays.asList(null,"","-1","-0.004","1e2","NaN","Infinity","1.1234567","100000000000000"," 1")){
            assertThrows(RuntimeException.class,()->service().calculate(input("MATERIAL_CUSTOM",invalid,"0","0",2)));
            assertThrows(RuntimeException.class,()->service().calculate(input("DIRECT_CUSTOM","0",invalid,"0",2)));
            assertThrows(RuntimeException.class,()->service().calculate(input("SHORTAGE_CUSTOM",null,null,invalid,2)));
        }
        for(String invalid:List.of("","UNKNOWN"))assertThrows(RuntimeException.class,()->service().calculate(input(invalid,"0","0","0",2)));
        assertThrows(RuntimeException.class,()->service().calculate(input(null,null,null,"0",2)));
        assertThrows(RuntimeException.class,()->service().calculate(input("SHORTAGE_CUSTOM","0",null,"0",2)));
        assertThrows(RuntimeException.class,()->service().calculate(input("SHORTAGE_CUSTOM",null,"0","0",2)));
    }
    @Test void missingRequiredPricesRejectEvenWhenWeightIsZero(){
        for(String key:List.of("powderPrice","nonPowderPrice","lossPrice")){
            Object old=prices.remove(key);assertThrows(RuntimeException.class,()->service().calculate(input("MATERIAL_CUSTOM","0","0","0",2)));prices.put(key,old);
        }
        prices.remove("shortagePrice");assertThrows(RuntimeException.class,()->service().calculate(input("SHORTAGE_CUSTOM",null,null,"0",2)));
    }
    @Test void overflowAndInvalidConfigurationAreRejected(){
        assertThrows(RuntimeException.class,()->service().calculate(input("MATERIAL_CUSTOM","99999999999999.995","0","0",2)));
        prices.put("powderPrice","99999999999999.99");assertThrows(RuntimeException.class,()->service().calculate(input("MATERIAL_CUSTOM","2","0","0",2)));
        prices.put("powderPrice","-0.004");assertThrows(RuntimeException.class,()->service().calculate(input("MATERIAL_CUSTOM","0","0","0",2)));
        prices.put("density","0");assertThrows(RuntimeException.class,()->service().calculate(input("SHORTAGE_CUSTOM",null,null,"0",2)));
    }
    @Test void sumOverflowIsRejectedEvenWhenComponentsFit(){
        prices.put("powderPrice","1");prices.put("nonPowderPrice","1");prices.put("lossPrice","0");
        assertThrows(RuntimeException.class,()->service().calculate(input("MATERIAL_CUSTOM","60000000000000","60000000000000","0",2)));
    }
    @Test void oldConstructorsKeepWeightOnlyBehavior(){
        var request=input(null,null,null,null,2);
        var result=service().calculate(new CalculationRequest(null,request.dimensions(),2,1L,"杆"));
        assertEquals(false,result.get("pricingEnabled"));assertFalse(result.containsKey("totalPrice"));assertEquals("284.48",result.get("totalWeight"));
        assertNull(new CalculationRequest("铱环",Map.of(),1).processingType());
    }
    @Test void dtoRejectsScientificNotationAndUnknownTypes(){
        try(var factory=Validation.buildDefaultValidatorFactory()){
            var validator=factory.getValidator();
            assertTrue(validator.validate(input("MATERIAL_CUSTOM","10","5","1",2)).isEmpty());
            assertTrue(validator.validate(input(null,null,null,null,2)).isEmpty());
            for(String invalid:List.of("-1","1e2","1.1234567","NaN"))assertFalse(validator.validate(input("MATERIAL_CUSTOM",invalid,"0","0",2)).isEmpty());
            assertFalse(validator.validate(input("BAD","0","0","0",2)).isEmpty());
        }
    }
}
