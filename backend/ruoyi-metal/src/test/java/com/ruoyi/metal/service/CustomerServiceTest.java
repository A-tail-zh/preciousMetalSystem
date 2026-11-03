package com.ruoyi.metal.service;

import java.util.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.metal.catalog.*;
import com.ruoyi.metal.mapper.CustomerMapper;
import com.ruoyi.metal.security.FactoryScope;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerServiceTest {
    CustomerMapper mapper;
    FactoryScope scope;
    CustomerService service;
    MockedStatic<SecurityUtils> security;
    @BeforeEach void setup() {
        mapper=mock(CustomerMapper.class);scope=mock(FactoryScope.class);service=new CustomerService(mapper,scope);
        when(scope.currentFactory()).thenReturn(7L);
        security=mockStatic(SecurityUtils.class);security.when(SecurityUtils::getUsername).thenReturn("customer-tester");
        doAnswer(call->{call.<CustomerRecord>getArgument(0).setId(9L);return 1;}).when(mapper).insert(any());
    }
    @AfterEach void close() { security.close(); }
    CustomerRecord form() {
        var row=new CustomerRecord();row.setName(" 客户 ");row.setCompanyName(" 公司 ");row.setAddress(" 地址 ");row.setContactName(" 张三 ");row.setContactPhone(" 13800138000 ");return row;
    }
    CustomerRecord existing(long id,long factory) {
        var row=form();row.setId(id);row.setFactoryId(factory);row.setVersion(0);return row;
    }
    @Test void ordinaryCreationBindsCurrentFactory() {
        var row=form();assertEquals("9",service.create(row));assertEquals(7L,row.getFactoryId());
        assertEquals("customer-tester",row.getCreateBy());verify(scope).lockFactories(List.of(7L));
    }
    @Test void forgedFactoryAndMissingOwnFactoryCannotInsert() {
        var row=form();row.setFactoryId(8L);assertThrows(ServiceException.class,()->service.create(row));
        row.setFactoryId(null);when(scope.currentFactory()).thenReturn(null);assertThrows(ServiceException.class,()->service.create(row));verify(mapper,never()).insert(any());
    }
    @Test void adminMustChooseFactoryAndCannotUseInactiveFactory() {
        when(scope.isAdmin()).thenReturn(true);var row=form();assertThrows(ServiceException.class,()->service.create(row));
        row.setFactoryId(8L);doThrow(new ServiceException("工厂无效")).when(scope).lockFactories(List.of(8L));assertThrows(ServiceException.class,()->service.create(row));verify(mapper,never()).insert(any());
    }
    @Test void adminCanCreateInChosenFactory() {
        when(scope.isAdmin()).thenReturn(true);var row=form();row.setFactoryId(8L);service.create(row);verify(scope).lockFactories(List.of(8L));
    }
    @Test void onlyOwnFactoryIsListedAndForeignFilterIsRejected() {
        var q=new ListQuery();service.list(q);verify(mapper).listFiltered(7L,q);
        q.setFactoryId(8L);assertThrows(ServiceException.class,()->service.list(q));verify(mapper,times(1)).listFiltered(any(),any());
    }
    @Test void foreignDetailsAndEditsAreRejected() {
        var foreign=existing(9,8);when(mapper.detail(9L)).thenReturn(foreign);doThrow(new ServiceException("无权访问",403)).when(scope).check(8L);
        assertThrows(ServiceException.class,()->service.detail(9L));assertThrows(ServiceException.class,()->service.edit(foreign));verify(mapper,never()).update(any());
    }
    @Test void factoryCannotChangeEvenForAdmin() {
        when(scope.isAdmin()).thenReturn(true);var old=existing(9,7);when(mapper.detail(9L)).thenReturn(old);when(mapper.lock(9L)).thenReturn(old);
        var row=existing(9,8);assertThrows(ServiceException.class,()->service.edit(row));verify(mapper,never()).update(any());
    }
    @Test void editRetainsFactoryAndAdvancesThroughVersionCheck() {
        when(scope.isAdmin()).thenReturn(true);var old=existing(9,7);when(mapper.detail(9L)).thenReturn(old);when(mapper.lock(9L)).thenReturn(old);when(mapper.update(any())).thenReturn(1);
        var row=existing(9,7);row.setFactoryId(null);service.edit(row);assertEquals(7L,row.getFactoryId());verify(mapper).update(row);
        row.setVersion(1);assertThrows(ServiceException.class,()->service.edit(row));verify(mapper,times(1)).update(any());
    }
    @Test void mixedFactoryBatchCannotDeleteAnyRecord() {
        when(mapper.detail(9L)).thenReturn(existing(9,7));when(mapper.detail(10L)).thenReturn(existing(10,8));doThrow(new ServiceException("无权访问",403)).when(scope).check(8L);
        assertThrows(ServiceException.class,()->service.delete(new Long[]{9L,10L}));verify(mapper,never()).delete(anyLong(),anyString());
    }
    @Test void missingRecordInBatchCannotDeleteAnyRecord() {
        var row=existing(9,7);when(mapper.detail(9L)).thenReturn(row);when(mapper.lock(9L)).thenReturn(row);
        assertThrows(ServiceException.class,()->service.delete(new Long[]{9L,10L}));verify(mapper,never()).delete(anyLong(),anyString());
    }
    @Test void validDeletionDeduplicatesAndKeepsAuditActor() {
        var row=existing(9,7);when(mapper.detail(9L)).thenReturn(row);when(mapper.lock(9L)).thenReturn(row);
        service.delete(new Long[]{9L,9L});verify(mapper).delete(9L,"customer-tester");
    }
    @Test void invalidDeletionDoesNotReachMapper() {
        for(Long[] ids:Arrays.asList(null,new Long[0],new Long[]{0L},new Long[]{null},new Long[101]))assertThrows(ServiceException.class,()->service.delete(ids));verifyNoInteractions(mapper);
    }
    @Test void validationNormalizesTextAndAllowsEmptyDepartment() {
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            var validator=factory.getValidator();var row=form();row.setDepartment("  ");assertNull(row.getDepartment());assertEquals("客户",row.getName());assertEquals("公司",row.getCompanyName());assertEquals("张三",row.getContactName());assertTrue(validator.validate(row).isEmpty());
            row.setName(" ");assertFalse(validator.validate(row).isEmpty());row.setName("客".repeat(101));assertFalse(validator.validate(row).isEmpty());
            row=form();row.setCompanyName("司".repeat(101));assertFalse(validator.validate(row).isEmpty());row=form();row.setAddress("址".repeat(501));assertFalse(validator.validate(row).isEmpty());
            row=form();row.setContactName("人".repeat(101));assertFalse(validator.validate(row).isEmpty());row=form();row.setDepartment("部".repeat(101));assertFalse(validator.validate(row).isEmpty());
            row=form();for(String phone:List.of("","02812345678","12800138000","1380013800","138001380000","138abcdefgh","+8613800138000")){row.setContactPhone(phone);assertFalse(validator.validate(row).isEmpty(),phone);}
            row=form();row.setName("客".repeat(100));row.setCompanyName("司".repeat(100));row.setAddress("址".repeat(500));row.setContactName("人".repeat(100));row.setDepartment("部".repeat(100));assertTrue(validator.validate(row).isEmpty());
        }
    }
    @Test void filtersEscapeLiteralWildcardAndRejectInvalidBounds() {
        var q=new ListQuery();q.setCompanyName("  %_!  ");assertEquals("%!%!_!!%",q.getCompanyNamePattern());q.setContactName("张");q.setContactPhone("138");assertEquals("%张%",q.getContactNamePattern());assertEquals("%138%",q.getContactPhonePattern());
        q.setContactName("x".repeat(501));assertThrows(ServiceException.class,q::validate);q.setContactName(null);q.setStartDate("2026-11-03");q.setEndDate("2026-11-02");assertThrows(ServiceException.class,q::validate);
    }
    @Test void customerMenuAndActionsCanBeAssignedThroughRoleEditor() {
        var rows=List.<Map<String,Object>>of(
            Map.of("id","3140","parentId","3000","menuType","C","component","metal/customer/index","perms","metal:customer:list"),
            Map.of("id","3142","parentId","3140","menuType","F","component","","perms","metal:customer:add"));
        assertEquals(2,com.ruoyi.metal.security.BusinessMenuOptions.filter(rows,true,Set.of()).size());
        assertEquals(1,com.ruoyi.metal.security.BusinessMenuOptions.filter(rows,false,Set.of("metal:customer:list")).size());
    }
}
