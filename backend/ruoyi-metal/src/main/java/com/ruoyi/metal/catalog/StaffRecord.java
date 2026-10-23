package com.ruoyi.metal.catalog;
import java.util.*;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.*;
/**
 * 工作人员数据模型，承载业务字段及适用的参数校验规则。
 */
public class StaffRecord extends MasterRecord {
 @NotBlank @Pattern(regexp="1[3-9][0-9]{9}")  private String phonenumber;
 @NotBlank @Size(max=100)  private String nickName;
 @Positive @JsonFormat(shape=JsonFormat.Shape.STRING) private Long factoryId;
 @NotBlank @Pattern(regexp="[01]")  private String status;
 @Size(min=8,max=72) @JsonProperty(access=JsonProperty.Access.WRITE_ONLY)  private String password;
  private String userName;
  @tools.jackson.databind.annotation.JsonSerialize(contentUsing=tools.jackson.databind.ser.std.ToStringSerializer.class) @NotNull private List<@NotNull @Positive Long> roleIds=new ArrayList<>();
  private List<String> roleNames=new ArrayList<>();
  private String factoryName;
 public String getPhonenumber(){return phonenumber;} public void setPhonenumber(String v){phonenumber=v==null?null:v.strip();}
 public String getNickName(){return nickName;} public void setNickName(String v){nickName=v==null?null:v.strip();}
 public Long getFactoryId(){return factoryId;} public void setFactoryId(Long v){factoryId=v;}
 public String getStatus(){return status;} public void setStatus(String v){status=v==null?null:v.strip();}
 public String getPassword(){return password;} public void setPassword(String v){password=v;}
 public String getUserName(){return userName;} public void setUserName(String v){userName=v==null?null:v.strip();}
 public List<Long> getRoleIds(){return roleIds;} public void setRoleIds(List<Long> v){roleIds=v;}
 public List<String> getRoleNames(){return roleNames;} public void setRoleNames(List<String> v){roleNames=v;}
 public String getFactoryName(){return factoryName;} public void setFactoryName(String v){factoryName=v==null?null:v.strip();}
}
