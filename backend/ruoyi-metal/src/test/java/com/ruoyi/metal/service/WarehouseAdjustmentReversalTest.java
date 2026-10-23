package com.ruoyi.metal.service;

import java.lang.reflect.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.metal.security.FactoryScope;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class WarehouseAdjustmentReversalTest {
    private void guard(JdbcTemplate jdbc) throws Throwable {
        var method = SemiFlowService.class.getMethod("guardAdjustmentReversal", long.class);
        try { method.invoke(new SemiFlowService(jdbc, mock(FactoryScope.class)), 7L); }
        catch (InvocationTargetException e) { throw e.getCause(); }
    }

    @Test void activeAutoAndManualRemainDependenciesEvenWhenFullyUsed() {
        for (String mode : List.of("AUTO", "MANUAL")) {
            for (long used : List.of(0L, 2L, 4L)) {
                var jdbc = mock(JdbcTemplate.class);
                when(jdbc.queryForList(anyString(), any(Object[].class))).thenAnswer(a -> {
                    String sql = a.getArgument(0);
                    if (sql.contains("FROM metal_semi_release ")) return List.of(Map.of("release_id", 91L, "release_mode", mode));
                    if (sql.contains("FROM metal_semi_release_use")) return used == 0 ? List.of() : List.of(Map.of("order_no", "OUT-DEPENDENCY"));
                    throw new IllegalStateException(sql);
                });
                var error = assertThrows(ServiceException.class, () -> guard(jdbc), mode + ":" + used);
                assertTrue(error.getMessage().contains("91"));
                assertTrue(error.getMessage().contains("释放"));
                verify(jdbc, never()).update(anyString(), any(Object[].class));
            }
        }
    }

    @Test void cancelledAndAbsentReleasesDoNotBlock() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenAnswer(a -> {
            assertTrue(((String)a.getArgument(0)).contains("status='ACTIVE'"));
            return List.of();
        });
        assertDoesNotThrow(() -> guard(jdbc));
    }

    @Test void errorListsOnlyBoundedReleaseDetails() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenAnswer(a -> {
            String sql = a.getArgument(0);
            if (sql.contains("FROM metal_semi_release ")) {
                assertTrue(sql.contains("LIMIT"));
                return List.of(Map.of("release_id", 91L, "release_mode", "AUTO"));
            }
            if (sql.contains("FROM metal_semi_release_use")) { assertTrue(sql.contains("LIMIT")); return List.of(Map.of("order_no", "OUT-DEPENDENCY")); }
            throw new IllegalStateException(sql);
        });
        var error = assertThrows(ServiceException.class, () -> guard(jdbc));
        assertTrue(error.getMessage().contains("OUT-DEPENDENCY"));
        assertTrue(error.getMessage().contains("下游"));
    }

    private record Fixture(JdbcTemplate jdbc, WarehouseService service, List<Long> locks) {}
    private Fixture fixture(String type, boolean blocked) {
        var jdbc = mock(JdbcTemplate.class);
        var locks = new ArrayList<Long>();
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenAnswer(a -> {
            String sql = a.getArgument(0);
            Object id = a.getArgument(1);
            if (sql.contains("JOIN metal_inventory") && sql.contains("ADJUST"))
                return List.of(Map.of("inventory_id", 8L), Map.of("inventory_id", 7L), Map.of("inventory_id", 7L));
            if (sql.contains("FROM metal_inventory t")) {
                locks.add(((Number)id).longValue());
                assertTrue(sql.endsWith("FOR UPDATE"));
                return List.of(Map.of("inventory_id", id, "factory_id", 1L, "category", "SEMI"));
            }
            if (sql.contains("FROM metal_semi_release "))
                return blocked && ((Number)id).longValue() == 8 ? List.of(Map.of("release_id",91L,"release_mode","AUTO")) : List.of();
            if (sql.contains("FROM metal_semi_release_use")) return List.of();
            return List.of();
        });
        return new Fixture(jdbc, new WarehouseService(jdbc, mock(FactoryScope.class)), locks);
    }
    private void reverse(Fixture f, String type) throws Throwable {
        var method = WarehouseService.class.getDeclaredMethod("reverseDocuments", List.class, String.class);
        method.setAccessible(true);
        try { method.invoke(f.service, List.of(Map.of("order_id",1L,"type",type,"status","POSTED")), "test"); }
        catch (InvocationTargetException e) { throw e.getCause(); }
    }

    @Test void adjustmentPreflightLocksSortedUniqueStocksAndRejectsBeforeAnyWrites() {
        var f = fixture("ADJUST", true);
        assertThrows(ServiceException.class, () -> reverse(f, "ADJUST"));
        assertEquals(List.of(7L, 8L), f.locks);
        verify(f.jdbc, never()).update(anyString(), any(Object[].class));
    }
    @Test void canceledReleasesAllowOriginalReversalChecks() {
        var f = fixture("ADJUST", false);
        assertDoesNotThrow(() -> reverse(f, "ADJUST"));
        assertEquals(List.of(7L, 8L), f.locks);
        verify(f.jdbc).update(contains("status='VOID'"), any(Object[].class));
    }
    @Test void batchDocumentReversalDoesNotUseAdjustmentGuard() {
        for (String type : List.of("PRODUCTION_OUT", "SEMI_RETURN", "SEMI_IN", "FINISHED_IN")) {
            var f = fixture(type, true);
            assertDoesNotThrow(() -> reverse(f, type));
            assertTrue(f.locks.isEmpty());
            verify(f.jdbc, never()).queryForList(contains("FROM metal_semi_release "), any(Object[].class));
        }
    }
}
