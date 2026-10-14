package com.ruoyi.metal.security;

import java.util.*;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.core.redis.RedisCache;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FactoryGrantTest {
    JdbcTemplate jdbc;FactoryScope scope;MockedStatic<SecurityUtils> security;
    @BeforeEach void setup(){jdbc=mock(JdbcTemplate.class);scope=spy(new FactoryScope(jdbc,mock(RedisCache.class)));doReturn(false).when(scope).isAdmin();security=mockStatic(SecurityUtils.class);LoginUser u=mock(LoginUser.class);when(u.getPermissions()).thenReturn(Set.of("system:user:list"));security.when(SecurityUtils::getLoginUser).thenReturn(u);}
    @AfterEach void close(){security.close();}
    @Test void invalidMenuIdsRejectedBeforeQuery(){for(List<Long> ids:Arrays.asList(null,List.of(0L),List.of(-1L),Arrays.asList(1L,null),List.of(1L,1L))){assertThrows(ServiceException.class,()->scope.menuGrants(ids));}verifyNoInteractions(jdbc);}
    @Test void missingMenuIsRejected(){assertThrows(ServiceException.class,()->scope.menuGrants(List.of(10L)));}
    @Test void unownedPermissionCannotBeGranted(){when(jdbc.query(anyString(),any(org.springframework.jdbc.core.RowMapper.class),any(Object[].class))).thenReturn(List.of("system:user:edit"));assertThrows(ServiceException.class,()->scope.menuGrants(List.of(10L)));}
    @Test void ownedPermissionCanBeGranted(){when(jdbc.query(anyString(),any(org.springframework.jdbc.core.RowMapper.class),any(Object[].class))).thenReturn(List.of("system:user:list"));assertDoesNotThrow(()->scope.menuGrants(List.of(10L)));}
    @Test void administratorCanGrantPermission(){doReturn(true).when(scope).isAdmin();when(jdbc.query(anyString(),any(org.springframework.jdbc.core.RowMapper.class),any(Object[].class))).thenReturn(List.of("system:user:edit"));assertDoesNotThrow(()->scope.menuGrants(List.of(10L)));}
    @Test void invalidRoleListsAreRejected(){for(List<Long> ids:Arrays.asList(null,List.<Long>of(),List.of(0L),Arrays.asList(9L,null),List.of(9L,9L))){assertThrows(ServiceException.class,()->scope.roleGrants(3L,ids));}verifyNoInteractions(jdbc);}
    @Test void superAdministratorRoleCannotBeAssigned(){assertThrows(ServiceException.class,()->scope.roleGrants(3L,List.of(1L)));verifyNoInteractions(jdbc);}
    @Test void missingOrDisabledRoleCannotBeAssigned(){assertThrows(ServiceException.class,()->scope.roleGrants(3L,List.of(9L)));}
    @Test void roleFromOtherFactoryCannotBeAssigned(){when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(Map.of("factory_id",4L)));assertThrows(ServiceException.class,()->scope.roleGrants(3L,List.of(9L)));}
    @Test void roleWithHigherPermissionsCannotBeAssigned(){when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(Map.of("factory_id",3L)));doThrow(new ServiceException("Forbidden",403)).when(scope).menuGrants(anyList());assertThrows(ServiceException.class,()->scope.roleGrants(3L,List.of(9L)));}
}
