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
    @BeforeEach void setup() { mapper = mock(FactoryMapper.class); var scope = mock(com.ruoyi.metal.security.FactoryScope.class); when(scope.isAdmin()).thenReturn(true); when(scope.currentFactory()).thenReturn(null); when(scope.resolveFactory(2L)).thenReturn(2L); var jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class, i -> i.getMethod().getName().equals("queryForObject") ? 0 : i.getMethod().getName().equals("update") ? 1 : RETURNS_DEFAULTS.answer(i)); service = new FactoryService(mapper, scope, jdbc); }
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
        when(mapper.selectFiltered(eq(2L),any())).thenReturn(List.of(first));
        when(mapper.selectLines(List.of(2L))).thenReturn(List.of(line(10,"2"),line(11,"2")));
        var query=new com.ruoyi.metal.catalog.ListQuery();query.setFactoryId(2L);assertEquals(1, service.list(query).size()); assertEquals(List.of("10","11"), first.getProductionLineIds());
        verify(mapper, times(1)).selectLines(anyList());
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
    @Test void createWithoutLinesOnlyWritesFactory() {
        FactoryRequest r=form(List.of()); doAnswer(i->{r.setFactoryId(1L);return 1;}).when(mapper).insert(any(),anyString());
        assertEquals("1",service.create(r,"admin")); verify(mapper).insert(r,"admin"); verifyNoMoreInteractions(mapper);
    }
    @Test void staleVersionNeverChangesAssociations() {
        Factory f = factory(1); f.setVersion(1); when(mapper.lockFactory(1L)).thenReturn(f);
        FactoryRequest r = form(List.of()); r.setFactoryId(1L); r.setVersion(0);
        assertThrows(ServiceException.class, () -> service.edit(r,"admin")); verify(mapper, never()).clearLines(anyLong());
    }
    @Test void editNeverRemovesAssociations() {
        FactoryRequest r=form(List.of());r.setFactoryId(1L);r.setVersion(0);
        when(mapper.lockFactory(1L)).thenReturn(factory(1));when(mapper.update(r,"admin")).thenReturn(1);
        service.edit(r,"admin");verify(mapper,never()).clearLines(anyLong());verify(mapper,never()).assignLines(any(),any());
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
    @Test void builtinsDoNotBlockEmptyFactoryDeletionButCustomTypesDo() {
        var jdbc=mock(org.springframework.jdbc.core.JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(),eq(Integer.class),any(Object[].class))).thenReturn(0);
        when(mapper.lockFactory(1L)).thenReturn(factory(1));
        var guarded=new FactoryService(mapper,mock(com.ruoyi.metal.security.FactoryScope.class),jdbc);
        guarded.delete(new Long[]{1L},"admin");
        String sql="SELECT COUNT(*) FROM metal_process_type WHERE factory_id=? AND builtin_code IS NULL";
        verify(jdbc).queryForObject(sql,Integer.class,1L);
        when(jdbc.queryForObject(sql,Integer.class,1L)).thenReturn(1);
        assertThrows(ServiceException.class,()->guarded.delete(new Long[]{1L},"admin"));
        verify(mapper,times(1)).delete(1L,"admin");
    }
    @Test void activeCustomersPreventFactoryDeletion() {
        var scope=mock(com.ruoyi.metal.security.FactoryScope.class);
        var jdbc=mock(org.springframework.jdbc.core.JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(),eq(Integer.class),any(Object[].class))).thenReturn(0);
        when(jdbc.queryForObject(contains("metal_customer"),eq(Integer.class),eq(1L))).thenReturn(1);
        when(mapper.lockFactory(1L)).thenReturn(factory(1));
        var guarded=new FactoryService(mapper,scope,jdbc);
        assertThrows(ServiceException.class,()->guarded.delete(new Long[]{1L},"admin"));
        verify(mapper,never()).delete(anyLong(),anyString());
    }
    @Test void validationRejectsInvalidPrecisionAndWhitespace() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator(); FactoryRequest r = form(List.of());
            assertTrue(validator.validate(r).isEmpty()); assertEquals("工厂A",r.getFactoryName());
            r.setArea(new BigDecimal("0")); assertFalse(validator.validate(r).isEmpty());
            r.setArea(new BigDecimal("1.00001")); assertFalse(validator.validate(r).isEmpty());
            r.setArea(new BigDecimal("10000000000")); assertFalse(validator.validate(r).isEmpty());
            r.setArea(new BigDecimal("1")); r.setFactoryName("   "); assertFalse(validator.validate(r).isEmpty());
            r.setFactoryName("A"); r.setProductionLineIds(null); assertTrue(validator.validate(r).isEmpty());
        }
    }
    @Test void createRestoresMissingTemplateAndRequiresFactoryRuleInsert(){
        var jdbc=mock(org.springframework.jdbc.core.JdbcTemplate.class);var scope=mock(com.ruoyi.metal.security.FactoryScope.class);
        when(jdbc.queryForObject(anyString(),eq(Integer.class),any(Object[].class))).thenReturn(0);
        doAnswer(i->{((FactoryRequest)i.getArgument(0)).setFactoryId(8L);return 1;}).when(mapper).insert(any(),anyString());
        when(jdbc.update(startsWith("INSERT INTO metal_factory_volume_rule"),any(Object[].class))).thenReturn(1);
        when(jdbc.update(startsWith("INSERT INTO metal_process_type"),any(Object[].class))).thenReturn(1);
        var guarded=new FactoryService(mapper,scope,jdbc);assertEquals("8",guarded.create(form(List.of()),"admin"));
        var order=inOrder(jdbc);order.verify(jdbc).update(startsWith("INSERT INTO metal_volume_rule"),eq("admin"));order.verify(jdbc).update(startsWith("INSERT INTO metal_factory_volume_rule"),eq(8L),eq("admin"));
        order.verify(jdbc).update(startsWith("INSERT INTO metal_process_type"),eq(8L),eq("酸洗"),eq("ACID_WASH"),eq("admin"),eq("admin"));
        order.verify(jdbc).update(startsWith("INSERT INTO metal_process_type"),eq(8L),eq("融定"),eq("SMELTING"),eq("admin"),eq("admin"));
        when(jdbc.update(startsWith("INSERT INTO metal_process_type"),any(Object[].class))).thenReturn(0);
        assertEquals(503,assertThrows(ServiceException.class,()->guarded.create(form(List.of()),"admin")).getCode());
        when(jdbc.update(startsWith("INSERT INTO metal_factory_volume_rule"),any(Object[].class))).thenReturn(0);
        assertEquals(503,assertThrows(ServiceException.class,()->guarded.create(form(List.of()),"admin")).getCode());
    }
    @Test void adminListsAllFactoriesWithoutSelection(){
        when(mapper.selectFiltered(isNull(),any())).thenReturn(List.of(factory(1),factory(2)));
        assertEquals(2,service.list().size());verify(mapper).selectFiltered(isNull(),any());
    }
    @Test void ordinaryListResolvesOwnFactoryAndRejectsForeignSelection(){
        var scope=mock(com.ruoyi.metal.security.FactoryScope.class);
        when(scope.resolveFactory(null)).thenReturn(7L);when(scope.resolveFactory(8L)).thenThrow(new ServiceException("无权访问",403));
        var own=new FactoryService(mapper,scope,mock(org.springframework.jdbc.core.JdbcTemplate.class));
        own.list();verify(mapper).selectFiltered(eq(7L),any());
        var query=new com.ruoyi.metal.catalog.ListQuery();query.setFactoryId(8L);assertThrows(ServiceException.class,()->own.list(query));
    }
}
