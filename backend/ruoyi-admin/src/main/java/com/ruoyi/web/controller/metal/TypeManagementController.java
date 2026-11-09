package com.ruoyi.web.controller.metal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.metal.service.TypeManagementService;

@RestController @Validated @RequestMapping("/metal/type-management")
public class TypeManagementController {
    private final TypeManagementService service;
    public TypeManagementController(TypeManagementService service){this.service=service;}
    @GetMapping("/product/formula-options")
    public AjaxResult formulas(){return AjaxResult.success(service.formulas());}
    @GetMapping("/{kind}/list")
    public TableDataInfo list(@PathVariable String kind,@RequestParam(required=false) @Positive Long factoryId,@RequestParam(defaultValue="") @Size(max=100) String name,@RequestParam(defaultValue="1") @Min(1) int pageNum,@RequestParam(defaultValue="16") @Min(1) @Max(64) int pageSize){var p=service.list(kind,factoryId,name,pageNum,pageSize);var result=new TableDataInfo(p.rows(),p.total());result.setCode(200);result.setMsg("查询成功");return result;}
    @GetMapping("/{kind}/{id}")
    public AjaxResult detail(@PathVariable String kind,@PathVariable @Positive Long id,@RequestParam(required=false) @Positive Long factoryId){return AjaxResult.success(service.detail(kind,id,factoryId));}
    @PostMapping("/{kind}") @Log(title="新增类型",businessType=BusinessType.INSERT)
    public AjaxResult create(@PathVariable String kind,@Valid @RequestBody TypeManagementService.Write input){return AjaxResult.success(service.create(kind,input));}
    @PutMapping("/{kind}/{id}") @Log(title="编辑类型",businessType=BusinessType.UPDATE)
    public AjaxResult edit(@PathVariable String kind,@PathVariable @Positive Long id,@Valid @RequestBody TypeManagementService.Write input){return AjaxResult.success(service.edit(kind,id,input));}
    @PostMapping("/{kind}/batch-delete") @Log(title="删除类型",businessType=BusinessType.DELETE)
    public AjaxResult remove(@PathVariable String kind,@Valid @RequestBody TypeManagementService.Delete input){service.remove(kind,input);return AjaxResult.success();}
}
