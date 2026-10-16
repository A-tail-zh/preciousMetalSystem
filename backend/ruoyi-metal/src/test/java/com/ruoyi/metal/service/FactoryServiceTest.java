package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.util.List;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.metal.domain.*;
import com.ruoyi.metal.dto.FactoryRequest;
import com.ruoyi.metal.mapper.FactoryMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FactoryServiceTest {
    private FactoryMapper mapper;
    private FactoryService service;
    @BeforeEach void setup() { mapper = mock(FactoryMapper.class); var scope = mock(com.ruoyi.metal.security.FactoryScope.class); when(scope.isAdmin()).thenReturn(true); when(scope.currentFactory()).thenReturn(null); var jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class, i -> i.getMethod().getName().equals("queryForObject") ? 0 : RETURNS_DEFAULTS.answer(i)); service = new FactoryService(mapper, scope, jdbc); }
    private Factory factory(long id) {
        Factory f = new Factory(); f.setFactoryId(Long.toString(id)); f.setFactoryName("工厂" + id); f.setVersion(0); return f;
    }
    private ProductionLine line(long id, String owner) {
        ProductionLine l = new ProductionLine(); l.setProductionLineId(Long.toString(id)); l.setProductionLineName("产线" + id); l.setFactoryId(owner); return l;
    }
    private FactoryRequest form(List<Long> ids) {
        FactoryRequest r = new FactoryRequest(); r.setFactoryName(" 工厂A "); r.setArea(new BigDecimal("12.5000"));
        r.setAddress(" 地址 "); r.setProductionLineIds(ids); return r;
    }
    @Test void pageHydratesLinesInOneBatch() {
        Factory first = factory(2), second = factory(1);
        when(mapper.selectList(null)).thenReturn(List.of(first, second));
        when(mapper.selectLines(List.of(2L,1L))).thenReturn(List.of(line(10,"2"),line(11,"2")));
        assertEquals(2, service.list().size()); assertEquals(List.of("10","11"), first.getProductionLineIds());
        assertTrue(second.getProductionLines().isEmpty()); verify(mapper, times(1)).selectLines(anyList());
    }
    @Test void emptyPageDoesNotQueryEmptyInClause() {
        when(mapper.selectList(null)).thenReturn(List.of()); assertTrue(service.list().isEmpty()); verify(mapper, never()).selectLines(anyList());
    }
    @Test void missingFactoryCannotBeReadOrEdited() {
        assertThrows(ServiceException.class, () -> service.detail(1L));
        FactoryRequest r = form(List.of()); r.setFactoryId(1L); r.setVersion(0);
        assertThrows(ServiceException.class, () -> service.edit(r,"admin")); verify(mapper, never()).update(any(),anyString());
    }
    @Test void duplicateLinesRejectedBeforeInsert() {
        assertThrows(ServiceException.class, () -> service.create(form(List.of(10L,10L)),"admin"));
        verify(mapper, never()).insert(any(),anyString());
    }
    @Test void occupiedLineRejected() {
        FactoryRequest r = form(List.of(10L));
        doAnswer(i -> { ((FactoryRequest)i.getArgument(0)).setFactoryId(1L); return 1; }).when(mapper).insert(any(),anyString());
        when(mapper.lockLines(List.of(10L),null)).thenReturn(List.of(line(10,"2")));
        assertThrows(ServiceException.class, () -> service.create(r,"admin")); verify(mapper, never()).assignLines(any(),any());
    }
    @Test void nonexistentLineRejected() {
        FactoryRequest r = form(List.of(10L));
        doAnswer(i -> { ((FactoryRequest)i.getArgument(0)).setFactoryId(1L); return 1; }).when(mapper).insert(any(),anyString());
        when(mapper.lockLines(List.of(10L),null)).thenReturn(List.of());
        assertThrows(ServiceException.class, () -> service.create(r,"admin"));
    }
    @Test void createWithoutLinesReturnsGeneratedId() {
        FactoryRequest r = form(List.of());
        doAnswer(i -> { ((FactoryRequest)i.getArgument(0)).setFactoryId(3L); return 1; }).when(mapper).insert(any(),anyString());
        when(mapper.lockLines(List.of(),null)).thenReturn(List.of());
        assertEquals("3",service.create(r,"admin")); verify(mapper, never()).assignLines(any(),any());
    }
    @Test void staleVersionNeverChangesAssociations() {
        Factory f = factory(1); f.setVersion(1); when(mapper.lockFactory(1L)).thenReturn(f);
        FactoryRequest r = form(List.of()); r.setFactoryId(1L); r.setVersion(0);
        assertThrows(ServiceException.class, () -> service.edit(r,"admin")); verify(mapper, never()).clearLines(anyLong());
    }
    @Test void editCanRemoveAllAssociations() {
        FactoryRequest r = form(List.of()); r.setFactoryId(1L); r.setVersion(0);
        when(mapper.lockFactory(1L)).thenReturn(factory(1)); when(mapper.lockLines(List.of(),1L)).thenReturn(List.of(line(10,"1")));
        when(mapper.update(r,"admin")).thenReturn(1); service.edit(r,"admin"); verify(mapper).clearLines(1L);
        verify(mapper, never()).assignLines(any(),any());
    }
    @Test void normalAccountCannotClaimUnassignedLineThroughFactoryEndpoint() {
        var normalScope=mock(com.ruoyi.metal.security.FactoryScope.class);
        var normalService=new FactoryService(mapper,normalScope,mock(org.springframework.jdbc.core.JdbcTemplate.class));
        FactoryRequest r=form(List.of(10L));r.setFactoryId(1L);r.setVersion(0);
        when(mapper.lockFactory(1L)).thenReturn(factory(1));
        when(mapper.lockLines(List.of(10L),1L)).thenReturn(List.of(line(10,null)));
        assertThrows(ServiceException.class,()->normalService.edit(r,"normal"));
        verify(mapper,never()).update(any(),anyString());verify(mapper,never()).assignLines(any(),any());
    }
    @Test void batchChecksEveryFactoryBeforeAnyDelete() {
        when(mapper.lockFactory(1L)).thenReturn(factory(1)); when(mapper.lockFactory(2L)).thenReturn(factory(2));
        when(mapper.countLines(2L)).thenReturn(1);
        assertThrows(ServiceException.class, () -> service.delete(new Long[]{2L,1L},"admin"));
        verify(mapper, never()).delete(anyLong(),anyString());
    }
    @Test void deleteDeduplicatesIds() {
        when(mapper.lockFactory(1L)).thenReturn(factory(1)); service.delete(new Long[]{1L,1L},"admin");
        verify(mapper,times(1)).delete(1L,"admin");
    }
    @Test void validationRejectsInvalidPrecisionAndWhitespace() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator(); FactoryRequest r = form(List.of());
            assertTrue(validator.validate(r).isEmpty()); assertEquals("工厂A",r.getFactoryName());
            r.setArea(new BigDecimal("0")); assertFalse(validator.validate(r).isEmpty());
            r.setArea(new BigDecimal("1.00001")); assertFalse(validator.validate(r).isEmpty());
            r.setArea(new BigDecimal("10000000000")); assertFalse(validator.validate(r).isEmpty());
            r.setArea(new BigDecimal("1")); r.setFactoryName("   "); assertFalse(validator.validate(r).isEmpty());
            r.setFactoryName("A"); r.setProductionLineIds(null); assertFalse(validator.validate(r).isEmpty());
        }
    }
}
