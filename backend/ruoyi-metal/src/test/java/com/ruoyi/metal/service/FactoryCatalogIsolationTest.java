package com.ruoyi.metal.service;

import java.util.Map;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.metal.catalog.*;
import com.ruoyi.metal.mapper.*;
import com.ruoyi.metal.security.FactoryScope;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FactoryCatalogIsolationTest {
    private FactoryScope scope() {
        var scope=mock(FactoryScope.class);
        when(scope.currentFactory()).thenReturn(11L);
        return scope;
    }
    @Test void publicProductCannotBeOpenedAsCurrentFactoryCatalog() {
        var mapper=mock(ProductMapper.class);
        var row=new ProductRecord();row.setId(7L);row.setCalculationVersion(2);row.setProductType("环");
        row.setDimensions(Map.of("outerDiameter","3","innerDiameter","1","thickness","1"));
        when(mapper.detail(7L)).thenReturn(row);
        var service=new ProductService(mapper,scope(),mock(JdbcTemplate.class),mock(QuoteRuleService.class));
        assertThrows(ServiceException.class,()->service.detail(7L));
    }
    @Test void publicMaterialCannotBeOpenedAsCurrentFactoryCatalog() {
        var mapper=mock(MaterialMapper.class);var row=new MaterialRecord();row.setId(8L);
        when(mapper.detail(8L)).thenReturn(row);
        assertThrows(ServiceException.class,()->new MaterialService(mapper,scope(),mock(JdbcTemplate.class)).detail(8L));
    }
    @Test void publicProcessCannotBeOpenedAsCurrentFactoryCatalog() {
        var mapper=mock(ProcessMapper.class);var row=new ProcessRecord();row.setId(9L);
        when(mapper.detail(9L)).thenReturn(row);
        assertThrows(ServiceException.class,()->new ProcessService(mapper,scope(),mock(JdbcTemplate.class)).detail(9L));
    }
}
