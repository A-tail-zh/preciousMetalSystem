package com.ruoyi.web.controller.metal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.metal.catalog.*;
import com.ruoyi.metal.service.CustomerService;

@RestController @Validated @RequestMapping("/metal/customer")
public class CustomerController extends BaseController {
    private final CustomerService service;
    public CustomerController(CustomerService service) { this.service = service; }

    @GetMapping("/list") @PreAuthorize("@ss.hasPermi('metal:customer:list')")
    @Log(title = "客户管理", businessType = BusinessType.OTHER, isSaveResponseData = false)
    public TableDataInfo list(@RequestParam(defaultValue = "1") @Min(1) int pageNum,
                             @RequestParam(defaultValue = "16") @Min(1) @Max(100) int pageSize,
                             @ModelAttribute ListQuery query) {
        return com.ruoyi.common.utils.PageUtils.withPage(pageNum, pageSize, () -> getDataTable(service.list(query)));
    }

    @GetMapping("/{id}") @PreAuthorize("@ss.hasPermi('metal:customer:query')")
    @Log(title = "客户管理", businessType = BusinessType.OTHER, isSaveResponseData = false)
    public AjaxResult detail(@PathVariable @Positive Long id) { return success(service.detail(id)); }

    @GetMapping("/{id}/edit-context") @PreAuthorize("@ss.hasPermi('metal:customer:edit')")
    public AjaxResult editContext(@PathVariable @Positive Long id) { return success(service.detail(id)); }

    @PostMapping @PreAuthorize("@ss.hasPermi('metal:customer:add')")
    @Log(title = "客户管理", businessType = BusinessType.INSERT)
    public AjaxResult create(@Valid @RequestBody CustomerRecord form) { return success().put("id", service.create(form)); }

    @PutMapping @PreAuthorize("@ss.hasPermi('metal:customer:edit')")
    @Log(title = "客户管理", businessType = BusinessType.UPDATE)
    public AjaxResult edit(@Valid @RequestBody CustomerRecord form) { service.edit(form); return success(); }

    @DeleteMapping("/{ids}") @PreAuthorize("@ss.hasPermi('metal:customer:remove')")
    @Log(title = "客户管理", businessType = BusinessType.DELETE)
    public AjaxResult delete(@PathVariable Long[] ids) { service.delete(ids); return success(); }

    @ExceptionHandler(ServiceException.class)
    public AjaxResult businessError(ServiceException error) {
        if ("编号、编码或名称已存在".equals(error.getMessage())) {
            String message="同厂客户名称和客户公司名称已存在";
            return AjaxResult.error(message).put("fieldErrors",java.util.List.of(java.util.Map.of("field","companyName","code","Duplicate","message",message)));
        }
        return AjaxResult.error(error.getCode()==null?500:error.getCode(),error.getMessage());
    }
}
