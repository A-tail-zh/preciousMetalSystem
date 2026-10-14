package com.ruoyi.framework.audit;

import java.util.*;
import java.util.regex.Pattern;
import com.alibaba.fastjson2.JSON;

public final class AuditRedactor {
 private AuditRedactor() {}
 private static final Pattern AUTH = Pattern.compile("(?i)\\b((?:proxy-)?authorization)([ \\t:=]+)(?:bearer|basic)[ \\t]+[^\\s,;|\\\"']+");
 private static final Pattern COOKIE = Pattern.compile("(?i)\\b((?:set-)?cookie)([ \\t]*[:=][ \\t]*)[^\\r\\n|,]*?(?=$|[\\r\\n|,]|[ \\t]+(?:status|method|path|duration|traceId|message)[ \\t]*[:=])");
 public static boolean secret(String key) {String k=key.toLowerCase(Locale.ROOT).replaceAll("[^a-z]","");return k.contains("password")||k.contains("token")||k.contains("secret")||k.contains("authorization")||k.contains("cookie")||k.equals("code")||k.contains("captcha")||k.contains("credential");}
 /** Bounded snapshots for display. */
 public static Object redact(Object x) {return redact(x,true);}
 /** Complete records for archival: never truncate collections, including JSON strings. */
 public static Object redactComplete(Object x) {return redact(x,false);}
 private static Object redact(Object x,boolean bounded) {
  if(x instanceof Map<?,?> m){Map<String,Object> out=new LinkedHashMap<>();m.forEach((k,v)->out.put(k.toString(),secret(k.toString())?"[REDACTED]":redact(v,bounded)));return out;}
  if(x instanceof Collection<?> c)return c.stream().limit(bounded?100:Long.MAX_VALUE).map(v->redact(v,bounded)).toList();
  if(x instanceof String s)return text(s,bounded);
  if(x instanceof java.math.BigDecimal||x instanceof Long)return x.toString();
  return x;
 }
 public static String text(String s) {return text(s,true);}
 private static String text(String s,boolean bounded) {
  if(s==null)return "";
  if(s.startsWith("{")||s.startsWith("[")){try{return JSON.toJSONString(redact(JSON.parse(s),bounded));}catch(Exception ignored){}}
  s=AUTH.matcher(s).replaceAll("$1$2[REDACTED]");
  s=COOKIE.matcher(s).replaceAll("$1$2[REDACTED]");
  return s.replaceAll("(?i)(password|passwd|pwd|token|secret|authorization|cookie)([ :=]+)[^\\s,;|]+","$1$2[REDACTED]").replaceAll("(?<![0-9])(1[0-9]{2})[0-9]{4}([0-9]{4})(?![0-9])","$1****$2").replaceAll("(?i)(jdbc:[^ ]*?://)[^ /:@]+:[^ /@]+@","$1[REDACTED]@");
 }
 public static String json(Object x,int max){Object value=x;try{if(!(x instanceof Map||x instanceof Collection||x instanceof String))value=JSON.parse(JSON.toJSONString(x));}catch(Exception ignored){value="[unavailable]";}String s=JSON.toJSONString(redact(value));return s.length()>max?s.substring(0,max):s;}
}
