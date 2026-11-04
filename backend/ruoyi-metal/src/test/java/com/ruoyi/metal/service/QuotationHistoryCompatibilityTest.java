package com.ruoyi.metal.service;

import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.metal.catalog.CalculationRequest;
import com.ruoyi.metal.catalog.QuotationSaveRequest;
import com.ruoyi.metal.security.FactoryScope;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class QuotationHistoryCompatibilityTest {
    private QuotationSaveRequest input(String dimension) {
        return new QuotationSaveRequest("60ac2aeb-001d-4e52-b159-3437eca60739", 13501L,
            List.of(new CalculationRequest("铱环", Map.of("outerDiameter", "3", "innerDiameter", "2", "height", dimension), 19)));
    }
    private String hash(QuotationSaveRequest input) throws Exception {
        String raw=input.factoryId()+":"+input.items().stream().map(i->i.category()+":"+i.quantity()+":"+new TreeMap<>(i.dimensions())).toList();
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    }
    @Test void originalHighPrecisionPayloadReplaysWithoutRepricing() throws Exception {
        var jdbc=mock(JdbcTemplate.class);var rules=mock(QuoteRuleService.class);var original=input("3.123456");
        var historical=Map.<String,Object>of("id","10200","totalPrice","5067.371250");
        var service=new DashboardService(jdbc,mock(FactoryScope.class),rules){@Override public Map<String,Object> quotation(Long id){assertEquals(10200L,id);return historical;}};
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(Map.of("request_hash",hash(original),"request_actor",13852L,"quote_id",10200L)));
        try(var security=mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUserId).thenReturn(13852L);
            var replayed=service.saveQuotation(original);assertEquals(historical.get("totalPrice"),replayed.get("totalPrice"));assertEquals(historical.get("id"),replayed.get("id"));assertEquals(true,replayed.get("reused"));
            assertThrows(RuntimeException.class,()->service.saveQuotation(input("3.12")));
            security.when(SecurityUtils::getUserId).thenReturn(1L);
            assertThrows(RuntimeException.class,()->service.saveQuotation(original));
        }
        verifyNoInteractions(rules);
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void newRequestIsRejectedBeforeAnyQuotationWrite() {
        var jdbc=mock(JdbcTemplate.class);var rules=mock(QuoteRuleService.class);
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of());
        assertThrows(RuntimeException.class,()->new DashboardService(jdbc,mock(FactoryScope.class),rules).saveQuotation(input("3")));
        verifyNoInteractions(rules);verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void newPricingFieldsCannotReplayAnOldFingerprint() throws Exception {
        var jdbc=mock(JdbcTemplate.class);var rules=mock(QuoteRuleService.class);var original=input("3.123456");var item=original.items().get(0);
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(Map.of("quote_id",10200L,"request_hash",hash(original),"request_actor",13852L)));
        try(var security=mockStatic(SecurityUtils.class)){security.when(SecurityUtils::getUserId).thenReturn(13852L);
        for(int field=0;field<4;field++){
            var changed=new CalculationRequest(item.category(),item.dimensions(),item.quantity(),null,null,field==0?"MATERIAL_CUSTOM":null,field==1?"0":null,field==2?"0":null,field==3?"0":null);
            var request=new QuotationSaveRequest(original.requestId(),original.factoryId(),List.of(changed));
            assertThrows(RuntimeException.class,()->new DashboardService(jdbc,mock(FactoryScope.class),rules).saveQuotation(request));
        }
        }
        verifyNoInteractions(rules);verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
}
