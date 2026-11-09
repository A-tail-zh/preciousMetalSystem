package com.ruoyi.metal.service;
import com.ruoyi.metal.security.FactoryScope;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
/** Business calculation tests use one valid factory; real authorization is tested separately. */
final class FactoryCatalogTestFixtures {
 static org.springframework.jdbc.core.JdbcTemplate productTypes(org.springframework.jdbc.core.JdbcTemplate jdbc){
  when(jdbc.queryForList(eq("SELECT name FROM metal_product_type WHERE factory_id=? AND name=? FOR UPDATE"),eq(String.class),anyLong(),anyString())).thenAnswer(i->ProductTypes.ALL.contains(i.getArgument(3,String.class))?java.util.List.of(i.getArgument(3,String.class)):java.util.List.of());return jdbc;
 }
 static FactoryScope scope(){var scope=mock(FactoryScope.class);when(scope.resolveFactory(any())).thenAnswer(i->i.getArgument(0)==null?7L:i.getArgument(0));when(scope.catalogFactory(anyString(),any())).thenReturn(7L);return scope;}
}
