package com.ruoyi.metal.service;

import java.util.*;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.metal.catalog.MaterialRecord;
import com.ruoyi.metal.mapper.MaterialMapper;
import com.ruoyi.metal.security.FactoryScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MaterialConfigurationPersistenceTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final MaterialMapper mapper=mock(MaterialMapper.class);
    private final FactoryScope scope=mock(FactoryScope.class);
    private final MaterialService service=new MaterialService(mapper,scope,jdbc,new StorageConfigurationService(jdbc,scope));

    MaterialConfigurationPersistenceTest() {
        when(jdbc.queryForObject(anyString(),eq(Integer.class),any(Object[].class))).thenReturn(0);
    }

    private MaterialRecord form() {
        when(scope.resolveFactory(7L)).thenReturn(7L);
        when(jdbc.queryForList(contains("FROM metal_material_type"),eq(String.class),eq(7L),anyString())).thenReturn(List.of("粉料"));
        when(jdbc.queryForList(contains("FROM metal_type WHERE"),eq(1L))).thenReturn(List.of(Map.of("metal_type_id",1L)));
        var row=new MaterialRecord();row.setFactoryId(7L);row.setCode("M1");row.setMaterialType("粉料");row.setMetalTypeId(1L);
        return row;
    }

    private void existing(MaterialRecord row,String shape,String mode,int stocks) {
        row.setId(21L);row.setVersion(0);
        when(mapper.detail(21L)).thenReturn(row);when(mapper.lock(21L)).thenReturn(row);when(mapper.update(any())).thenReturn(1);
        var saved=new HashMap<String,Object>();saved.put("material_form",shape);saved.put("measurement_mode",mode);
        when(jdbc.queryForList(startsWith("SELECT * FROM metal_material"),eq(21L),eq(7L))).thenReturn(List.of(saved));
        when(jdbc.queryForObject(contains("FROM metal_storage_stock"),eq(Integer.class),eq(21L))).thenReturn(stocks);
    }

    @ParameterizedTest @ValueSource(strings={"POWDER:WEIGHT","METAL:WEIGHT","METAL:PIECE","UNTREATED:WEIGHT","UNTREATED:PIECE"})
    void createPersistsExplicitConfigurationDespiteMaterialName(String value) {
        var parts=value.split(":");var row=form();row.setStorageConfiguration(Map.of("form",parts[0],"measurementMode",parts[1]));
        doAnswer(i->{row.setId(21L);return 1;}).when(mapper).insert(row);
        try(var security=mockStatic(SecurityUtils.class)){security.when(SecurityUtils::getUsername).thenReturn("tester");assertEquals("21",service.create(row));}
        verify(jdbc).update("UPDATE metal_material SET measurement_mode=?,material_form=? WHERE material_id=?",parts[1],parts[0],21L);
    }

    @Test void powderPieceIsRejectedBeforeInsert() {
        var row=form();row.setStorageConfiguration(Map.of("form","POWDER","measurementMode","PIECE"));
        assertThrows(ServiceException.class,()->service.create(row));verify(mapper,never()).insert(any());
    }

    @Test void newMaterialRequiresExplicitConfiguration() {
        var row=form();assertThrows(ServiceException.class,()->service.create(row));verify(mapper,never()).insert(any());
    }

    @Test void missingLegacyConfigurationIsReturnedWithoutGuessingFromName() {
        var row=form();existing(row,null,null,0);
        var configuration=service.detail(21L).getStorageConfiguration();
        assertNull(configuration.get("form"));assertNull(configuration.get("measurementMode"));
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }

    @Test void manualLegacyConfigurationIsSaved() {
        var old=form();existing(old,null,null,0);
        var row=form();row.setId(21L);row.setVersion(0);row.setStorageConfiguration(Map.of("form","METAL","measurementMode","PIECE"));
        try(var security=mockStatic(SecurityUtils.class)){service.edit(row);}
        verify(jdbc).update("UPDATE metal_material SET measurement_mode=?,material_form=? WHERE material_id=?","PIECE","METAL",21L);
    }

    @Test void omittedConfigurationPreservesLegacyValues() {
        var old=form();existing(old,"METAL","WEIGHT",0);
        var row=form();row.setId(21L);row.setVersion(0);
        try(var security=mockStatic(SecurityUtils.class)){service.edit(row);}
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }

    @Test void stockedMaterialConfigurationCannotBeChanged() {
        var old=form();existing(old,"METAL","WEIGHT",1);
        var row=form();row.setId(21L);row.setVersion(0);row.setStorageConfiguration(Map.of("form","UNTREATED","measurementMode","WEIGHT"));
        assertEquals(409,assertThrows(ServiceException.class,()->service.edit(row)).getCode());verify(mapper,never()).update(any());
    }
}
