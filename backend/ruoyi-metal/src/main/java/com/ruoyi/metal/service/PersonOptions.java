package com.ruoyi.metal.service;
/** SQL fragments for scoped person options. Aliases are server-owned constants. */
public final class PersonOptions {
 private PersonOptions(){}
 public static String roles(String alias){if(!java.util.Set.of("u","sys_user").contains(alias))throw new IllegalArgumentException("Invalid person alias");return "COALESCE((SELECT GROUP_CONCAT(DISTINCT r.role_name ORDER BY r.role_name SEPARATOR '、') FROM sys_user_role ur JOIN sys_role r ON r.role_id=ur.role_id WHERE ur.user_id="+alias+".user_id AND (r.factory_id="+alias+".factory_id OR r.role_id=1) AND r.del_flag='0' AND r.status='0'),'')";}
 public static String label(String alias){return "CONCAT(COALESCE(NULLIF("+roles(alias)+",''),'未分配角色'),'｜',COALESCE(NULLIF("+alias+".nick_name,''),"+alias+".user_name),'｜',COALESCE(NULLIF("+alias+".phonenumber,''),'未填写手机号'))";}
}
