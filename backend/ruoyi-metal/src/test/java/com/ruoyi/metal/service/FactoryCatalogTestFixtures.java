package com.ruoyi.metal.service;
import com.ruoyi.metal.security.FactoryScope;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
/** Business calculation tests use one valid factory; real authorization is tested separately. */
final class FactoryCatalogTestFixtures {
 static FactoryScope scope(){var scope=mock(FactoryScope.class);when(scope.resolveFactory(any())).thenAnswer(i->i.getArgument(0)==null?7L:i.getArgument(0));when(scope.catalogFactory(anyString(),any())).thenReturn(7L);return scope;}
}
