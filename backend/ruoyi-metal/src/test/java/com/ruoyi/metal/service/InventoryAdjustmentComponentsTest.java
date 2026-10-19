package com.ruoyi.metal.service;
import java.math.BigDecimal;
import com.ruoyi.metal.catalog.WarehouseRequests.Adjustment;
import com.ruoyi.common.exception.ServiceException;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class InventoryAdjustmentComponentsTest {
 private BigDecimal n(String value){return value==null?null:new BigDecimal(value);}
 @Test void legacyFingerprintAndConstructorRemainStable(){var old=new Adjustment("fixture",0,1L,2L,n("1"),0L,"reason");assertEquals("Adjustment[requestId=fixture, version=0, operatorId=1, keeperId=2, deltaWeight=1, deltaQuantity=0, reason=reason]",old.toString());assertNull(old.deltaGrossWeight());}
 @Test void optionalFieldsCannotBeForgedByLegacyReasonText(){var legacy=new Adjustment("fixture",0,1L,2L,n("1"),0L,"a, deltaGrossWeight=1, deltaImpurity=0");var components=new Adjustment("fixture",0,1L,2L,n("1"),0L,"a",n("1"),n("0"));assertNotEquals(legacy.toString(),components.toString());}
 @Test void validatesSignedComponentsAndNonzeroComponentOnlyChange(){assertDoesNotThrow(()->WarehouseService.validateAdjustmentComponents("RAW",n("-1.000001"),0,n("-1.000002"),n("-0.000001")));assertDoesNotThrow(()->WarehouseService.validateAdjustmentComponents("RAW",n("0"),0,n("2"),n("2")));assertDoesNotThrow(()->WarehouseService.validateAdjustmentComponents("RAW",n("2"),0,n("0"),n("-2")));}
 @Test void rejectsPartialInconsistentZeroAndProductComponents(){assertThrows(ServiceException.class,()->WarehouseService.validateAdjustmentComponents("RAW",n("1"),0,n("1"),null));assertThrows(ServiceException.class,()->WarehouseService.validateAdjustmentComponents("RAW",n("99"),0,n("100"),n("2")));assertThrows(ServiceException.class,()->WarehouseService.validateAdjustmentComponents("RAW",n("0"),0,n("0"),n("0")));for(String category:new String[]{"SEMI","FINISHED"})assertThrows(ServiceException.class,()->WarehouseService.validateAdjustmentComponents(category,n("1"),0,n("1"),n("0")));}
 @Test void requestPrecisionAllowsNegativesAndRejectsSevenDecimals(){try(var factory=Validation.buildDefaultValidatorFactory()){var v=factory.getValidator();assertTrue(v.validate(new Adjustment("fixture",0,1L,2L,n("-1.000001"),0L,"reason",n("-1.000002"),n("-0.000001"))).isEmpty());assertFalse(v.validate(new Adjustment("fixture",0,1L,2L,n("0"),0L,"reason",n("0.0000001"),n("0.0000001"))).isEmpty());}}
}
