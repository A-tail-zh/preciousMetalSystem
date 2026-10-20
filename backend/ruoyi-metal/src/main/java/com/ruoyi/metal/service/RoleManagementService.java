package com.ruoyi.metal.service;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.metal.catalog.*;
import com.ruoyi.metal.mapper.RoleRecordMapper;
import com.ruoyi.metal.security.FactoryScope;
import com.ruoyi.common.exception.ServiceException;
/**
 * 角色业务服务，负责业务校验、数据处理和相关持久化操作。
 */
@Service public class RoleManagementService extends MasterService<RoleRecord> {
 private final JdbcTemplate jdbc;
 public RoleManagementService(RoleRecordMapper mapper,FactoryScope scope,JdbcTemplate jdbc){super(mapper,scope);this.jdbc=jdbc;}
 /**
  * 声明列表是否按照当前用户所属工厂过滤。
  */
 @Override protected boolean scoped(){return true;}
 /**
  * 校验当前用户对目标记录所属工厂的访问权限。
  */
 @Override protected void access(RoleRecord row){if(!scope.isAdmin())scope.check(row.getFactoryId());}
 /**
  * 在锁定主记录前检查访问权限，并按固定顺序锁定涉及的工厂。
  */
 @Override protected void beforeLock(RoleRecord form){RoleRecord old=mapper.detail(form.getId());if(old!=null){access(old);scope.lockFactories(Arrays.asList(old.getFactoryId(),form.getFactoryId()));}}
 /**
  * 批量删除前检查目标记录的访问权限，并锁定涉及的工厂。
  */
 @Override protected void beforeDelete(List<Long> ids){List<Long> factories=new ArrayList<>();for(Long id:ids){RoleRecord row=mapper.detail(id);if(row!=null){access(row);if(row.getFactoryId()!=null)factories.add(row.getFactoryId());}}scope.lockFactories(factories);}
 /**
  * 保存前校验业务数据，并准备需要持久化的字段。
  */
 @Override protected void prepare(RoleRecord row,RoleRecord old){
  if(old!=null&&old.getId()==1L)throw new ServiceException("系统超级管理员角色为只读");
  if(row.getFactoryId()==null)throw new ServiceException("请选择所属工厂");
  if(old!=null&&!Objects.equals(old.getFactoryId(),row.getFactoryId())){
   scope.adminOnly();if(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_role ur JOIN sys_user u ON u.user_id=ur.user_id AND u.del_flag='0' WHERE ur.role_id=?",Integer.class,row.getId())>0)throw new ServiceException("角色被人员使用，不能转移工厂");
  }
  scope.lockFactories(List.of(row.getFactoryId()));scope.menuGrants(row.getMenuIds());
  if(old==null)row.setRoleKey("factory_"+UUID.randomUUID().toString().replace("-",""));else row.setRoleKey(old.getRoleKey());
 }
 /**
  * 保存主记录的关联关系。
  */
 @Override protected void relations(RoleRecord row){
  jdbc.update("DELETE FROM sys_role_menu WHERE role_id=?",row.getId());
  for(Long id:row.getMenuIds())jdbc.update("INSERT INTO sys_role_menu(role_id,menu_id) VALUES(?,?)",row.getId(),id);
  scope.invalidate(jdbc.query("SELECT user_id FROM sys_user_role WHERE role_id=?",(r,n)->r.getLong(1),row.getId()));
 }
 /**
  * 为查询结果批量补充关联资料。
  */
 @Override protected void hydrate(List<RoleRecord> rows){
  if(rows.isEmpty())return;Map<Long,RoleRecord> indexed=new HashMap<>();rows.forEach(r->{r.setMenuIds(new ArrayList<>());indexed.put(r.getId(),r);});String placeholders=String.join(",",Collections.nCopies(rows.size(),"?"));
  jdbc.query("SELECT role_id,menu_id FROM sys_role_menu WHERE role_id IN ("+placeholders+") ORDER BY menu_id",(org.springframework.jdbc.core.RowCallbackHandler)r->indexed.get(r.getLong("role_id")).getMenuIds().add(r.getLong("menu_id")),rows.stream().map(RoleRecord::getId).toArray());
 }
 /**
  * 检查业务引用和保护规则，不满足删除条件时抛出异常。
  */
 @Override protected void deletable(RoleRecord row){if(row.getId()==1L)throw new ServiceException("不能删除系统超级管理员角色");if(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_role ur JOIN sys_user u ON u.user_id=ur.user_id AND u.del_flag='0' WHERE ur.role_id=?",Integer.class,row.getId())>0)throw new ServiceException("角色被人员引用，不能删除");}
 /**
  * 按版本号更新启用状态，并通过业务保存流程使相关旧登录失效。
  */
 @Transactional(rollbackFor=Exception.class) public void changeStatus(RoleRecord input){if(!Set.of("0","1").contains(input.getStatus()))throw new ServiceException("状态无效");RoleRecord row=detail(input.getId());row.setVersion(input.getVersion());row.setStatus(input.getStatus());edit(row);}
}
