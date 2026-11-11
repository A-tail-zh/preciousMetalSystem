package com.ruoyi.metal.service;

import java.util.*;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.metal.catalog.ProcessRecord;
import com.ruoyi.metal.mapper.ProcessMapper;
import com.ruoyi.metal.security.FactoryScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProcessRulePersistenceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ProcessMapper mapper = mock(ProcessMapper.class);
    private final FactoryScope scope = mock(FactoryScope.class);
    private final StorageConfigurationService storage = new StorageConfigurationService(jdbc, scope);
    private final ProcessService service = new ProcessService(mapper, scope, jdbc, storage);

    private ProcessRecord form(String semantic) {
        when(scope.resolveFactory(7L)).thenReturn(7L);
        when(jdbc.queryForObject(anyString(),eq(Integer.class),any(Object[].class))).thenReturn(0);
        when(jdbc.queryForObject(contains("metal_process_type"),eq(Integer.class),eq(12))).thenReturn(1);
        var type = new HashMap<String,Object>();
        type.put("builtin_code",semantic.equals("NORMAL")?null:semantic);
        when(jdbc.queryForList(contains("SELECT builtin_code"),eq(12),eq(7L))).thenReturn(List.of(type));
        var row = new ProcessRecord(); row.setFactoryId(7L);row.setProcessType(12);row.setName("test");
        return row;
    }

    @ParameterizedTest @ValueSource(strings={"ACID_WASH","SMELTING","NORMAL"})
    void createDerivesAndPersistsRuleWithoutClientConfiguration(String semantic) {
        var row = form(semantic);
        doAnswer(i->{row.setId(21L);return 1;}).when(mapper).insert(row);
        try(var security=mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUsername).thenReturn("tester");
            assertEquals("21",service.create(row));
        }
        assertEquals(semantic,row.getStorageConfiguration().get("semanticCode"));
        verify(jdbc).update("UPDATE metal_process SET semantic_code=? WHERE process_id=?",semantic,21L);
    }

    @ParameterizedTest @ValueSource(strings={"ACID_WASH","SMELTING"})
    void clientCannotOverrideFixedBuiltinRule(String semantic) {
        var row=form(semantic);row.setStorageConfiguration(Map.of("semanticCode","NORMAL"));
        assertEquals(409,assertThrows(ServiceException.class,()->service.create(row)).getCode());
        verify(mapper,never()).insert(any());
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }

    @Test void editPersistsRuleAndConfigurationReturnsIt() {
        var row=form("ACID_WASH");row.setId(21L);row.setVersion(0);
        when(mapper.detail(21L)).thenReturn(row);when(mapper.lock(21L)).thenReturn(row);when(mapper.update(row)).thenReturn(1);
        when(jdbc.queryForList(startsWith("SELECT * FROM metal_process"),eq(21L),eq(7L))).thenReturn(List.of(Map.of("semantic_code","ACID_WASH")));
        when(jdbc.queryForObject(anyString(),eq(Integer.class),eq(21L))).thenReturn(0);
        try(var security=mockStatic(SecurityUtils.class)){service.edit(row);}
        verify(jdbc).update("UPDATE metal_process SET semantic_code=? WHERE process_id=?","ACID_WASH",21L);
        assertEquals("ACID_WASH",storage.catalogDetail("process",21L,7L).get("semanticCode"));
    }

    @Test void productionReferencePreventsChangingRule() {
        var row=form("ACID_WASH");row.setId(21L);row.setVersion(0);
        when(mapper.detail(21L)).thenReturn(row);when(mapper.lock(21L)).thenReturn(row);
        when(jdbc.queryForList(startsWith("SELECT * FROM metal_process"),eq(21L),eq(7L))).thenReturn(List.of(Map.of("semantic_code","NORMAL")));
        when(jdbc.queryForObject(startsWith("SELECT COUNT(*) FROM metal_production_order_step"),eq(Integer.class),eq(21L))).thenReturn(1);
        assertEquals(409,assertThrows(ServiceException.class,()->service.edit(row)).getCode());
        verify(mapper,never()).update(any());
    }

    @Test void compatibilityEndpointCannotOverrideBuiltinRule() {
        form("ACID_WASH");when(scope.isAdmin()).thenReturn(true);
        when(jdbc.queryForList(startsWith("SELECT * FROM metal_process"),eq(21L),eq(7L))).thenReturn(List.of(Map.of("semantic_code","ACID_WASH","process_type",12,"version",0)));
        assertEquals(409,assertThrows(ServiceException.class,()->storage.save("process",21L,Map.of("factoryId",7L,"version",0,"semanticCode","SMELTING"))).getCode());
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }

    @Test void migrationBlocksBeforeWritingWhenExistingRuleConflicts() {
        when(jdbc.queryForObject(anyString(),eq(Integer.class))).thenReturn(0,1);
        assertThrows(IllegalStateException.class,()->ProcessRuleMigration.migrate(jdbc));
        verify(jdbc,never()).update(anyString());
    }
}
