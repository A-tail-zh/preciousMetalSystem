package com.ruoyi.metal.service;
import java.math.BigDecimal;
import java.util.*;
import com.ruoyi.metal.catalog.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductVersionCompatibilityTest {
    ProductService service(){
        var rules=mock(QuoteRuleService.class);when(rules.metalName(1L)).thenReturn("铱");
        when(rules.volumeRule()).thenReturn(Map.of("piValue","3.14","curvedBottomFactor","1.03","version",0));
        when(rules.rule(1L)).thenReturn(Map.of("density","22.65"));
        return new ProductService(null,null,mock(JdbcTemplate.class),rules);
    }
    ProductRecord legacy(){var r=new ProductRecord();r.setId(42L);r.setCalculationVersion(1);r.setMetalTypeId(1L);r.setProductType("环");r.setCategory("铱环");r.setDimensions(Map.of("outerDiameter","4","innerDiameter","2","height","1.234567"));r.setVolume(new BigDecimal("11.234567"));r.setWeight(new BigDecimal("77.123456"));return r;}
    @Test void legacyNonGeometryEditPreservesAllStoredGeometry(){var old=legacy();var form=legacy();form.setName("新名称");service().prepare(form,old);assertEquals(old.getDimensions(),form.getDimensions());assertEquals(old.getVolume(),form.getVolume());assertEquals(old.getWeight(),form.getWeight());}
    @Test void legacyGeometryCannotBeChangedWithoutExplicitConversion(){var form=legacy();form.setDimensions(Map.of("outerDiameter","5","innerDiameter","2","height","1"));assertThrows(RuntimeException.class,()->service().prepare(form,legacy()));}
    @Test void sameNamedRingRequiresVersionTwoAndNewFields(){var form=legacy();form.setCalculationVersion(2);form.setDimensions(Map.of("outerDiameter","4","innerDiameter","2","thickness","1"));service().prepare(form,legacy());assertEquals(new BigDecimal("9.42"),form.getVolume());assertEquals(2,form.getCalculationVersion());}
    @Test void newCreatesCannotUseLegacyDimensionsOrLegacyTypes(){var form=legacy();form.setId(null);assertThrows(RuntimeException.class,()->service().prepare(form,null));form.setProductType("杆实心");form.setCategory(null);assertThrows(RuntimeException.class,()->service().prepare(form,null));}
    @Test void versionTwoCannotDowngrade(){var old=legacy();old.setCalculationVersion(2);assertThrows(RuntimeException.class,()->service().prepare(legacy(),old));}
    @Test void previewHasNoDatabaseWritesAndMatchesSave(){var dimensions=Map.of("outerDiameter","0.3","innerDiameter","0.1","thickness","0.5");var r=service().preview(new ProductCalculationRequest(1L,"环",dimensions));assertEquals("0.03",r.get("volume"));assertEquals("0.68",r.get("weight"));var p=new ProductRecord();p.setMetalTypeId(1L);p.setProductType("环");p.setDimensions(dimensions);service().prepare(p,null);assertEquals(r.get("weight"),p.getWeight().toPlainString());}
}
