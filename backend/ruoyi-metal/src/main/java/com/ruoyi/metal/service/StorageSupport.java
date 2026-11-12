package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.*;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.metal.security.FactoryScope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;

/** Shared boundary parsing for the new storage commands, not the retired warehouse workflow. */
public final class StorageSupport {
 private StorageSupport(){}
 public static ServiceException fail(String message){return new ServiceException(message,400);}
 public static String text(Object value){return value==null?"":value.toString().trim();}
 public static long id(Object value){try{String s=text(value);if(!s.matches("[1-9][0-9]{0,18}"))throw fail("对象编号无效");return Long.parseLong(s);}catch(NumberFormatException e){throw fail("对象编号无效");}}
 public static Long optionalId(Object value){return value==null||text(value).isEmpty()?null:id(value);}
 public static int version(Object value){try{int v=Integer.parseInt(text(value));if(v<0)throw fail("版本号无效");return v;}catch(NumberFormatException e){throw fail("请提供最新版本号");}}
 public static BigDecimal decimal(Object value,boolean signed){String s=text(value);if(!s.matches((signed?"-?":"")+"\\d{1,14}(\\.\\d{1,2})?"))throw fail("数量须为最多14位整数和2位小数的十进制数");return new BigDecimal(s).setScale(2);}
 public static BigDecimal quantity(Object value){BigDecimal v=decimal(value,false);if(v.stripTrailingZeros().scale()>0||v.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE))>0)throw fail("件数须为整数且不能超出范围");return v;}
 public static String date(Object value){try{return LocalDate.parse(text(value)).toString();}catch(Exception e){throw fail("单据日期无效");}}
 public static String reason(Object value){String r=text(value);if(r.isBlank()||r.length()>500)throw fail("请填写500字以内的原因");return r;}
 public static String uuid(Object value){String r=text(value);if(!r.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}"))throw fail("请求标识无效");return r.toLowerCase(Locale.ROOT);}
 @SuppressWarnings("unchecked") public static List<Map<String,Object>> maps(Object value){if(!(value instanceof List<?> list)||list.size()>500)throw fail("明细格式无效，最多500条");var out=new ArrayList<Map<String,Object>>();for(Object item:list){if(!(item instanceof Map<?,?>))throw fail("明细格式无效");out.add(new LinkedHashMap<>((Map<String,Object>)item));}return out;}
 public static Map<String,Object> one(JdbcTemplate jdbc,String sql,Object...args){var rows=jdbc.queryForList(sql,args);if(rows.isEmpty())throw new ServiceException("记录不存在或不属于当前工厂",404);return rows.get(0);}
 public static long insert(JdbcTemplate jdbc,String sql,Object...args){var holder=new GeneratedKeyHolder();jdbc.update(c->{var p=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);return p;},holder);return Objects.requireNonNull(holder.getKey()).longValue();}
 public static void allow(FactoryScope scope,String... permissions){if(scope.isAdmin())return;for(String p:permissions)if(SecurityUtils.hasPermi(p))return;throw new ServiceException("没有此操作权限",403);}
 private static Object canonical(Object v){if(v instanceof Map<?,?> m){var result=new TreeMap<String,Object>();m.forEach((k,x)->result.put(k.toString(),canonical(x)));return result;}if(v instanceof List<?> list)return list.stream().map(StorageSupport::canonical).toList();return v;}
 public static String hash(Object value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JSON.toJSONBytes(canonical(value))));}catch(Exception e){throw new IllegalStateException(e);}}
 public static boolean personCan(JdbcTemplate jdbc,long user,long factory,String permission){return jdbc.queryForObject("SELECT COUNT(*) FROM sys_user u WHERE u.user_id=? AND u.factory_id=? AND u.del_flag='0' AND u.status='0' AND EXISTS(SELECT 1 FROM sys_user_role ur JOIN sys_role r ON r.role_id=ur.role_id JOIN sys_role_menu rm ON rm.role_id=r.role_id JOIN sys_menu m ON m.menu_id=rm.menu_id WHERE ur.user_id=u.user_id AND r.status='0' AND r.del_flag='0' AND m.status='0' AND (m.perms=? OR m.perms='*:*:*'))",Integer.class,user,factory,permission)>0;}
 public static void reviewer(JdbcTemplate jdbc,long factory,long reviewer,long creator,String permission){if(!personCan(jdbc,reviewer,factory,permission))throw fail("复验人须为本厂启用且具有对应复验权限的人员");}
}
