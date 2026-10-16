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
import com.ruoyi.metal.catalog.StaffRecord;
import com.ruoyi.metal.service.StaffManagementService;
/**
 * 工作人员 HTTP 接口，负责请求映射、参数校验和接口权限声明，业务处理交给对应服务。
 */
@RestController @Validated @RequestMapping("/system/user")
public class StaffManagementController extends BaseController {
 private final StaffManagementService service; public StaffManagementController(StaffManagementService service){this.service=service;}
 /**
  * 查询列表并补充关联资料。
  */
 @GetMapping("/list") @PreAuthorize("@ss.hasPermi('system:user:list')") public TableDataInfo list(@RequestParam(defaultValue="1") @Min(1) int pageNum,@RequestParam(defaultValue="16") @Min(1) @Max(100) int pageSize, @ModelAttribute com.ruoyi.metal.catalog.ListQuery query){PageHelper.startPage(pageNum,pageSize);return getDataTable(service.list(query));}
 /**
  * 查询指定记录详情。
  */
 @GetMapping("/{id}") @PreAuthorize("@ss.hasPermi('system:user:query')") public AjaxResult detail(@PathVariable @Positive Long id){return success(service.detail(id));}
 /**
  * 新增记录并保存关联资料。
  */
 @PostMapping @Log(title="Staff",businessType=BusinessType.INSERT) @PreAuthorize("@ss.hasPermi('system:user:add')") public AjaxResult create(@Valid @RequestBody StaffRecord form){return success().put("id",service.create(form));}
 /**
  * 修改记录并更新关联资料。
  */
 @PutMapping @Log(title="Staff",businessType=BusinessType.UPDATE) @PreAuthorize("@ss.hasPermi('system:user:edit')") public AjaxResult edit(@Valid @RequestBody StaffRecord form){service.edit(form);return success();}
 /**
  * 提交批量删除请求，由业务层检查访问权限和引用限制。
  */
 @DeleteMapping("/{ids}") @Log(title="Staff",businessType=BusinessType.DELETE) @PreAuthorize("@ss.hasPermi('system:user:remove')") public AjaxResult delete(@PathVariable Long[] ids){service.delete(ids);return success();}
 /**
  * 提交启用或停用操作，由业务层校验版本和账号保护规则。
  */
 @PutMapping("/changeStatus") @Log(title="Staff状态",businessType=BusinessType.UPDATE) @PreAuthorize("@ss.hasPermi('system:user:edit')") public AjaxResult status(@RequestBody StaffRecord form){service.changeStatus(form);return success();}
 /**
  * 提交密码重置请求，由业务层校验密码并注销目标用户的旧登录。
  */
 @PutMapping("/resetPwd") @Log(title="重置密码",businessType=BusinessType.UPDATE) @PreAuthorize("@ss.hasPermi('system:user:resetPwd')") public AjaxResult password(@RequestBody StaffRecord form){service.resetPassword(form);return success();}
}
