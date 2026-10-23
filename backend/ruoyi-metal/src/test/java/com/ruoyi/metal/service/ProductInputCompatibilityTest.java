package com.ruoyi.metal.service;
import com.ruoyi.metal.catalog.*;import com.ruoyi.metal.mapper.ProductMapper;import com.ruoyi.metal.security.FactoryScope;import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;import java.util.*;import java.math.BigDecimal;import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
class ProductInputCompatibilityTest {
 private ProductRecord product(String diameter,String length){var p=new ProductRecord();p.setCategory("铱杆实心");p.setDimensions(Map.of("diameter",diameter,"length",length));return p;}
 private ProductService service(){var rules=mock(QuoteRuleService.class);var rule=new QuoteRule();rule.setPiValue(new BigDecimal("3.14"));when(rules.get()).thenReturn(rule);return new ProductService(mock(ProductMapper.class),mock(FactoryScope.class),mock(JdbcTemplate.class),rules);}
 @Test void newScientificDimensionsAreRejected(){assertThrows(RuntimeException.class,()->service().prepare(product("1e1","20"),null));}
 @Test void unchangedLegacyFormatIsAcceptedUsingServerOldRecord(){assertDoesNotThrow(()->service().prepare(product("1e1","21"),product("1e1","20")));}
 @Test void changedLegacyFormatIsValidatedAgain(){assertThrows(RuntimeException.class,()->service().prepare(product("2e1","20"),product("1e1","20")));}
 @Test void unsafeUnchangedValuesAreNeverGrandfathered(){assertThrows(RuntimeException.class,()->service().prepare(product("0","20"),product("0","20")));}
}
