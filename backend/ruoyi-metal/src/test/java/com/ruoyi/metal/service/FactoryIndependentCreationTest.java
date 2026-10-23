package com.ruoyi.metal.service;

import com.ruoyi.metal.dto.FactoryRequest;
import com.ruoyi.metal.domain.Factory;
import com.ruoyi.metal.mapper.FactoryMapper;
import com.ruoyi.metal.security.FactoryScope;
import com.ruoyi.common.exception.ServiceException;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FactoryIndependentCreationTest {
    private final FactoryMapper mapper = mock(FactoryMapper.class);
    private final FactoryService service = new FactoryService(mapper, mock(FactoryScope.class), mock(JdbcTemplate.class));
    private FactoryRequest form(List<Long> lines) {
        var r = new FactoryRequest(); r.setFactoryName("独立工厂"); r.setAddress("测试地址");
        r.setArea(BigDecimal.ONE); r.setProductionLineIds(lines); return r;
    }
    @Test void omittedLinesAreValidAndCreateWithoutRelationWrites() {
        var r = form(null);
        try (var validation = Validation.buildDefaultValidatorFactory()) {
            assertTrue(validation.getValidator().validate(r).isEmpty());
        }
        doAnswer(i -> {r.setFactoryId(3L); return 1;}).when(mapper).insert(r,"tester");
        assertEquals("3", service.create(r,"tester"));
        verify(mapper).insert(r,"tester"); verifyNoMoreInteractions(mapper);
    }
    @Test void emptyLinesCreateWithoutRelationWrites() {
        var r = form(List.of());
        doAnswer(i -> {r.setFactoryId(3L); return 1;}).when(mapper).insert(r,"tester");
        assertEquals("3", service.create(r,"tester"));
        verify(mapper).insert(r,"tester"); verifyNoMoreInteractions(mapper);
    }
    @Test void editingWithOmittedOrEmptyLinesNeverTouchesExistingLines() {
        for (var lines : java.util.Arrays.<List<Long>>asList(null,List.of())) {
            reset(mapper); var r = form(lines); r.setFactoryId(3L); r.setVersion(4);
            var current = new Factory(); current.setFactoryId("3"); current.setVersion(4);
            when(mapper.lockFactory(3L)).thenReturn(current); when(mapper.update(r,"tester")).thenReturn(1);
            service.edit(r,"tester");
            verify(mapper).lockFactory(3L); verify(mapper).update(r,"tester"); verifyNoMoreInteractions(mapper);
        }
    }
    @Test void legacyBindingRequestIsRejectedBeforeAnyWrite() {
        var r = form(List.of(9L));
        assertTrue(assertThrows(ServiceException.class,()->service.create(r,"tester")).getMessage().contains("产线管理"));
        r.setFactoryId(3L); r.setVersion(0);
        assertTrue(assertThrows(ServiceException.class,()->service.edit(r,"tester")).getMessage().contains("产线管理"));
        verifyNoInteractions(mapper);
    }
}
