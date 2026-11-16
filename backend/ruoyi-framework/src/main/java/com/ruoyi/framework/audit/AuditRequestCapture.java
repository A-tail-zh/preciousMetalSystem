package com.ruoyi.framework.audit;

import java.lang.reflect.*;
import java.util.*;
import com.alibaba.fastjson2.JSON;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Request display is independent of the DTO used to locate business snapshots. */
public final class AuditRequestCapture {
 private AuditRequestCapture(){}
 private static final tools.jackson.databind.json.JsonMapper NORMALIZER=tools.jackson.databind.json.JsonMapper.builder().build();
 private static boolean structured(Object x){if(x==null)return false;String type=x.getClass().getName();return !(x instanceof MultipartFile)&&!type.startsWith("jakarta.")&&!type.startsWith("org.springframework.")&&!(x instanceof Number||x instanceof String||x instanceof Boolean||x instanceof Enum<?>);}
 public static void controller(AuditContext c,Method method,Object[] args,List<Map<String,Object>> files){
  var parameters=new LinkedHashMap<String,Object>();Object body=null;boolean found=false;
  if(method!=null){var declarations=method.getParameters();for(int i=0;i<Math.min(declarations.length,args.length);i++){
   var p=declarations[i];if(p.isAnnotationPresent(RequestBody.class)){body=args[i];found=true;continue;}
   var path=p.getAnnotation(PathVariable.class);var param=p.getAnnotation(RequestParam.class);
   if((path!=null||param!=null)&&!(args[i] instanceof MultipartFile)){
    String name=path!=null?(path.name().isEmpty()?path.value():path.name()):(param.name().isEmpty()?param.value():param.name());
    parameters.put(name.isEmpty()?"arg"+i:name,args[i]);
   }
  }}
  if(!found&&files.isEmpty()&&parameters.isEmpty())return;
  c.httpCaptured=true;c.snapshotInput=body;
  var value=new LinkedHashMap<String,Object>();value.put("parameters",parameters);if(found)value.put("body",body);if(!files.isEmpty())value.put("files",files);
  c.request=bounded(value);
 }
 public static Object service(AuditContext c,Object[] args){
  if(c.httpCaptured){if(c.snapshotInput==null)for(Object value:args)if(structured(value)){c.snapshotInput=value;break;}return c.snapshotInput;}
  Object body=null;var parameters=new LinkedHashMap<String,Object>();
  for(int i=0;i<args.length;i++){Object value=args[i];if(structured(value)&&body==null)body=value;else if(value==null||value instanceof String||value instanceof Number||value instanceof Boolean||value instanceof Enum<?>)parameters.put("arg"+i,value);}
  var display=new LinkedHashMap<String,Object>();display.put("parameters",parameters);if(body!=null)display.put("body",body);
  if(!c.request.isEmpty())display.put("metadata",c.request);
  c.request=bounded(display);c.snapshotInput=body!=null?body:args.length==0?null:args[0];return c.snapshotInput;
 }
 static String bounded(Object value){
  String safe;try{safe=JSON.toJSONString(AuditRedactor.redactComplete(NORMALIZER.convertValue(value,Object.class)));}catch(RuntimeException unavailable){safe="{\"unavailable\":true}";}if(safe.length()<=4096)return safe;
  var summary=new LinkedHashMap<String,Object>();
  try{var object=JSON.parseObject(safe);Object body=object.get("body");if(body instanceof Map<?,?> map){for(String key:List.of("requestId","version","reason","completedQuantity","quantity","netWeight"))if(map.containsKey(key))summary.put(key,map.get(key));}}catch(RuntimeException ignored){}
  String important=AuditRedactor.json(summary,2048);int length=Math.min(1200,safe.length());String output;
  do{output=JSON.toJSONString(Map.of("truncated",true,"operation",important,"preview",safe.substring(0,length)));length/=2;}while(output.length()>4096&&length>0);
  return output;
 }
}
