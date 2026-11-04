package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.util.Map;
import com.ruoyi.metal.catalog.ProductRecord;
import com.ruoyi.metal.mapper.ProductMapper;
import com.ruoyi.metal.security.FactoryScope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductWeightTest {
    private ProductService service(String density) {
        var rules=mock(QuoteRuleService.class);
        when(rules.metalName(any())).thenReturn("铱");
        when(rules.pi(any())).thenReturn(new BigDecimal("3.14"));when(rules.volumeRuleForMetal(any())).thenReturn(Map.of("piValue","3.14","curvedBottomFactor","1.03"));
        when(rules.rule(any())).thenReturn(density==null?Map.of():Map.of("density",density));
        return new ProductService(mock(ProductMapper.class),FactoryCatalogTestFixtures.scope(),mock(JdbcTemplate.class),rules);
    }
    private ProductRecord product() {
        var row=new ProductRecord();row.setFactoryId(7L);row.setCategory("铱环");row.setProductType("环");row.setMetalTypeId(1L);row.setCalculationVersion(2);
        row.setDimensions(Map.of("outerDiameter","0.3","innerDiameter","0.1","thickness","0.5"));
        row.setWeight(new BigDecimal("999"));return row;
    }
    @Test void usesRoundedVolumeAndOverridesCallerWeight() {
        var row=product();service("22.65").prepare(row,null);
        assertEquals(new BigDecimal("0.03"),row.getVolume());
        assertEquals(new BigDecimal("0.68"),row.getWeight());
    }
    @Test void densityIsSpecificToSelectedMetalRule() {
        var row=product();service("21.45").prepare(row,null);
        assertEquals(new BigDecimal("0.64"),row.getWeight());
    }
    @Test void missingDensityLeavesWeightEmpty() {
        var row=product();service(null).prepare(row,null);assertNull(row.getWeight());
    }
    @Test void curvedBottomNowHasFormula() {
        var row=product();row.setCategory(null);row.setProductType("弧底");row.setDimensions(ProductGeometryTest.FLAT);
        service("22.65").prepare(row,null);assertEquals(new BigDecimal("21.83"),row.getVolume());
    }
    @Test void editingUsesExistingVolumeWithoutRewritingHistoricalRecord() {
        var old=product();old.setDimensions(ProductGeometry.normalize("环",old.getDimensions()));old.setVolume(new BigDecimal("0.034567"));
        var row=product();service("22.65").prepare(row,old);
        assertEquals(old.getVolume(),row.getVolume());
        assertEquals(new BigDecimal("0.68"),row.getWeight());
        assertEquals(new BigDecimal("999"),old.getWeight());
    }
    @Test void calculatedWeightCannotOverflowStorage() {
        assertThrows(RuntimeException.class,()->service("99999999999999").prepare(productWithLargeVolume(),null));
    }
    private ProductRecord productWithLargeVolume() {
        var row=product();row.setDimensions(Map.of("outerDiameter","3","innerDiameter","1","thickness","1"));return row;
    }
}
