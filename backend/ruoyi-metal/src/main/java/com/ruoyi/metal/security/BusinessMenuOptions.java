package com.ruoyi.metal.security;
import java.util.*;
/** Role editor options for the implemented application, excluding scaffold-only modules. */
public final class BusinessMenuOptions {
 private BusinessMenuOptions(){}
 private static final Set<String> COMPONENTS=Set.of("metal/factory/index","metal/product/index","metal/production-line/index","metal/material/index","metal/process/index","metal/work-order/index","metal/quote-rule/index","metal/quotation/index","metal/warehouse/index","metal/inventory/index","metal/warehouse-order/index","metal/work-report/index","metal/warning-record/index","metal/staff/index","metal/role/index","audit/operations","audit/logins","audit/runtime","audit/config");
 private static boolean allowedPermission(String p){return !Set.of("metal:work-order:approve","metal:work-order:reject","metal:work-order:resubmit").contains(p)&&(p.startsWith("metal:")||Set.of("system:user:list","system:user:query","system:user:add","system:user:edit","system:user:remove","system:user:resetPwd","system:user:export","system:role:list","system:role:query","system:role:add","system:role:edit","system:role:remove","monitor:operlog:list","monitor:operlog:query","monitor:operlog:export","monitor:logininfor:list","monitor:runtime-log:list","monitor:runtime-log:query","monitor:runtime-log:export","monitor:log-config:query","monitor:log-config:edit").contains(p));}
 public static List<Map<String,Object>> filter(List<Map<String,Object>> rows,boolean admin,Set<String> owned){
  var index=new HashMap<String,Map<String,Object>>();rows.forEach(r->index.put(String.valueOf(r.get("id")),r));var kept=new HashSet<String>();var menus=new HashSet<String>();
  for(var row:rows){String c=Objects.toString(row.get("component"),""),p=Objects.toString(row.get("perms"),"");
   if("C".equals(row.get("menuType"))&&COMPONENTS.contains(c)&&(admin||!c.startsWith("audit/")||c.equals("audit/operations"))&&allowedPermission(p)&&(admin||owned.contains("*:*:*")||owned.contains(p))){String id=String.valueOf(row.get("id"));menus.add(id);kept.add(id);}
  }
  for(var row:rows){String p=Objects.toString(row.get("perms"),"");if("F".equals(row.get("menuType"))&&menus.contains(String.valueOf(row.get("parentId")))&&allowedPermission(p)&&(admin||owned.contains("*:*:*")||owned.contains(p)))kept.add(String.valueOf(row.get("id")));}
  for(String id:new ArrayList<>(kept)){var visited=new HashSet<String>();var row=index.get(id);while(row!=null&&visited.add(String.valueOf(row.get("id")))){row=index.get(String.valueOf(row.get("parentId")));if(row==null||!"M".equals(row.get("menuType"))||!Objects.toString(row.get("perms"),"").isBlank())break;kept.add(String.valueOf(row.get("id")));}}
  return rows.stream().filter(r->kept.contains(String.valueOf(r.get("id")))).map(r->{Map<String,Object> result=new LinkedHashMap<>(r);if(!kept.contains(String.valueOf(r.get("parentId"))))result.put("parentId","0");result.remove("component");result.remove("menuType");return result;}).toList();
 }
}
