package com.ruoyi.web.controller.metal;
import java.util.*;import org.springframework.web.bind.annotation.*;import org.springframework.jdbc.core.JdbcTemplate;import com.ruoyi.metal.security.FactoryScope;import com.ruoyi.common.exception.ServiceException;import com.ruoyi.common.utils.SecurityUtils;import com.ruoyi.metal.catalog.ListQuery;
@RestController @RequestMapping("/metal/filter-options") public class FilterOptionsController {
 private final JdbcTemplate jdbc;private final FactoryScope scope;public FilterOptionsController(JdbcTemplate jdbc,FactoryScope scope){this.jdbc=jdbc;this.scope=scope;}

 /** Candidate labels only; table and column identifiers are fixed by server-side allowlists. */
 @GetMapping("/suggestions") public Map<String,Object> suggestions(@RequestParam String module,@RequestParam String field,@RequestParam(defaultValue="") String keyword,@RequestParam(required=false) Long factoryId){
  var permissions=Map.of("factory","metal:factory:list","product","metal:product:list","line","metal:production-line:list","material","metal:material:list","process","metal:process:list","role","system:role:list","warehouse-order","metal:warehouse-order:list");
  String permission=permissions.get(module);var granted=SecurityUtils.getLoginUser().getPermissions();
  if(permission==null||(!scope.isAdmin()&&!granted.contains("*:*:*")&&!granted.contains(permission)))throw new ServiceException("没有筛选权限",403);
  if(keyword.length()>500)throw new ServiceException("关键词过长");
  Long own=scope.currentFactory();if(factoryId!=null){if(factoryId<=0)throw new ServiceException("工厂无效");scope.check(factoryId);own=factoryId;}
  String table,column,where="1=1",factoryColumn="factory_id";
  switch(module+":"+field){
   case "factory:name"->{table="metal_factory";column="factory_name";where="del_flag='0'";}
   case "factory:address"->{table="metal_factory";column="address";where="del_flag='0'";}
   case "product:code"->{table="metal_product";column="product_code";where="del_flag='0'";factoryColumn=null;}
   case "product:name"->{table="metal_product";column="product_name";where="del_flag='0'";factoryColumn=null;}
   case "line:code"->{table="metal_production_line";column="production_line_code";}
   case "line:name"->{table="metal_production_line";column="production_line_name";}
   case "material:code"->{table="metal_material";column="material_code";where="del_flag='0'";factoryColumn=null;}
   case "material:specification"->{table="metal_material";column="specification";where="del_flag='0'";factoryColumn=null;}
   case "process:name"->{table="metal_process";column="process_name";where="del_flag='0'";factoryColumn=null;}
   case "role:name"->{table="sys_role";column="role_name";where="del_flag='0'";}
   case "warehouse-order:orderNo"->{table="metal_warehouse_order";column="order_no";}
   case "warehouse-order:workOrderNo"->{table="metal_work_order w";column="w.order_no";factoryColumn="w.factory_id";where="EXISTS (SELECT 1 FROM metal_warehouse_order o WHERE o.work_order_id=w.work_order_id AND o.factory_id=w.factory_id)";}
   default->throw new ServiceException("筛选字段无效");
  }
  String pattern=ListQuery.pattern(keyword);if(pattern==null)return Map.of("code",200,"rows",List.of());
  var args=new ArrayList<Object>();if(own!=null&&factoryColumn!=null){where+=" AND "+factoryColumn+"=?";args.add(own);}
  where+=" AND "+column+" LIKE ? ESCAPE '!'";args.add(pattern);
  var rows=jdbc.queryForList("SELECT DISTINCT "+column+" AS value FROM "+table+" WHERE "+where+" ORDER BY "+column+" LIMIT 20",args.toArray());return Map.of("code",200,"rows",rows);
 }
 @GetMapping("/{kind}") public Map<String,Object> options(@PathVariable String kind,@RequestParam String module,@RequestParam(defaultValue="") String keyword,@RequestParam(defaultValue="1") int pageNum,@RequestParam(required=false) Long factoryId){
  var modules=Map.of("staff","system:user:list","role","system:role:list","line","metal:production-line:list","warehouse-order","metal:warehouse-order:list");var permissions=SecurityUtils.getLoginUser().getPermissions();String required=modules.get(module);if(required==null||(!scope.isAdmin()&&!permissions.contains("*:*:*")&&!permissions.contains(required)))throw new ServiceException("没有筛选权限",403);
  Set<String> allowed=switch(module){case "staff"->Set.of("factories","roles");case "role"->Set.of("factories");case "line"->Set.of("factories","products");default->Set.of("factories","warehouses","people");};if(!allowed.contains(kind))throw new ServiceException("筛选选项无效");if(pageNum<1||pageNum>100000||keyword.length()>100)throw new ServiceException("选项查询参数无效");Long own=scope.currentFactory();if(factoryId!=null){if(factoryId<=0)throw new ServiceException("工厂无效");scope.check(factoryId);own=factoryId;}
  String table,id,label,where="1=1";switch(kind){case "factories"->{table="metal_factory";id="factory_id";label="factory_name";where="del_flag='0'";}case "roles"->{table="sys_role";id="role_id";label="role_name";where="del_flag='0'";}case "products"->{table="metal_product";id="product_id";label="CONCAT(product_code,' / ',product_name)";where="del_flag='0'";}case "warehouses"->{table="metal_warehouse";id="warehouse_id";label="name";}default->{table="sys_user";id="user_id";label="CONCAT(nick_name,' / ',user_name)";}}
  var args=new ArrayList<Object>();if(own!=null&&!kind.equals("products")){if(kind.equals("people")){where+=" AND (factory_id=? OR EXISTS (SELECT 1 FROM metal_warehouse_order o WHERE o.factory_id=? AND (o.operator_id=sys_user.user_id OR o.keeper_id=sys_user.user_id OR o.recipient_id=sys_user.user_id OR JSON_CONTAINS(COALESCE(o.operator_ids,JSON_ARRAY()),JSON_QUOTE(CAST(sys_user.user_id AS CHAR))))))";args.add(own);args.add(own);}else{where+=" AND factory_id=?";args.add(own);}}
  String pattern=ListQuery.pattern(keyword);if(pattern!=null){where+=" AND "+label+" LIKE ? ESCAPE '!'";args.add(pattern);}args.add((pageNum-1)*20);var rows=jdbc.queryForList("SELECT CAST("+id+" AS CHAR) id,"+label+" name FROM "+table+" WHERE "+where+" ORDER BY "+id+" LIMIT 21 OFFSET ?",args.toArray());return Map.of("code",200,"rows",rows.stream().limit(20).toList(),"hasMore",rows.size()>20);
 }
}
