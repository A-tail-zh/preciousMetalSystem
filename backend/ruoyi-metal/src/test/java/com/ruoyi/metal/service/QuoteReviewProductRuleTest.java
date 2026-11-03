package com.ruoyi.metal.service;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class QuoteReviewProductRuleTest {
    private Map<String,Object> row(Object density){
        var row=new LinkedHashMap<String,Object>();row.put("metalTypeId","2");row.put("metalTypeName","铂");row.put("density",density);row.put("version",6);return row;
    }
    @Test void productListQueriesOnlyGeometryFieldsAndKeepsNullDensity() throws Exception {
        var jdbc=mock(JdbcTemplate.class);when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(row(null)));
        var actual=new QuoteRuleService(jdbc).productRules(null);
        assertEquals(List.of(row(null)),actual);assertEquals(Set.of("metalTypeId","metalTypeName","density","version"),actual.get(0).keySet());
        var sql=ArgumentCaptor.forClass(String.class);verify(jdbc).queryForList(sql.capture(),eq(new Object[]{}));
        String query=sql.getValue().toLowerCase(Locale.ROOT);
        for(String price:List.of("powder_price","non_powder_price","shortage_price","loss_price","base_price","select *"))assertFalse(query.contains(price),query);
        assertTrue(query.contains("r.del_flag='0'"));assertTrue(query.contains("coalesce(r.version,0)"));
    }
    @Test void invalidMetalDoesNotReturnAnUnrelatedRecord() {
        var jdbc=mock(JdbcTemplate.class);when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of());
        var service=new QuoteRuleService(jdbc);
        assertThrows(com.ruoyi.common.exception.ServiceException.class,()->service.productRule(99L));
        assertThrows(com.ruoyi.common.exception.ServiceException.class,()->service.productRule(null));
    }
    @Test void selectedMetalStillReturnsOneGeometryRecord() throws Exception {
        var jdbc=mock(JdbcTemplate.class);when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(row("21.45")));
        assertEquals(row("21.45"),new QuoteRuleService(jdbc).productRule(2L));
        verify(jdbc).queryForList(contains("WHERE m.metal_type_id=?"),eq(new Object[]{2L}));
    }
}
