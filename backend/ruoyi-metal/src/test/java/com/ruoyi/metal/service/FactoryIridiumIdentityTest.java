package com.ruoyi.metal.service;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FactoryIridiumIdentityTest {
    private Map<String,Object> powder(long id,String name){
        var stock=new HashMap<String,Object>();stock.put("category","RAW");stock.put("material_type","粉料");
        stock.put("metal_type_id_snapshot",id);stock.put("metal_type_name_snapshot",name);
        stock.put("powder_origin","NATIVE");stock.put("washing_status","EXEMPT");return stock;
    }
    @Test void factoryIridiumDoesNotDependOnGlobalPrimaryKey(){assertDoesNotThrow(()->RawOriginService.requireSmelting(powder(91,"铱"),91));}
    @Test void metalNameCannotBeOverriddenByLegacyPrimaryKey(){assertThrows(RuntimeException.class,()->RawOriginService.requireSmelting(powder(1,"铂"),1));}
}
