package com.ruoyi.framework.web.service;

import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.system.service.ISysUserService;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TokenAuthorizationRefreshTest {
    final TokenService service=new TokenService();
    final ISysUserService users=mock(ISysUserService.class);
    final SysPermissionService permissions=mock(SysPermissionService.class);
    SysUser current; LoginUser login;
    SysUser user(){var u=new SysUser();u.setUserId(9L);u.setFactoryId(3L);u.setAuthVersion(7);u.setStatus("0");u.setDelFlag("0");return u;}
    @BeforeEach void setup(){
        ReflectionTestUtils.setField(service,"userService",users);ReflectionTestUtils.setField(service,"permissionService",permissions);
        current=user();login=new LoginUser(9L,null,user(),Set.of("old:permission"));login.setToken("existing-session");
        when(users.selectUserById(9L)).thenReturn(current);
    }
    @Test void grantsAndRevocationsReplaceCachedPermissionsOnEveryRequest(){
        when(permissions.getMenuPermission(current)).thenReturn(Set.of("new:permission"),Set.of());
        assertTrue(service.refreshAuthorization(login));assertEquals(Set.of("new:permission"),login.getPermissions());
        assertTrue(service.refreshAuthorization(login));assertTrue(login.getPermissions().isEmpty());
        assertEquals("existing-session",login.getToken());assertSame(current,login.getUser());
    }
    @Test void revokedLoginCannotBeRevivedByPermissionRefresh(){
        current.setAuthVersion(8);assertFalse(service.refreshAuthorization(login));verifyNoInteractions(permissions);
    }
    @Test void disabledDeletedMissingOrTransferredUserIsRejected(){
        current.setStatus("1");assertFalse(service.refreshAuthorization(login));current.setStatus("0");
        current.setDelFlag("2");assertFalse(service.refreshAuthorization(login));current.setDelFlag("0");
        current.setFactoryId(4L);assertFalse(service.refreshAuthorization(login));
        when(users.selectUserById(9L)).thenReturn(null);assertFalse(service.refreshAuthorization(login));verifyNoInteractions(permissions);
    }
}
