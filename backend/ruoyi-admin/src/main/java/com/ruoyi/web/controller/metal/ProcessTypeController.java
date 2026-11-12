package com.ruoyi.web.controller.metal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
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
 public record CreateType(@NotBlank(message="请输入工序类型名称") @Size(max=100,message="工序类型名称最多100个字符") String name, @Positive Long factoryId){}
 public record EditType(@NotBlank(message="请输入工序类型名称") @Size(max=100,message="工序类型名称最多100个字符") String name, @Positive Long factoryId, @NotNull @PositiveOrZero Integer version){}
 @GetMapping @PreAuthorize("@ss.hasAnyPermi('metal:process:list,metal:process:query,metal:process:add,metal:process:edit,metal:process:remove,metal:process-type:list,metal:process-type:add,metal:process-type:edit,metal:process-type:remove')")
 public AjaxResult list(@RequestParam(required=false) Long factoryId){return success(service.list(factoryId));}
 @PostMapping @PreAuthorize("@ss.hasPermi('metal:process-type:add')") @Log(title="工序类型",businessType=BusinessType.INSERT)
 public AjaxResult create(@Valid @RequestBody CreateType form){return success(service.create(form.name(),SecurityUtils.getUsername(),form.factoryId()));}
 @PutMapping("/{id}") @PreAuthorize("@ss.hasPermi('metal:process-type:edit')") @Log(title="工序类型",businessType=BusinessType.UPDATE)
 public AjaxResult edit(@PathVariable Long id,@Valid @RequestBody EditType form){return success(service.edit(id,form.name(),form.version(),form.factoryId()));}
 @DeleteMapping("/{id}") @PreAuthorize("@ss.hasPermi('metal:process-type:remove')") @Log(title="工序类型",businessType=BusinessType.DELETE)
 public AjaxResult remove(@PathVariable Long id,@RequestParam Long factoryId,@RequestParam Integer version){service.remove(id,version,factoryId);return success();}
}
