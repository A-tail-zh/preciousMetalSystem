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
        service = new MaterialService(mock(MaterialMapper.class), mock(FactoryScope.class), jdbc);
    }

    MaterialRecord material(String type, Long metal) {
        var row = new MaterialRecord();
        row.setId(10L); row.setMaterialType(type); row.setMetalTypeId(metal);
        return row;
    }

    @ParameterizedTest @CsvSource({"铱粉,粉料", "铱块,块料", "旧铱杆,旧料", "旧铱坩埚,旧料"})
    void legacyTypesMapToIridium(String legacy, String type) {
        var row = material(legacy, null);
        service.prepare(row, null);
        assertEquals(type, row.getMaterialType()); assertEquals(1L, row.getMetalTypeId());
    }

    @Test void legacyTypeCannotDescribeAnotherMetal() {
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

    @Test void onlyIridiumCanEnterProduction() {
        assertDoesNotThrow(() -> MaterialTypes.requireProduction(1L));
        assertThrows(ServiceException.class, () -> MaterialTypes.requireProduction(2L));
        assertThrows(ServiceException.class, () -> MaterialTypes.requireProduction(null));
    }
}
