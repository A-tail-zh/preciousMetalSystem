package com.ruoyi.metal.catalog;
import java.util.*;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.*;
/**
 * 角色数据模型，承载业务字段及适用的参数校验规则。
 */
public class RoleRecord extends MasterRecord {
 @NotBlank @Size(max=100)  private String roleName;
 @Positive @JsonFormat(shape=JsonFormat.Shape.STRING) private Long factoryId;
 @NotNull @Min(0)  private Integer roleSort;
 @NotBlank @Pattern(regexp="[01]")  private String status;
 @JsonIgnore private String roleKey;
  @tools.jackson.databind.annotation.JsonSerialize(contentUsing=tools.jackson.databind.ser.std.ToStringSerializer.class) private List<Long> menuIds=new ArrayList<>();
  private String factoryName;
 public String getRoleName(){return roleName;} public void setRoleName(String v){roleName=v==null?null:v.strip();}
 public Long getFactoryId(){return factoryId;} public void setFactoryId(Long v){factoryId=v;}
 public Integer getRoleSort(){return roleSort;} public void setRoleSort(Integer v){roleSort=v;}
 public String getStatus(){return status;} public void setStatus(String v){status=v==null?null:v.strip();}
 public String getRoleKey(){return roleKey;} public void setRoleKey(String v){roleKey=v==null?null:v.strip();}
 public List<Long> getMenuIds(){return menuIds;} public void setMenuIds(List<Long> v){menuIds=v;}
 public String getFactoryName(){return factoryName;} public void setFactoryName(String v){factoryName=v==null?null:v.strip();}
}
