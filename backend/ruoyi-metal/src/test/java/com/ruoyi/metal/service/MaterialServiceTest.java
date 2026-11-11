package com.ruoyi.metal.service;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.metal.catalog.MaterialRecord;
import com.ruoyi.metal.mapper.MaterialMapper;
import com.ruoyi.metal.security.FactoryScope;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MaterialServiceTest {
    JdbcTemplate jdbc;
    MaterialService service;

    @BeforeEach void setup() {
        jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(contains("FROM metal_type WHERE"), any(Object[].class)))
                .thenReturn(List.of(Map.of("metal_type_id", 1L)));
        when(jdbc.queryForList(contains("FROM metal_material_type"), eq(String.class), eq(7L), anyString())).thenAnswer(i->List.of("粉料","块料","旧料").contains(i.getArgument(3,String.class))?List.of(i.getArgument(3,String.class)):List.of());
        service = new MaterialService(mock(MaterialMapper.class), FactoryCatalogTestFixtures.scope(), jdbc, mock(StorageConfigurationService.class));
    }

    MaterialRecord material(String type, Long metal) {
        var row = new MaterialRecord();
        row.setFactoryId(7L);
        row.setStorageConfiguration(Map.of("form","UNTREATED","measurementMode","WEIGHT"));
        row.setId(10L); row.setMaterialType(type); row.setMetalTypeId(metal);
        return row;
    }

    @ParameterizedTest @CsvSource({"铱粉,粉料", "铱块,块料", "旧铱杆,旧料", "旧铱坩埚,旧料"})
    void unregisteredLegacyNamesCannotCreateNewBusiness(String legacy, String type) {
        var row = material(legacy, null);
        assertThrows(ServiceException.class,()->service.prepare(row,null));
    }

    @Test void unregisteredNameIsRejectedForAnyMetal() {
        assertThrows(ServiceException.class, () -> service.prepare(material("铱粉", 2L), null));
    }

    @Test void canonicalTypesRequireAValidMetal() {
        for (Long metal : new Long[]{null, 0L, -1L})
            assertThrows(ServiceException.class, () -> service.prepare(material("粉料", metal), null));
        when(jdbc.queryForList(contains("FROM metal_type WHERE"), any(Object[].class))).thenReturn(List.of());
        assertThrows(ServiceException.class, () -> service.prepare(material("粉料", 99L), null));
    }

    @Test void invalidTypesAreRejected() {
        assertThrows(ServiceException.class, () -> service.prepare(material("未知", 1L), null));
        assertThrows(ServiceException.class, () -> service.prepare(material(null, 1L), null));
    }

    @Test void unreferencedClassificationCanChange() {
        assertDoesNotThrow(() -> service.prepare(material("块料", 2L), material("粉料", 1L)));
    }

    @Test void inventoryIncludingArchivedOrZeroBalanceLocksClassification() {
        when(jdbc.queryForList(contains("FROM metal_inventory"), any(Object[].class)))
                .thenReturn(List.of(Map.of("inventory_id", 20L)));
        assertThrows(ServiceException.class, () -> service.prepare(material("块料", 1L), material("粉料", 1L)));
        assertThrows(ServiceException.class, () -> service.prepare(material("粉料", 2L), material("粉料", 1L)));
        assertDoesNotThrow(() -> service.prepare(material("粉料", 1L), material("粉料", 1L)));
    }

    @Test void historicalWarehouseLinesAlsoLockClassificationAndDeletion() {
        when(jdbc.queryForList(contains("FROM metal_warehouse_order_line"), any(Object[].class)))
                .thenReturn(List.of(Map.of("line_id", 30L)));
        assertThrows(ServiceException.class, () -> service.prepare(material("旧料", 1L), material("粉料", 1L)));
        assertThrows(ServiceException.class, () -> service.deletable(material("粉料", 1L)));
    }

    @Test void registeredCustomAndLongNamesAreSavedExactly() {
        for(String type:List.of("颗粒料","料".repeat(100),"铱粉")){
            when(jdbc.queryForList(contains("FROM metal_material_type"),eq(String.class),eq(7L),eq(type))).thenReturn(List.of(type));
            var row=material(type,1L);assertDoesNotThrow(()->service.prepare(row,null));assertEquals(type,row.getMaterialType());
        }
        assertThrows(ServiceException.class,()->service.prepare(material("料".repeat(101),1L),null));
    }
    @Test void historicalUnregisteredNameCanOnlyBeRetainedOnItsOriginalRecord() {
        assertDoesNotThrow(()->service.prepare(material("旧目录外名称",1L),material("旧目录外名称",1L)));
        assertThrows(ServiceException.class,()->service.prepare(material("旧目录外名称",1L),null));
        assertThrows(ServiceException.class,()->service.prepare(material("另一个旧名称",1L),material("旧目录外名称",1L)));
    }
    @Test void directoryQueryUsesFactoryAndCurrentReadAndSavesCanonicalName() {
        when(jdbc.queryForList("SELECT name FROM metal_material_type WHERE factory_id=? AND name=? FOR UPDATE",String.class,7L,"abc")).thenReturn(List.of("ABC"));
        var row=material("abc",1L);service.prepare(row,null);assertEquals("ABC",row.getMaterialType());
        verify(jdbc).queryForList("SELECT name FROM metal_material_type WHERE factory_id=? AND name=? FOR UPDATE",String.class,7L,"abc");
    }
    @Test void onlyIridiumCanEnterProduction() {
        assertDoesNotThrow(() -> MaterialTypes.requireProduction(1L));
        assertThrows(ServiceException.class, () -> MaterialTypes.requireProduction(2L));
        assertThrows(ServiceException.class, () -> MaterialTypes.requireProduction(null));
    }
}
