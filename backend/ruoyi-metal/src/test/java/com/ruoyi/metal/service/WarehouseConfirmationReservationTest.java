package com.ruoyi.metal.service;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.util.*;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.metal.security.FactoryScope;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WarehouseConfirmationReservationTest {
    private void confirmReservations(int marker, long heldPieces) throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenAnswer(call -> {
            String sql = call.getArgument(0);
            if (sql.startsWith("SELECT DISTINCT inventory_id")) return List.of(Map.of("inventory_id", 1L));
            if (sql.startsWith("SELECT * FROM metal_inventory"))
                return List.of(Map.of("inventory_id", 1L, "category", "RAW", "measurement_mode", "PIECE", "net_weight", new BigDecimal("100"), "quantity", 5L));
            if (sql.startsWith("SELECT inventory_id,origin_batch_id")) return List.of(Map.of("inventory_id", 1L, "origin_batch_id", 2L));
            if (sql.startsWith("SELECT b.batch_id,b.measurement_mode"))
                return List.of(Map.of("batch_id", 2L, "measurement_mode", "PIECE", "product_snapshot", "{\"measurementPolicyVersion\":" + marker + "}"));
            if (sql.contains("SUM(weight)")) return List.of(Map.of("weight", new BigDecimal("80"), "quantity", heldPieces));
            if (sql.startsWith("SELECT DISTINCT item_id") || sql.startsWith("SELECT DISTINCT source_line_id")) return List.of();
            throw new AssertionError(sql);
        });
        var service = new WarehouseWorkflowService(jdbc, mock(FactoryScope.class), mock(WarehouseService.class), mock(WarehouseImportService.class), mock(OutboundDraftService.class));
        var method = WarehouseWorkflowService.class.getDeclaredMethod("reserveActual", long.class);
        method.setAccessible(true);
        try { method.invoke(service, 3L); }
        catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception cause) throw cause;
            throw e;
        }
    }

    @Test void configurableRawCannotConfirmMoreReservedPiecesThanPhysicalStock() {
        assertThrows(ServiceException.class, () -> confirmReservations(3, 6));
    }

    @Test void configurableRawAcceptsTheExactPieceBalance() {
        assertDoesNotThrow(() -> confirmReservations(3, 5));
    }

    @Test void historicalRawKeepsItsWeightOnlyContract() {
        assertDoesNotThrow(() -> confirmReservations(2, 6));
    }
}
