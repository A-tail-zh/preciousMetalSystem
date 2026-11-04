package com.ruoyi.metal.service;
import java.util.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class SemiReturnIntegrationTest {
 @Test void returnTypeIsSupported()throws Exception{var method=WarehouseService.class.getDeclaredMethod("type",String.class);method.setAccessible(true);assertDoesNotThrow(()->method.invoke(new WarehouseService(null,null),"SEMI_RETURN"));}
 @Test void rawReturnAllowanceMustSubtractSemiReturns(){assertTrue(RawReturnAllowance.totalsSql(false).contains("SEMI_RETURN"));}
 @Test void consumptionExcludesReturnedSemis(){var row=new HashMap<String,Object>();row.put("status","OPEN");row.put("semiIssuedWeight","100");row.put("semiReturnedWeight","40");row.put("outputWeight","58");var result=ProcessConsumptionService.calculateBatch(row);assertEquals("60.000000",result.get("netInputWeight"));assertEquals("2.000000",result.get("provisionalDifferenceWeight"));}
}
