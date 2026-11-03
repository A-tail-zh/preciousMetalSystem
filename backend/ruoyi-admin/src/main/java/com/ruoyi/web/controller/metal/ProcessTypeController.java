package com.ruoyi.web.controller.metal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.metal.service.ProcessTypeService;
@RestController
@RequestMapping("/metal/process/types")
public class ProcessTypeController extends BaseController {
 private final ProcessTypeService service;
 public ProcessTypeController(ProcessTypeService service){this.service=service;}
 public record CreateType(@NotBlank(message="请输入工序类型名称") @Size(max=100,message="工序类型名称最多100个字符") String name){}
 @GetMapping @PreAuthorize("@ss.hasAnyPermi('metal:process:list,metal:process:add,metal:process:edit')")
 public AjaxResult list(){return success(service.list());}
 @PostMapping @PreAuthorize("@ss.hasPermi('metal:process:add')") @Log(title="工序类型",businessType=BusinessType.INSERT)
 public AjaxResult create(@Valid @RequestBody CreateType form){return success(service.create(form.name(),SecurityUtils.getUsername()));}
}
