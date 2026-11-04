package com.ruoyi.metal.service;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.ruoyi.common.exception.ServiceException;
import static org.junit.jupiter.api.Assertions.*;
class RawInventoryOwnershipTest {
 @Test void trimsNamesWithoutInventingUnknownSupplier(){assertEquals("甲方公司",WarehouseService.normalizeOrganization("  甲方公司  "));assertEquals("",WarehouseService.normalizeOrganization(null));assertEquals("",WarehouseService.normalizeOrganization("  "));}
 @Test void mixedRawStockCannotBeIssuedOrAdjusted(){assertThrows(ServiceException.class,()->WarehouseService.validateOwnership(Map.of("category","RAW","ownership_status","REVIEW")));}
 @Test void normalAndNonRawStockRemainUsable(){assertDoesNotThrow(()->WarehouseService.validateOwnership(Map.of("category","RAW","ownership_status","NORMAL")));assertDoesNotThrow(()->WarehouseService.validateOwnership(Map.of("category","SEMI","ownership_status","NORMAL")));}
}
