package com.ruoyi.metal.service;
import java.util.*;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.metal.catalog.*;
import com.ruoyi.metal.mapper.StaffRecordMapper;
import com.ruoyi.metal.security.FactoryScope;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
/**
 * 工作人员业务服务，负责业务校验、数据处理和相关持久化操作。
 */
@Service public class StaffManagementService extends MasterService<StaffRecord> {
 private final JdbcTemplate jdbc;
 public StaffManagementService(StaffRecordMapper mapper,FactoryScope scope,JdbcTemplate jdbc){super(mapper,scope);this.jdbc=jdbc;}
 /**
  * 声明列表是否按照当前用户所属工厂过滤。
  */
 @Override protected boolean scoped(){return true;}
 /**
  * 校验当前用户对目标记录所属工厂的访问权限。
  */
 @Override protected void access(StaffRecord row){if(!scope.isAdmin())scope.check(row.getFactoryId());}
 /**
  * 在锁定主记录前检查访问权限，并按固定顺序锁定涉及的工厂。
  */
 @Override protected void beforeLock(StaffRecord form){StaffRecord old=mapper.detail(form.getId());if(old!=null){access(old);scope.lockFactories(Arrays.asList(old.getFactoryId(),form.getFactoryId()));}}
 /**
  * 批量删除前检查目标记录的访问权限，并锁定涉及的工厂。
  */
 @Override protected void beforeDelete(List<Long> ids){List<Long> factories=new ArrayList<>();for(Long id:ids){StaffRecord row=mapper.detail(id);if(row!=null){access(row);if(row.getFactoryId()!=null)factories.add(row.getFactoryId());}}scope.lockFactories(factories);}
 /**
  * 保存前校验业务数据，并准备需要持久化的字段。
  */
 @Override protected void prepare(StaffRecord row,StaffRecord old){
  if("1".equals(row.getStatus())&&old!=null&&(old.getId()==1L||old.getId().equals(SecurityUtils.getUserId())))throw new ServiceException("不能停用超级管理员或当前账号");
  if(old!=null&&old.getId()==1L){scope.adminOnly();row.setFactoryId(null);row.setUserName("admin");row.setRoleIds(List.of(1L));return;}
  if(old!=null&&!Objects.equals(old.getFactoryId(),row.getFactoryId()))scope.adminOnly();
  if(row.getFactoryId()==null)throw new ServiceException("请选择所属工厂");
  scope.lockFactories(List.of(row.getFactoryId()));scope.roleGrants(row.getFactoryId(),row.getRoleIds());
  row.setUserName(row.getPhonenumber());
  if(old==null){validatePassword(row.getPassword());row.setPassword(SecurityUtils.encryptPassword(row.getPassword()));}
  else if(row.getPassword()!=null)throw new ServiceException("编辑不允许修改密码，请使用重置密码");
 }
 /**
  * 保存主记录的关联关系。
  */
 @Override protected void relations(StaffRecord row){
  jdbc.update("DELETE FROM sys_user_role WHERE user_id=?",row.getId());
  for(Long role:row.getRoleIds())jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)",row.getId(),role);
  scope.invalidate(List.of(row.getId()));
 }
 /**
  * 为查询结果批量补充关联资料。
  */
 @Override protected void hydrate(List<StaffRecord> rows){
  if(rows.isEmpty())return;Map<Long,StaffRecord> indexed=new HashMap<>();rows.forEach(r->{r.setRoleIds(new ArrayList<>());r.setRoleNames(new ArrayList<>());indexed.put(r.getId(),r);});
  String placeholders=String.join(",",Collections.nCopies(rows.size(),"?"));
  jdbc.query("SELECT ur.user_id,r.role_id,r.role_name FROM sys_user_role ur JOIN sys_role r ON r.role_id=ur.role_id AND r.del_flag='0' WHERE ur.user_id IN ("+placeholders+") ORDER BY r.role_sort,r.role_id",(org.springframework.jdbc.core.RowCallbackHandler)rs->{StaffRecord r=indexed.get(rs.getLong("user_id"));r.getRoleIds().add(rs.getLong("role_id"));r.getRoleNames().add(rs.getString("role_name"));},rows.stream().map(StaffRecord::getId).toArray());
 }
 /**
  * 检查业务引用和保护规则，不满足删除条件时抛出异常。
  */
 @Override protected void deletable(StaffRecord row){if(row.getId()==1L||row.getId().equals(SecurityUtils.getUserId()))throw new ServiceException("不能删除超级管理员或当前账号");WorkOrderReferences.person(jdbc,row.getId());WarehouseReferences.person(jdbc,row.getId());scope.invalidate(List.of(row.getId()));}
 private void validatePassword(String value){com.ruoyi.common.utils.PasswordPolicy.validate(value);}
 /**
  * 按版本号更新启用状态，并通过业务保存流程使相关旧登录失效。
  */
 @Transactional(rollbackFor=Exception.class) public void changeStatus(StaffRecord input){
  if(!Set.of("0","1").contains(input.getStatus()))throw new ServiceException("状态无效");
  StaffRecord row=detail(input.getId());row.setVersion(input.getVersion());row.setStatus(input.getStatus());edit(row);
 }
 /**
  * 校验密码长度，按版本号更新密码哈希，并使目标用户的旧登录失效。
  */
 @Transactional(rollbackFor=Exception.class) public void resetPassword(StaffRecord input){
  validatePassword(input.getPassword());StaffRecord row=detail(input.getId());
  if(input.getVersion()==null)throw new ServiceException("必须提供版本号");
  int changed=jdbc.update("UPDATE sys_user SET password=?,pwd_update_date=NOW(),version=version+1,update_by=?,update_time=NOW() WHERE user_id=? AND version=? AND del_flag='0'",SecurityUtils.encryptPassword(input.getPassword()),SecurityUtils.getUsername(),row.getId(),input.getVersion());
  if(changed!=1)throw new ServiceException("数据已被修改，请刷新后重试");scope.invalidate(List.of(row.getId()));
 }
}
