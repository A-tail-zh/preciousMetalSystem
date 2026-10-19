package com.ruoyi.framework.aspect;
import java.util.*;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.ruoyi.system.domain.SysUserRole;
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
 @Autowired private PlatformTransactionManager transactionManager;
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

 /** Legacy relationship mutations must change authentication version in the same transaction. */
 @Around("execution(* com.ruoyi.system.service.impl.SysRoleServiceImpl.deleteAuthUser(..)) || execution(* com.ruoyi.system.service.impl.SysRoleServiceImpl.deleteAuthUsers(..)) || execution(* com.ruoyi.system.service.impl.SysRoleServiceImpl.insertAuthUsers(..))")
 public Object roleAssignments(ProceedingJoinPoint point)throws Throwable{
  try{return new TransactionTemplate(transactionManager).execute(status->{
   scope.adminOnly();Object[] args=point.getArgs();Long roleId;Long[] requested;
   if(args.length==1&&args[0] instanceof SysUserRole relation){roleId=relation.getRoleId();requested=new Long[]{relation.getUserId()};}
   else if(args.length==2&&args[0] instanceof Long role&&args[1] instanceof Long[] users){roleId=role;requested=users;}
   else throw new ServiceException("角色授权参数无效");
   if(roleId==null||roleId<=0||requested==null||requested.length==0||requested.length>1000||Arrays.stream(requested).anyMatch(id->id==null||id<=0))throw new ServiceException("请选择有效角色及1至1000名人员");
   if(roleId==1L)throw new ServiceException("系统超级管理员角色不允许通过此接口分配或撤销",403);
   List<Long> ids=Arrays.stream(requested).distinct().sorted().toList();String placeholders=String.join(",",Collections.nCopies(ids.size(),"?"));
   String userQuery="SELECT user_id,factory_id FROM sys_user WHERE user_id IN ("+placeholders+") AND del_flag='0' ORDER BY user_id";
   var before=jdbc.queryForList(userQuery,ids.toArray());if(before.size()!=ids.size())throw new ServiceException("人员不存在或已删除");
   scope.lockFactories(before.stream().map(u->asId(u.get("factory_id"))).filter(Objects::nonNull).distinct().sorted().toList());
   var users=jdbc.queryForList(userQuery+" FOR UPDATE",ids.toArray());if(users.size()!=ids.size())throw new ServiceException("人员已变更，请刷新后重试");
   for(int i=0;i<users.size();i++)if(!Objects.equals(asId(before.get(i).get("factory_id")),asId(users.get(i).get("factory_id"))))throw new ServiceException("人员所属工厂已变更，请刷新后重试");
   boolean grant=point.getSignature().getName().equals("insertAuthUsers");
   if(grant)for(var user:users){Long factory=asId(user.get("factory_id"));if(factory==null)throw new ServiceException("人员未配置有效工厂");scope.roleGrants(factory,List.of(roleId));}
   List<Object> relationArgs=new ArrayList<>();relationArgs.add(roleId);relationArgs.addAll(ids);
   Set<Long> existing=new HashSet<>();jdbc.queryForList("SELECT user_id FROM sys_user_role WHERE role_id=? AND user_id IN ("+placeholders+")",relationArgs.toArray()).forEach(r->existing.add(asId(r.get("user_id"))));
   Set<Long> affected=new TreeSet<>(ids);if(grant)affected.removeAll(existing);else affected.retainAll(existing);
   Object result;try{Object[] actual=args.length==1?args:new Object[]{roleId,ids.toArray(Long[]::new)};result=point.proceed(actual);}catch(RuntimeException|Error failure){throw failure;}catch(Throwable failure){throw new RoleMutationFailure(failure);}
   if(result instanceof Number n&&n.intValue()>0&&!affected.isEmpty())scope.invalidate(affected);
   return result;
  });}catch(RoleMutationFailure failure){throw failure.getCause();}
 }
 private static Long asId(Object value){return value==null?null:Long.valueOf(value.toString());}
 private static class RoleMutationFailure extends RuntimeException{RoleMutationFailure(Throwable cause){super(cause);}}
}
