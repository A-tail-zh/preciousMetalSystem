package com.ruoyi.framework.aspect;
import java.util.*;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ruoyi.metal.security.FactoryScope;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.exception.ServiceException;
/** Native maintenance endpoints are admin-only; their changes must also revoke sessions. */
@Aspect @Component public class LegacyIdentityGuard {
 private final FactoryScope scope;private final JdbcTemplate jdbc;
 public LegacyIdentityGuard(FactoryScope scope,JdbcTemplate jdbc){this.scope=scope;this.jdbc=jdbc;}
 @Before("execution(* com.ruoyi.system.service.impl.SysUserServiceImpl.insertUser(..)) || execution(* com.ruoyi.system.service.impl.SysUserServiceImpl.updateUser(..)) || execution(* com.ruoyi.system.service.impl.SysUserServiceImpl.insertUserAuth(..))")
 public void grants(JoinPoint point){
  Long userId=null;Long[] roleIds=null;
  for(Object a:point.getArgs()){if(a instanceof SysUser u){userId=u.getUserId();roleIds=u.getRoleIds();}else if(a instanceof Long id)userId=id;else if(a instanceof Long[] ids)roleIds=ids;}
  if(roleIds!=null&&(userId==null||userId!=1L)&&Arrays.asList(roleIds).contains(1L))throw new ServiceException("不能分配系统超级管理员角色",403);
 }
 @AfterReturning("execution(* com.ruoyi.system.service.impl.SysUserServiceImpl.updateUser(..)) || execution(* com.ruoyi.system.service.impl.SysUserServiceImpl.updateUserStatus(..)) || execution(* com.ruoyi.system.service.impl.SysUserServiceImpl.resetPwd(..)) || execution(* com.ruoyi.system.service.impl.SysUserServiceImpl.deleteUserByIds(..)) || execution(* com.ruoyi.system.service.impl.SysUserServiceImpl.insertUserAuth(..)) || execution(* com.ruoyi.system.service.impl.SysRoleServiceImpl.updateRole(..)) || execution(* com.ruoyi.system.service.impl.SysRoleServiceImpl.updateRoleStatus(..)) || execution(* com.ruoyi.system.service.impl.SysRoleServiceImpl.deleteRoleByIds(..)) || execution(* com.ruoyi.system.service.impl.SysRoleServiceImpl.authDataScope(..))")
 public void revoke(JoinPoint point){
  Set<Long> ids=new HashSet<>();boolean roles=point.getTarget().getClass().getSimpleName().contains("Role");
  for(Object arg:point.getArgs()){
   if(arg instanceof SysUser u&&u.getUserId()!=null)ids.add(u.getUserId());
   else if(arg instanceof SysRole r&&r.getRoleId()!=null)ids.add(r.getRoleId());
   else if(arg instanceof Long id)ids.add(id);
   else if(arg instanceof Long[] array)ids.addAll(Arrays.asList(array));
  }
  if(roles){Set<Long> users=new HashSet<>();for(Long role:ids)users.addAll(jdbc.query("SELECT user_id FROM sys_user_role WHERE role_id=?",(r,n)->r.getLong(1),role));scope.invalidate(users);}
  else scope.invalidate(ids);
 }
}
