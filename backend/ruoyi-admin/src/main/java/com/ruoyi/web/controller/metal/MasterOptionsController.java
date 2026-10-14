package com.ruoyi.web.controller.metal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.metal.security.FactoryScope;
/**
 * 业务选项 HTTP 接口，负责请求映射、参数校验和接口权限声明，业务处理交给对应服务。
 */
@RestController @RequestMapping("/metal/options") public class MasterOptionsController extends BaseController {
 private final JdbcTemplate jdbc;private final FactoryScope scope;
 public MasterOptionsController(JdbcTemplate jdbc,FactoryScope scope){this.jdbc=jdbc;this.scope=scope;}
 /**
  * 获取当前用户可访问的工厂选项。
  */
 @GetMapping("/factories") @PreAuthorize("@ss.hasAnyPermi('metal:production-line:add,metal:production-line:edit,system:user:add,system:user:edit,system:role:add,system:role:edit')")
 public AjaxResult factories(){Long id=scope.currentFactory();return success(id==null?jdbc.queryForList("SELECT CAST(factory_id AS CHAR) id,factory_name name FROM metal_factory WHERE del_flag='0' ORDER BY factory_id"):jdbc.queryForList("SELECT CAST(factory_id AS CHAR) id,factory_name name FROM metal_factory WHERE del_flag='0' AND factory_id=?",id));}
 /**
  * 获取可选择的产品资料。
  */
 @GetMapping("/products") @PreAuthorize("@ss.hasAnyPermi('metal:production-line:add,metal:production-line:edit')") public AjaxResult products(){return success(jdbc.queryForList("SELECT CAST(product_id AS CHAR) id,product_name name FROM metal_product WHERE del_flag='0' ORDER BY product_id"));}
 /**
  * 获取可选择的工序资料或产品工序计划。
  */
 @GetMapping("/processes") @PreAuthorize("@ss.hasAnyPermi('metal:product:add,metal:product:edit')") public AjaxResult processes(){return success(jdbc.queryForList("SELECT CAST(process_id AS CHAR) id,process_name name FROM metal_process WHERE del_flag='0' ORDER BY process_id"));}
 /**
  * 获取指定工厂的有效角色选项。
  */
 @GetMapping("/roles") @PreAuthorize("@ss.hasAnyPermi('system:user:add,system:user:edit')") public AjaxResult roles(@RequestParam Long factoryId){scope.check(factoryId);return success(jdbc.queryForList("SELECT CAST(role_id AS CHAR) id,role_name name FROM sys_role WHERE factory_id=? AND del_flag='0' AND status='0' AND role_id<>1 ORDER BY role_sort,role_id",factoryId));}
 /**
  * 获取当前用户可以授予的菜单和按钮权限选项。
  */
 @GetMapping("/menus") @PreAuthorize("@ss.hasAnyPermi('system:role:add,system:role:edit')") public AjaxResult menus(){Set<String> owned=com.ruoyi.common.utils.SecurityUtils.getLoginUser().getPermissions();List<Map<String,Object>> all=jdbc.queryForList("SELECT CAST(menu_id AS CHAR) id,CAST(parent_id AS CHAR) parentId,menu_name name,perms FROM sys_menu WHERE status='0' ORDER BY order_num,menu_id");return success(all.stream().filter(m->scope.isAdmin()||m.get("perms")==null||m.get("perms").toString().isBlank()||owned.contains(m.get("perms"))).toList());}
}
