package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.metal.domain.Factory;
import com.ruoyi.metal.domain.ProductionLine;
import com.ruoyi.metal.dto.FactoryRequest;
import com.ruoyi.metal.mapper.FactoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Isolated CRUD tests: no database, HTTP server or existing records are needed. */
class FactoryCrudTest {
    private FactoryMapper mapper;
    private FactoryService service;

    @BeforeEach void setup() {
        mapper = mock(FactoryMapper.class);
        var scope = mock(com.ruoyi.metal.security.FactoryScope.class); when(scope.isAdmin()).thenReturn(true); when(scope.currentFactory()).thenReturn(null); var jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class, i -> i.getMethod().getName().equals("queryForObject") ? 0 : RETURNS_DEFAULTS.answer(i)); service = new FactoryService(mapper, scope, jdbc);
    }

    private FactoryRequest form(List<Long> lines) {
        FactoryRequest form = new FactoryRequest();
        form.setFactoryName(" Test factory ");
        form.setArea(new BigDecimal("12.5000"));
        form.setAddress(" Test address ");
        form.setProductionLineIds(lines);
        return form;
    }

    private Factory factory(long id) {
        Factory factory = new Factory();
        factory.setFactoryId(Long.toString(id));
        factory.setFactoryName("Factory " + id);
        factory.setVersion(0);
        return factory;
    }

    private ProductionLine line(long id, String owner) {
        ProductionLine line = new ProductionLine();
        line.setProductionLineId(Long.toString(id));
        line.setProductionLineName("Line " + id);
        line.setFactoryId(owner);
        return line;
    }

    private void generatedId() {
        doAnswer(invocation -> {
            ((FactoryRequest) invocation.getArgument(0)).setFactoryId(3L);
            return 1;
        }).when(mapper).insert(any(), eq("tester"));
    }

    @Test void createWithoutLinesOnlyWritesFactory() {
        FactoryRequest form=form(List.of());generatedId();assertEquals("3",service.create(form,"tester"));
        assertEquals("Test factory",form.getFactoryName());verify(mapper).insert(form,"tester");verifyNoMoreInteractions(mapper);
    }
    @Test void createRejectsCallerSuppliedIdWithoutWriting() {
        FactoryRequest form = form(List.of());
        form.setFactoryId(3L);
        assertThrows(ServiceException.class, () -> service.create(form, "tester"));
        verifyNoInteractions(mapper);
    }

    @Test void createRejectsPartialLineAssignment() {
        generatedId();
        when(mapper.lockLines(List.of(10L, 11L), null))
            .thenReturn(List.of(line(10, null), line(11, null)));
        when(mapper.assignLines(List.of(10L, 11L), 3L)).thenReturn(1);
        assertThrows(ServiceException.class, () -> service.create(form(List.of(10L, 11L)), "tester"));
    }

    @Test void detailLoadsAssociatedLinesAndTheirIds() {
        Factory factory = factory(3);
        when(mapper.selectById(3L)).thenReturn(factory);
        when(mapper.selectLines(List.of(3L))).thenReturn(List.of(line(10, "3")));
        assertSame(factory, service.detail(3L));
        assertEquals(List.of("10"), factory.getProductionLineIds());
        assertEquals("Line 10", factory.getProductionLines().get(0).getProductionLineName());
    }

    @Test void missingDetailDoesNotLoadAssociations() {
        assertThrows(ServiceException.class, () -> service.detail(3L));
        verify(mapper, never()).selectLines(anyList());
    }

    @Test void optionsForNewFactoryNeedNoFactoryLookup() {
        List<ProductionLine> available = List.of(line(10, null));
        when(mapper.selectOptions(null)).thenReturn(available);
        assertSame(available, service.options(null));
        verify(mapper, never()).selectById(any());
    }

    @Test void optionsForExistingFactoryVerifyItsExistence() {
        when(mapper.selectById(3L)).thenReturn(factory(3));
        List<ProductionLine> available = List.of(line(10, "3"), line(11, null));
        when(mapper.selectOptions(3L)).thenReturn(available);
        assertSame(available, service.options(3L));
    }

    @Test void optionsRejectMissingFactory() {
        assertThrows(ServiceException.class, () -> service.options(3L));
        verify(mapper, never()).selectOptions(any());
    }

    private FactoryRequest editForm(List<Long> lines) {
        FactoryRequest form = form(lines);
        form.setFactoryId(3L);
        form.setVersion(0);
        when(mapper.lockFactory(3L)).thenReturn(factory(3));
        return form;
    }

    @Test void editOnlyWritesBaseFields() {
        FactoryRequest form=editForm(List.of());when(mapper.update(form,"tester")).thenReturn(1);
        service.edit(form,"tester");verify(mapper).lockFactory(3L);verify(mapper).update(form,"tester");verifyNoMoreInteractions(mapper);
    }
    @Test void editRejectsLineOwnedByAnotherFactoryBeforeAnyWrite() {
        FactoryRequest form = editForm(List.of(10L));
        when(mapper.lockLines(List.of(10L), 3L)).thenReturn(List.of(line(10, "4")));
        assertThrows(ServiceException.class, () -> service.edit(form, "tester"));
        verify(mapper, never()).update(any(), anyString());
        verify(mapper, never()).clearLines(anyLong());
        verify(mapper, never()).assignLines(anyList(), anyLong());
    }

    @Test void editRejectsMissingLineBeforeAnyWrite() {
        FactoryRequest form = editForm(List.of(10L));
        when(mapper.lockLines(List.of(10L), 3L)).thenReturn(List.of());
        assertThrows(ServiceException.class, () -> service.edit(form, "tester"));
        verify(mapper, never()).update(any(), anyString());
    }

    @Test void failedOptimisticUpdateDoesNotClearAssociations() {
        FactoryRequest form = editForm(List.of());
        assertThrows(ServiceException.class, () -> service.edit(form, "tester"));
        verify(mapper, never()).clearLines(anyLong());
        verify(mapper, never()).assignLines(anyList(), anyLong());
    }

    @Test void editRejectsPartialLineAssignment() {
        FactoryRequest form = editForm(List.of(10L));
        when(mapper.lockLines(List.of(10L), 3L)).thenReturn(List.of(line(10, null)));
        when(mapper.update(form, "tester")).thenReturn(1);
        assertThrows(ServiceException.class, () -> service.edit(form, "tester"));
    }

    @Test void editRequiresPositiveIdAndVersion() {
        for (Long id : Arrays.asList(null, 0L, -1L)) {
            FactoryRequest form = form(List.of());
            form.setFactoryId(id);
            form.setVersion(0);
            assertThrows(ServiceException.class, () -> service.edit(form, "tester"));
        }
        FactoryRequest form = form(List.of());
        form.setFactoryId(3L);
        assertThrows(ServiceException.class, () -> service.edit(form, "tester"));
        verifyNoInteractions(mapper);
    }

    @Test void invalidLineIdsAreRejectedBeforeCreateOrEditWrites() {
        List<List<Long>> invalid = Arrays.asList(List.of(0L), List.of(-1L),
            Arrays.asList(10L, null), List.of(10L, 10L), Collections.nCopies(1001, 10L));
        for (List<Long> ids : invalid) {
            FactoryRequest form = form(ids);
            assertThrows(ServiceException.class, () -> service.create(form, "tester"));
            form.setFactoryId(3L);
            form.setVersion(0);
            assertThrows(ServiceException.class, () -> service.edit(form, "tester"));
        }
        verifyNoInteractions(mapper);
    }

    @Test void batchDeleteLocksAndChecksAllFactoriesBeforeDeleting() {
        when(mapper.lockFactory(1L)).thenReturn(factory(1));
        when(mapper.lockFactory(3L)).thenReturn(factory(3));
        when(mapper.delete(anyLong(), eq("tester"))).thenReturn(1);
        service.delete(new Long[]{3L, 1L, 3L}, "tester");
        InOrder order = inOrder(mapper);
        order.verify(mapper).lockFactory(1L);
        order.verify(mapper).lockFactory(3L);
        order.verify(mapper).countLines(1L);
        order.verify(mapper).countLines(3L);
        order.verify(mapper).delete(1L, "tester");
        order.verify(mapper).delete(3L, "tester");
        verifyNoMoreInteractions(mapper);
    }

    @Test void batchDeleteMissingFactoryDoesNotDeleteAnyFactory() {
        when(mapper.lockFactory(1L)).thenReturn(factory(1));
        assertThrows(ServiceException.class, () -> service.delete(new Long[]{1L, 3L}, "tester"));
        verify(mapper, never()).delete(anyLong(), anyString());
    }

    @Test void invalidDeleteIdsDoNotReachMapper() {
        List<Long[]> invalid = Arrays.asList(null, new Long[0], new Long[]{null},
            new Long[]{0L}, new Long[]{-1L}, Collections.nCopies(101, 1L).toArray(Long[]::new));
        for (Long[] ids : invalid)
            assertThrows(ServiceException.class, () -> service.delete(ids, "tester"));
        verifyNoInteractions(mapper);
    }
}
