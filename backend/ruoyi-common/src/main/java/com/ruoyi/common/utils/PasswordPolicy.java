package com.ruoyi.common.utils;
import java.nio.charset.StandardCharsets;import com.ruoyi.common.exception.ServiceException;
public final class PasswordPolicy {
 private PasswordPolicy(){}
 public static boolean valid(String value){return value!=null&&!value.isBlank()&&value.length()>=8&&value.getBytes(StandardCharsets.UTF_8).length<=72;}
 public static void validate(String value){if(!valid(value))throw new ServiceException("密码至少8位，最多72个UTF-8字节");}
}
