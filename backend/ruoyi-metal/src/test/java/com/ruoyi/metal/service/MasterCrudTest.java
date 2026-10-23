package com.ruoyi.metal.service;

import java.util.*;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.metal.catalog.*;
import com.ruoyi.metal.mapper.*;
import com.ruoyi.metal.security.FactoryScope;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"rawtypes", "unchecked"})
class MasterCrudTest {
    enum Kind { PRODUCT, LINE, MATERIAL, PROCESS }
    MasterMapper mapper;
    MasterService service;
    FactoryScope scope;
    JdbcTemplate jdbc;
    MasterRecord form;
    MockedStatic<SecurityUtils> security;

    @BeforeEach void login() {
        security = mockStatic(SecurityUtils.class);
        security.when(SecurityUtils::getUsername).thenReturn("crud-tester");
    }
    @AfterEach void logout() { security.close(); }

    void setup(Kind kind) {
        scope = mock(FactoryScope.class);
        when(scope.isAdmin()).thenReturn(true);
        when(scope.currentFactory()).thenReturn(null);
        jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0);
        switch (kind) {
            case PRODUCT -> {
                ProductMapper m = mock(ProductMapper.class); mapper = m;
                QuoteRuleService quoteRules=mock(QuoteRuleService.class);QuoteRule rule=new QuoteRule();rule.setPiValue(new java.math.BigDecimal("3.141592653589793"));when(quoteRules.get()).thenReturn(rule);
                service = new ProductService(m, scope, jdbc,quoteRules);
                ProductRecord p = new ProductRecord(); p.setName("Product"); p.setCode("P");
                p.setCategory("铱环");
                p.setDimensions(Map.of("outerDiameter", "12", "innerDiameter", "10", "height", "2")); form = p;
            }
            case LINE -> {
                LineMapper m = mock(LineMapper.class); mapper = m;
                service = new LineService(m, scope, jdbc);
                LineRecord l = new LineRecord(); l.setName("Line"); l.setCode("L"); l.setFactoryId(7L); form = l;
            }
            case MATERIAL -> {
                MaterialMapper m = mock(MaterialMapper.class); mapper = m;
                service = new MaterialService(m, scope, jdbc);
                MaterialRecord r = new MaterialRecord(); r.setCode("M"); r.setMaterialType("铱粉"); form = r;
            }
            case PROCESS -> {
                ProcessMapper m = mock(ProcessMapper.class); mapper = m;
                service = new ProcessService(m, scope, jdbc);
                ProcessRecord p = new ProcessRecord(); p.setName("Process"); p.setProcessType(1); form = p;
            }
        }
    }

    @ParameterizedTest @EnumSource(Kind.class) void createReturnsGeneratedIdAndAuditUser(Kind kind) {
        setup(kind);
        doAnswer(i -> { ((MasterRecord)i.getArgument(0)).setId(9L); return 1; }).when(mapper).insert(form);
        assertEquals("9", service.create(form));
        assertEquals("crud-tester", form.getCreateBy()); assertEquals("crud-tester", form.getUpdateBy());
        verify(mapper).insert(form);
    }
    @ParameterizedTest @EnumSource(Kind.class) void createRejectsSuppliedId(Kind kind) {
        setup(kind); form.setId(9L);
        assertThrows(ServiceException.class, () -> service.create(form)); verifyNoInteractions(mapper);
    }
    @ParameterizedTest @EnumSource(Kind.class) void duplicateCreateIsBusinessError(Kind kind) {
        setup(kind); when(mapper.insert(form)).thenThrow(new DuplicateKeyException("duplicate"));
        assertThrows(ServiceException.class, () -> service.create(form));
    }
    @ParameterizedTest @EnumSource(Kind.class) void listAndDetailReturnRecords(Kind kind) {
        setup(kind); form.setId(9L); when(mapper.list(null)).thenReturn(List.of(form)); when(mapper.detail(9L)).thenReturn(form);
        assertEquals(List.of(form), service.list()); assertSame(form, service.detail(9L));
    }
    @ParameterizedTest @EnumSource(Kind.class) void emptyListAndMissingDetail(Kind kind) {
        setup(kind); when(mapper.list(null)).thenReturn(List.of()); assertTrue(service.list().isEmpty());
        assertThrows(ServiceException.class, () -> service.detail(9L)); verifyNoInteractions(jdbc);
    }
    @ParameterizedTest @EnumSource(Kind.class) void editUpdatesWithCurrentVersion(Kind kind) {
        setup(kind); form.setId(9L); form.setVersion(0); when(mapper.lock(9L)).thenReturn(form); when(mapper.update(form)).thenReturn(1);
        service.edit(form); verify(mapper).update(form); assertEquals("crud-tester", form.getUpdateBy());
    }
    @ParameterizedTest @EnumSource(Kind.class) void staleVersionNeverWrites(Kind kind) {
        setup(kind); form.setId(9L); form.setVersion(0);
        MasterRecord old = switch(kind) {
            case PRODUCT -> new ProductRecord(); case LINE -> new LineRecord();
            case MATERIAL -> new MaterialRecord(); case PROCESS -> new ProcessRecord();
        }; old.setId(9L); old.setVersion(1);
        when(mapper.lock(9L)).thenReturn(old);
        assertThrows(ServiceException.class, () -> service.edit(form)); verify(mapper, never()).update(any());
        verifyNoInteractions(jdbc);
    }
    @ParameterizedTest @EnumSource(Kind.class) void editRequiresIdAndVersion(Kind kind) {
        setup(kind); assertThrows(ServiceException.class, () -> service.edit(form)); form.setId(9L);
        assertThrows(ServiceException.class, () -> service.edit(form)); verifyNoInteractions(mapper);
    }
    @ParameterizedTest @EnumSource(Kind.class) void failedUpdateIsRejected(Kind kind) {
        setup(kind); form.setId(9L); form.setVersion(0); when(mapper.lock(9L)).thenReturn(form);
        assertThrows(ServiceException.class, () -> service.edit(form));
    }
    @ParameterizedTest @EnumSource(Kind.class) void duplicateEditIsBusinessError(Kind kind) {
        setup(kind); form.setId(9L); form.setVersion(0); when(mapper.lock(9L)).thenReturn(form);
        when(mapper.update(form)).thenThrow(new DuplicateKeyException("duplicate"));
        assertThrows(ServiceException.class, () -> service.edit(form));
    }
    @ParameterizedTest @EnumSource(Kind.class) void deleteDeduplicatesAndUsesAuditUser(Kind kind) {
        setup(kind); form.setId(9L); if (form instanceof LineRecord l) l.setFactoryId(null);
        when(mapper.lock(9L)).thenReturn(form); service.delete(new Long[]{9L,9L});
        verify(mapper, times(1)).delete(9L, "crud-tester");
    }
    @ParameterizedTest @EnumSource(Kind.class) void batchMissingRecordDoesNotPartiallyDelete(Kind kind) {
        setup(kind); form.setId(9L); when(mapper.lock(9L)).thenReturn(form);
        assertThrows(ServiceException.class, () -> service.delete(new Long[]{10L,9L}));
        verify(mapper, never()).delete(anyLong(), anyString());
    }
    @ParameterizedTest @EnumSource(Kind.class) void invalidDeletionNeverWrites(Kind kind) {
        setup(kind);
        for (Long[] ids : Arrays.asList(null,new Long[0],new Long[]{null},new Long[]{0L},new Long[]{-1L},new Long[101]))
            assertThrows(ServiceException.class, () -> service.delete(ids));
        verifyNoInteractions(mapper);
    }

    @Test void invalidProductDimensionsNeverInsert() {
        setup(Kind.PRODUCT); ProductRecord p = (ProductRecord)form;
        for (String value : List.of("0", "-1", "abc", "1.1234567", "100000000000000")) {
            p.setDimensions(Map.of("outerDiameter",value,"innerDiameter","10","height","2"));
            assertThrows(ServiceException.class, () -> service.create(p));
        }
        p.setDimensions(Map.of("outerDiameter","10","innerDiameter","10","height","2"));
        assertThrows(ServiceException.class, () -> service.create(p));
        p.setDimensions(Map.of("outerDiameter","12","innerDiameter","10","height","2","length","1"));
        assertThrows(ServiceException.class, () -> service.create(p)); verify(mapper,never()).insert(any());
    }
    @Test void duplicateAndMissingProductProcessesNeverInsert() {
        setup(Kind.PRODUCT); ProductRecord p = (ProductRecord)form; ProductStep s = new ProductStep(); s.setProcessId(8L);
        p.setProcesses(List.of(s,s)); assertThrows(ServiceException.class, () -> service.create(p));
        p.setProcesses(List.of(s)); assertThrows(ServiceException.class, () -> service.create(p));
        verify(mapper,never()).insert(any());
    }
    @Test void referencedProductAndProcessCannotBeDeleted() {
        for (Kind kind : List.of(Kind.PRODUCT,Kind.PROCESS)) {
            setup(kind); form.setId(9L); when(mapper.lock(9L)).thenReturn(form);
            when(jdbc.queryForObject(anyString(),eq(Integer.class),any(Object[].class))).thenReturn(1);
            assertThrows(ServiceException.class, () -> service.delete(new Long[]{9L}));
            verify(mapper,never()).delete(anyLong(),anyString());
        }
    }
    @Test void lineCannotBeDeletedWhileAssignedToProducts() {
        setup(Kind.LINE); form.setId(9L); when(mapper.lock(9L)).thenReturn(form);
        // Factory ownership alone must not prevent deletion; product references still do.
        when(jdbc.queryForObject(anyString(),eq(Integer.class),any(Object[].class))).thenReturn(1);
        assertThrows(ServiceException.class, () -> service.delete(new Long[]{9L})); verify(mapper,never()).delete(anyLong(),anyString());
    }
    @Test void invalidOrMissingLineProductsNeverInsert() {
        setup(Kind.LINE); LineRecord l = (LineRecord)form;
        for (List<Long> ids : Arrays.asList(List.of(8L,8L),List.of(0L),Arrays.asList(8L,null),List.of(8L))) {
            l.setProductIds(ids); assertThrows(ServiceException.class, () -> service.create(l));
        }
        verify(mapper,never()).insert(any());
    }
    @Test void lineListUsesCurrentFactoryAndDetailEnforcesScope() {
        setup(Kind.LINE); when(scope.currentFactory()).thenReturn(7L); when(mapper.list(7L)).thenReturn(List.of());
        assertTrue(service.list().isEmpty()); verify(mapper).list(7L);
        when(scope.isAdmin()).thenReturn(false); when(mapper.detail(9L)).thenReturn(form);
        doThrow(new ServiceException("Forbidden",403)).when(scope).check(7L);
        assertThrows(ServiceException.class, () -> service.detail(9L)); verifyNoInteractions(jdbc);
    }
    @Test void invalidMaterialTypeNeverInsert() {
        setup(Kind.MATERIAL); ((MaterialRecord)form).setMaterialType("invalid");
        assertThrows(ServiceException.class, () -> service.create(form)); verifyNoInteractions(mapper);
    }
}
