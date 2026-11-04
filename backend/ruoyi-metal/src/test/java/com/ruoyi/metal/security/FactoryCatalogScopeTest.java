package com.ruoyi.metal.security;

import java.util.*;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.core.redis.RedisCache;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FactoryCatalogScopeTest {
    private FactoryScope scope(JdbcTemplate jdbc,Long own){
        var s=spy(new FactoryScope(jdbc,mock(RedisCache.class)));doReturn(own).when(s).currentFactory();
        when(jdbc.queryForObject(anyString(),eq(Integer.class),any())).thenReturn(1);return s;
    }
    @Test void normalAccountCannotChooseAnotherFactory(){var j=mock(JdbcTemplate.class);var s=scope(j,11L);assertEquals(11L,s.resolveFactory(null));assertThrows(ServiceException.class,()->s.resolveFactory(22L));}
    @Test void administratorMustChooseFactory(){var s=scope(mock(JdbcTemplate.class),null);assertThrows(ServiceException.class,()->s.resolveFactory(null));assertEquals(22L,s.resolveFactory(22L));}
    @Test void anotherFactoryAndLegacyCatalogAreRejected(){
        var j=mock(JdbcTemplate.class);var s=scope(j,11L);
        when(j.queryForList(anyString(),eq(7L))).thenReturn(List.of(Map.of("factory_id",22L)));
        when(j.queryForList(anyString(),eq(8L))).thenReturn(List.of(Collections.singletonMap("factory_id",null)));
        assertThrows(ServiceException.class,()->s.requireCatalog("metal",7L,11L));
        assertThrows(ServiceException.class,()->s.requireCatalog("metal",8L,11L));
    }
    @Test void administratorStillCannotBindDifferentFactories(){var j=mock(JdbcTemplate.class);var s=scope(j,null);when(j.queryForList(anyString(),eq(7L))).thenReturn(List.of(Map.of("factory_id",22L)));assertThrows(ServiceException.class,()->s.requireCatalog("metal",7L,11L));}
    @Test void ownCatalogCanBeBound(){var j=mock(JdbcTemplate.class);var s=scope(j,11L);when(j.queryForList(anyString(),eq(7L))).thenReturn(List.of(Map.of("factory_id",11L)));assertDoesNotThrow(()->s.requireCatalog("metal",7L,11L));}
    @Test void resourceNameCannotBecomeSql(){var j=mock(JdbcTemplate.class);var s=scope(j,11L);assertThrows(IllegalArgumentException.class,()->s.catalogFactory("metal_type WHERE 1=1",7L));}
}
