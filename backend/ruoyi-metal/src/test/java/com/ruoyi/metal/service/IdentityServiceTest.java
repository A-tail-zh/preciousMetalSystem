package com.ruoyi.metal.service;

import java.util.*;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.metal.catalog.*;
import com.ruoyi.metal.mapper.*;
import com.ruoyi.metal.security.FactoryScope;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdentityServiceTest {
    RoleRecordMapper roles;
    StaffRecordMapper staff;
    FactoryScope scope;
    JdbcTemplate jdbc;
    RoleManagementService roleService;
    StaffManagementService staffService;
    MockedStatic<SecurityUtils> security;
    @BeforeEach void setup() {
        roles=mock(RoleRecordMapper.class); staff=mock(StaffRecordMapper.class);
        scope=mock(FactoryScope.class); jdbc=mock(JdbcTemplate.class);
        when(scope.isAdmin()).thenReturn(true); when(scope.currentFactory()).thenReturn(null);
        when(jdbc.queryForObject(anyString(),eq(Integer.class),any(Object[].class))).thenReturn(0);
        roleService=new RoleManagementService(roles,scope,jdbc); staffService=new StaffManagementService(staff,scope,jdbc);
        security=mockStatic(SecurityUtils.class);
        security.when(SecurityUtils::getUserId).thenReturn(7L);
        security.when(SecurityUtils::getUsername).thenReturn("tester");
        security.when(()->SecurityUtils.encryptPassword(anyString())).thenReturn("hashed-password");
    }
    @AfterEach void close(){security.close();}
    RoleRecord role(Long id) {
        RoleRecord r=new RoleRecord();r.setId(id);r.setVersion(0);r.setFactoryId(3L);r.setRoleName("Test role");
        r.setRoleKey("existing-role");r.setRoleSort(0);r.setStatus("0");r.setMenuIds(List.of(10L));return r;
    }
    StaffRecord person(Long id) {
        StaffRecord r=new StaffRecord();r.setId(id);r.setVersion(0);r.setFactoryId(3L);r.setNickName("Test staff");
        r.setPhonenumber("13912345678");r.setStatus("0");r.setRoleIds(List.of(9L));return r;
    }
    void roleEdit(RoleRecord input,RoleRecord old) {when(roles.detail(input.getId())).thenReturn(old);when(roles.lock(input.getId())).thenReturn(old);when(roles.update(input)).thenReturn(1);}
    void staffEdit(StaffRecord input,StaffRecord old) {when(staff.detail(input.getId())).thenReturn(old);when(staff.lock(input.getId())).thenReturn(old);when(staff.update(input)).thenReturn(1);}

    @Test void roleCreateChecksGrantsGeneratesKeyAndStoresMenus(){
        RoleRecord r=role(null);doAnswer(i->{r.setId(9L);return 1;}).when(roles).insert(r);
        assertEquals("9",roleService.create(r));assertTrue(r.getRoleKey().startsWith("factory_"));
        verify(scope).lockFactories(List.of(3L));verify(scope).menuGrants(List.of(10L));
        verify(jdbc).update("INSERT INTO sys_role_menu(role_id,menu_id) VALUES(?,?)",9L,10L);
        verify(scope).invalidate(anyCollection());
    }
    @Test void roleEditKeepsKeyAndInvalidatesAffectedSessions(){
        RoleRecord r=role(9L),old=role(9L);r.setRoleKey("injected");roleEdit(r,old);
        roleService.edit(r);assertEquals("existing-role",r.getRoleKey());verify(scope).invalidate(anyCollection());
    }
    @Test void roleCannotGrantUnownedPermissions(){
        RoleRecord r=role(null);doThrow(new ServiceException("Forbidden",403)).when(scope).menuGrants(anyList());
        assertThrows(ServiceException.class,()->roleService.create(r));verify(roles,never()).insert(any());verifyNoInteractions(jdbc);
    }
    @Test void rootRoleCannotBeEditedOrDeleted(){
        RoleRecord r=role(1L);roleEdit(r,role(1L));
        assertThrows(ServiceException.class,()->roleService.edit(r));assertThrows(ServiceException.class,()->roleService.delete(new Long[]{1L}));
        verify(roles,never()).update(any());verify(roles,never()).delete(anyLong(),anyString());
    }
    @Test void referencedRoleCannotBeDeletedOrTransferred(){
        RoleRecord old=role(9L),r=role(9L);r.setFactoryId(4L);roleEdit(r,old);
        when(jdbc.queryForObject(anyString(),eq(Integer.class),any(Object[].class))).thenReturn(1);
        assertThrows(ServiceException.class,()->roleService.edit(r));assertThrows(ServiceException.class,()->roleService.delete(new Long[]{9L}));
        verify(roles,never()).update(any());verify(roles,never()).delete(anyLong(),anyString());
    }
    @Test void roleTransferRequiresAdmin(){
        RoleRecord r=role(9L);r.setFactoryId(4L);roleEdit(r,role(9L));doThrow(new ServiceException("Forbidden",403)).when(scope).adminOnly();
        assertThrows(ServiceException.class,()->roleService.edit(r));verify(roles,never()).update(any());
    }
    @Test void roleRequiresFactory(){RoleRecord r=role(null);r.setFactoryId(null);assertThrows(ServiceException.class,()->roleService.create(r));verifyNoInteractions(roles);}
    @Test void scopedRoleAndStaffListsUseCurrentFactory(){
        when(scope.currentFactory()).thenReturn(3L);when(roles.list(3L)).thenReturn(List.of());when(staff.list(3L)).thenReturn(List.of());
        assertTrue(roleService.list().isEmpty());assertTrue(staffService.list().isEmpty());verify(roles).list(3L);verify(staff).list(3L);
    }
    @Test void crossFactoryDetailsAreRejectedBeforeHydrating(){
        when(scope.isAdmin()).thenReturn(false);when(roles.detail(9L)).thenReturn(role(9L));when(staff.detail(8L)).thenReturn(person(8L));
        doThrow(new ServiceException("Forbidden",403)).when(scope).check(3L);
        assertThrows(ServiceException.class,()->roleService.detail(9L));assertThrows(ServiceException.class,()->staffService.detail(8L));verifyNoInteractions(jdbc);
    }
    @Test void staffCreateHashesPasswordAssignsRolesAndInvalidatesSession(){
        StaffRecord r=person(null);r.setPassword("Password123");doAnswer(i->{r.setId(8L);return 1;}).when(staff).insert(r);
        assertEquals("8",staffService.create(r));assertEquals("hashed-password",r.getPassword());assertEquals(r.getPhonenumber(),r.getUserName());
        verify(scope).roleGrants(3L,List.of(9L));verify(jdbc).update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)",8L,9L);verify(scope).invalidate(List.of(8L));
    }
    @Test void staffCreateRejectsShortOrOversizedPasswords(){
        for(String password:Arrays.asList(null,"1234567","x".repeat(73),"密".repeat(25))) {
            StaffRecord r=person(null);r.setPassword(password);assertThrows(ServiceException.class,()->staffService.create(r));
        }verify(staff,never()).insert(any());
    }
    @Test void staffCannotReceiveInvalidOrCrossFactoryRole(){
        StaffRecord r=person(null);r.setPassword("Password123");doThrow(new ServiceException("Forbidden",403)).when(scope).roleGrants(anyLong(),anyList());
        assertThrows(ServiceException.class,()->staffService.create(r));verify(staff,never()).insert(any());
    }
    @Test void staffEditReplacesRolesWithoutChangingPassword(){
        StaffRecord r=person(8L);staffEdit(r,person(8L));staffService.edit(r);verify(staff).update(r);verify(scope).invalidate(List.of(8L));
        security.verify(()->SecurityUtils.encryptPassword(anyString()),never());
    }
    @Test void staffEditRejectsPasswordField(){StaffRecord r=person(8L);r.setPassword("Password123");staffEdit(r,person(8L));assertThrows(ServiceException.class,()->staffService.edit(r));verify(staff,never()).update(any());}
    @Test void staffTransferRequiresAdmin(){StaffRecord r=person(8L);r.setFactoryId(4L);staffEdit(r,person(8L));doThrow(new ServiceException("Forbidden",403)).when(scope).adminOnly();assertThrows(ServiceException.class,()->staffService.edit(r));verify(staff,never()).update(any());}
    @Test void rootAndCurrentAccountCannotBeDisabledOrDeleted(){
        for(Long id:List.of(1L,7L)){StaffRecord r=person(id);r.setStatus("1");staffEdit(r,person(id));assertThrows(ServiceException.class,()->staffService.edit(r));assertThrows(ServiceException.class,()->staffService.delete(new Long[]{id}));}
        verify(staff,never()).update(any());verify(staff,never()).delete(anyLong(),anyString());
    }
    @Test void ordinaryStaffDeletionInvalidatesSession(){StaffRecord r=person(8L);when(staff.lock(8L)).thenReturn(r);staffService.delete(new Long[]{8L});verify(staff).delete(8L,"tester");verify(scope).invalidate(List.of(8L));}
    @Test void passwordResetUsesVersionGuardAndInvalidatesSession(){
        StaffRecord r=person(8L);r.setPassword("NewPassword123");when(staff.detail(8L)).thenReturn(person(8L));when(jdbc.update(anyString(),any(Object[].class))).thenReturn(1);
        staffService.resetPassword(r);verify(jdbc).update(contains("AND version=? AND del_flag='0'"),eq("hashed-password"),eq("tester"),eq(8L),eq(0));verify(scope).invalidate(List.of(8L));
    }
    @Test void stalePasswordResetDoesNotInvalidateSessions(){StaffRecord r=person(8L);r.setPassword("NewPassword123");when(staff.detail(8L)).thenReturn(person(8L));assertThrows(ServiceException.class,()->staffService.resetPassword(r));verify(scope,never()).invalidate(anyCollection());}
    @Test void passwordResetRequiresVersion(){StaffRecord r=person(8L);r.setVersion(null);r.setPassword("NewPassword123");when(staff.detail(8L)).thenReturn(person(8L));assertThrows(ServiceException.class,()->staffService.resetPassword(r));verify(jdbc,never()).update(anyString(),any(Object[].class));}
    @Test void invalidStatusIsRejected(){RoleRecord r=role(9L);r.setStatus("2");StaffRecord s=person(8L);s.setStatus("2");assertThrows(ServiceException.class,()->roleService.changeStatus(r));assertThrows(ServiceException.class,()->staffService.changeStatus(s));verifyNoInteractions(roles,staff);}
}
