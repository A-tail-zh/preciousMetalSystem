package com.ruoyi.metal.service;
import java.util.*;import org.springframework.stereotype.Service;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.transaction.annotation.Transactional;import org.springframework.dao.DuplicateKeyException;
import jakarta.validation.constraints.*;import com.ruoyi.common.utils.SecurityUtils;import com.ruoyi.common.utils.PasswordPolicy;import com.ruoyi.common.exception.ServiceException;import com.ruoyi.metal.security.FactoryScope;
@Service public class ProfileManagementService {
 private final JdbcTemplate jdbc;private final FactoryScope scope;
 public ProfileManagementService(JdbcTemplate j,FactoryScope s){jdbc=j;scope=s;}
 public record Profile(@NotNull @Min(0) Integer version,@NotBlank @Size(max=100) String nickName,@Size(max=11) String phonenumber,@Email @Size(max=50) String email,@Pattern(regexp="[012]") String sex){}
 public record Password(@NotBlank String oldPassword,@NotBlank String newPassword){}
 public Integer version(){return jdbc.queryForObject("SELECT version FROM sys_user WHERE user_id=? AND del_flag='0'",Integer.class,SecurityUtils.getUserId());}
 private Map<String,Object> lock(){Long id=SecurityUtils.getUserId();var initial=jdbc.queryForMap("SELECT factory_id FROM sys_user WHERE user_id=? AND del_flag='0' AND status='0'",id);if(initial.get("factory_id")!=null)scope.lockFactories(List.of(((Number)initial.get("factory_id")).longValue()));return jdbc.queryForMap("SELECT * FROM sys_user WHERE user_id=? AND del_flag='0' AND status='0' FOR UPDATE",id);}
 @Transactional(rollbackFor=Exception.class) public boolean update(Profile input){
  var old=lock();Long id=SecurityUtils.getUserId();if(!Objects.equals(((Number)old.get("version")).intValue(),input.version()))throw new ServiceException("人员资料已修改，请刷新后重试");
  String phone=input.phonenumber()==null?"":input.phonenumber().strip();boolean admin=SecurityUtils.isAdmin(id);
  if((!admin||!phone.isEmpty())&&!phone.matches("1[3-9][0-9]{9}"))throw new ServiceException("请输入大陆11位手机号");
  boolean changed=!Objects.equals(Objects.toString(old.get("phonenumber"),""),phone);String name=admin?"admin":phone;
  try{if(jdbc.update("UPDATE sys_user SET nick_name=?,phonenumber=?,user_name=?,email=?,sex=?,version=version+1,update_by=?,update_time=NOW() WHERE user_id=? AND version=?",input.nickName().strip(),phone,name,input.email(),input.sex(),SecurityUtils.getUsername(),id,input.version())!=1)throw new ServiceException("人员资料已修改，请刷新后重试");}catch(DuplicateKeyException e){throw new ServiceException("手机号或登录账号已被使用");}
  if(changed&&!admin)scope.invalidate(List.of(id));return changed&&!admin;
 }
 @Transactional(rollbackFor=Exception.class) public void password(Password input){
  PasswordPolicy.validate(input.newPassword());var old=lock();String hash=old.get("password").toString();
  if(!SecurityUtils.matchesPassword(input.oldPassword(),hash))throw new ServiceException("修改密码失败，旧密码错误");
  if(SecurityUtils.matchesPassword(input.newPassword(),hash))throw new ServiceException("新密码不能与旧密码相同");
  jdbc.update("UPDATE sys_user SET password=?,pwd_update_date=NOW(),version=version+1,update_by=?,update_time=NOW() WHERE user_id=?",SecurityUtils.encryptPassword(input.newPassword()),SecurityUtils.getUsername(),SecurityUtils.getUserId());scope.invalidate(List.of(SecurityUtils.getUserId()));
 }
}
