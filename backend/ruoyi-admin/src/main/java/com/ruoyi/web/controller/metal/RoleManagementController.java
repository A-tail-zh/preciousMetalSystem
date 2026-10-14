package com.ruoyi.web.controller.metal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import com.github.pagehelper.PageHelper;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.metal.catalog.RoleRecord;
import com.ruoyi.metal.service.RoleManagementService;
/**
 * 角色权限 HTTP 接口，负责请求映射、参数校验和接口权限声明，业务处理交给对应服务。
 */
@RestController @Validated @RequestMapping("/system/role")
public class RoleManagementController extends BaseController {
 private final RoleManagementService service; public RoleManagementController(RoleManagementService service){this.service=service;}
 /**
  * 查询列表并补充关联资料。
  */
 @GetMapping("/list") @PreAuthorize("@ss.hasPermi('system:role:list')") public TableDataInfo list(@RequestParam(defaultValue="1") @Min(1) int pageNum,@RequestParam(defaultValue="16") @Min(1) @Max(100) int pageSize){PageHelper.startPage(pageNum,pageSize);return getDataTable(service.list());}
 /**
  * 查询指定记录详情。
  */
 @GetMapping("/{id}") @PreAuthorize("@ss.hasPermi('system:role:query')") public AjaxResult detail(@PathVariable @Positive Long id){return success(service.detail(id));}
 /**
  * 新增记录并保存关联资料。
  */
 @PostMapping @Log(title="Role",businessType=BusinessType.INSERT) @PreAuthorize("@ss.hasPermi('system:role:add')") public AjaxResult create(@Valid @RequestBody RoleRecord form){return success().put("id",service.create(form));}
 /**
  * 修改记录并更新关联资料。
  */
 @PutMapping @Log(title="Role",businessType=BusinessType.UPDATE) @PreAuthorize("@ss.hasPermi('system:role:edit')") public AjaxResult edit(@Valid @RequestBody RoleRecord form){service.edit(form);return success();}
 /**
  * 提交批量删除请求，由业务层检查访问权限和引用限制。
  */
 @DeleteMapping("/{ids}") @Log(title="Role",businessType=BusinessType.DELETE) @PreAuthorize("@ss.hasPermi('system:role:remove')") public AjaxResult delete(@PathVariable Long[] ids){service.delete(ids);return success();}
 /**
  * 提交启用或停用操作，由业务层校验版本和账号保护规则。
  */
 @PutMapping("/changeStatus") @Log(title="Role状态",businessType=BusinessType.UPDATE) @PreAuthorize("@ss.hasPermi('system:role:edit')") public AjaxResult status(@RequestBody RoleRecord form){service.changeStatus(form);return success();}
 
}
