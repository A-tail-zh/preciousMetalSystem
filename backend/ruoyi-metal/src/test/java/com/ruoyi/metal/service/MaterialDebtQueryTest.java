package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.util.*;
import com.ruoyi.metal.security.FactoryScope;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class MaterialDebtQueryTest {
    @Test void orderHistoryStaysScopedAndDoesNotTreatReversalsAsSettlements(){
        JdbcTemplate jdbc=mock(JdbcTemplate.class);FactoryScope scope=mock(FactoryScope.class);
        when(scope.resolveFactory(1L)).thenReturn(1L);
        when(jdbc.queryForObject(anyString(),eq(Long.class),any(Object[].class))).thenReturn(0L);
        var queries=new ArrayList<String>();
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(call->{String sql=call.getArgument(0);queries.add(sql);return sql.startsWith("SELECT id FROM metal_production_order")?List.of(Map.of("id",9L)):List.of();});
        var service=new StorageLedgerService(jdbc,scope);
        service.debts(1L,null,"CUSTOMER_OWES",9L,true,1,16);
        String history=queries.get(queries.size()-1);
        assertTrue(history.contains("d.factory_id=?"));assertTrue(history.contains("d.work_order_id=?"));assertTrue(history.contains("d.creditor_id=0"));assertTrue(history.contains("e.reversed_by IS NULL"));assertFalse(history.contains("d.remaining_weight>0"));
        service.debts(1L,null,"FACTORY_OWES",1,16);
        String open=queries.get(queries.size()-1);assertTrue(open.contains("d.remaining_weight>0"));assertTrue(open.contains("d.debtor_id=0"));
    }
    @Test void returnedBalancesExposePartialAndSettledPieceDebtWithoutMixingActualWeights(){
        var service=new StorageLedgerService(mock(JdbcTemplate.class),mock(FactoryScope.class));
        var row=new HashMap<String,Object>();row.put("identity_snapshot","{}");row.put("original_weight",new BigDecimal("100.00"));row.put("remaining_weight",new BigDecimal("40.00"));row.put("original_quantity",10L);row.put("remaining_quantity",4L);
        var partial=service.debtApi(row);assertEquals("60.00",partial.get("settledWeight"));assertEquals(6L,partial.get("settledQuantity"));assertEquals("OPEN",partial.get("status"));
        row.put("remaining_weight",BigDecimal.ZERO);row.put("remaining_quantity",0L);assertEquals("SETTLED",service.debtApi(row).get("status"));
        row.put("reversed_by",7L);var reversed=service.debtApi(row);assertEquals("REVERSED",reversed.get("status"));assertEquals("0.00",reversed.get("settledWeight"));
    }
}
