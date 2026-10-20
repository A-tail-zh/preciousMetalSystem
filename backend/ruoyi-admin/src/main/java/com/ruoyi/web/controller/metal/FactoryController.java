package com.ruoyi.web.controller.metal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.github.pagehelper.PageHelper;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.metal.dto.FactoryRequest;
import com.ruoyi.metal.service.FactoryService;

/**
 * 工厂 HTTP 接口，负责请求映射、参数校验和接口权限声明，业务处理交给对应服务。
 */
@RestController
@Validated
@RequestMapping("/metal/factory")
public class FactoryController extends BaseController {
    private final FactoryService service;
    public FactoryController(FactoryService service) { this.service = service; }

    /**
     * 查询列表并补充关联资料。
     */
    @GetMapping("/list")
    @Log(title = "工厂管理", businessType = BusinessType.OTHER, isSaveResponseData = false)
    @PreAuthorize("@ss.hasPermi('metal:factory:list')")
    public TableDataInfo list(@RequestParam(defaultValue = "1") @Min(1) int pageNum,
                             @RequestParam(defaultValue = "16") @Min(1) @Max(100) int pageSize, @ModelAttribute com.ruoyi.metal.catalog.ListQuery query) {
        return com.ruoyi.common.utils.PageUtils.withPage(pageNum, pageSize, () -> getDataTable(service.list(query)));
    }
    /**
     * 获取当前业务可选择的关联资料。
     */
    @GetMapping("/line-options")
    @Log(title = "工厂管理", businessType = BusinessType.OTHER, isSaveResponseData = false)
    @PreAuthorize("@ss.hasAnyPermi('metal:factory:add,metal:factory:edit')")
    public AjaxResult options(@RequestParam(required = false) @Positive Long factoryId) {
        return success(service.options(factoryId));
    }
    /**
     * 查询指定记录详情；业务层执行相应的访问权限检查。
     */
    @GetMapping("/{id}")
    @Log(title = "工厂管理", businessType = BusinessType.OTHER, isSaveResponseData = false)
    @PreAuthorize("@ss.hasPermi('metal:factory:query')")
    public AjaxResult detail(@PathVariable @Positive Long id) { return success(service.detail(id)); }

    /**
     * 新增记录并保存关联资料。
     */
    @PostMapping
    @PreAuthorize("@ss.hasPermi('metal:factory:add')")
    @Log(title = "工厂管理", businessType = BusinessType.INSERT)
    public AjaxResult create(@Valid @RequestBody FactoryRequest form) {
        return success().put("factoryId", service.create(form, getUsername()));
    }
    /**
     * 修改记录并更新关联资料。
     */
    @PutMapping
    @PreAuthorize("@ss.hasPermi('metal:factory:edit')")
    @Log(title = "工厂管理", businessType = BusinessType.UPDATE)
    public AjaxResult edit(@Valid @RequestBody FactoryRequest form) {
        service.edit(form, getUsername());
        return success();
    }
    /**
     * 批量删除记录；业务层校验访问权限和引用限制。
     */
    @DeleteMapping("/{ids}")
    @PreAuthorize("@ss.hasPermi('metal:factory:remove')")
    @Log(title = "工厂管理", businessType = BusinessType.DELETE)
    public AjaxResult delete(@PathVariable Long[] ids) {
        service.delete(ids, getUsername());
        return success();
    }
}
