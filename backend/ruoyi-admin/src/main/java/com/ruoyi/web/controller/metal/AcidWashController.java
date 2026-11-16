package com.ruoyi.web.controller.metal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.metal.catalog.AcidWashRequests.*;
import com.ruoyi.metal.catalog.WarehouseRequests.Action;
import com.ruoyi.metal.service.AcidWashService;

@RestController @Validated @RequestMapping("/metal/acid-wash")
public class AcidWashController {
 private final AcidWashService service;
 public AcidWashController(AcidWashService service){this.service=service;}
 @GetMapping("/options") @PreAuthorize("@ss.hasPermi('metal:warehouse-order:add')")
 public AjaxResult options(@RequestParam @Positive long factoryId){return AjaxResult.success(service.options(factoryId));}
 @GetMapping("/list") @PreAuthorize("@ss.hasAnyPermi('metal:warehouse-order:add,metal:warehouse-order:query')")
 public AjaxResult list(@RequestParam @Positive long factoryId,@RequestParam(defaultValue="1") @Min(1) int pageNum,@RequestParam(defaultValue="16") @Min(1) @Max(100) int pageSize){return AjaxResult.success(service.list(factoryId,pageNum,pageSize));}
 @GetMapping("/{id}") @PreAuthorize("@ss.hasAnyPermi('metal:warehouse-order:add,metal:warehouse-order:query')")
 public AjaxResult detail(@PathVariable @Positive long id){return AjaxResult.success(service.detail(id));}
 @PostMapping @PreAuthorize("@ss.hasPermi('metal:warehouse-order:add')") @Log(title="酸洗领料",businessType=BusinessType.INSERT)
 public AjaxResult start(@Valid @RequestBody Start input){return AjaxResult.success().put("id",service.start(input));}
 @PostMapping("/{id}/finish") @PreAuthorize("@ss.hasPermi('metal:warehouse-order:add') && @ss.hasPermi('metal:process-batch:settle')") @Log(title="酸洗产出及全部还料结算",businessType=BusinessType.UPDATE)
 public AjaxResult finish(@PathVariable @Positive long id,@Valid @RequestBody Finish input){return AjaxResult.success().put("id",service.finish(id,input));}
 @PostMapping("/{id}/reverse") @PreAuthorize("@ss.hasPermi('metal:warehouse-order:reverse')") @Log(title="酸洗批次整体冲销",businessType=BusinessType.UPDATE)
 public AjaxResult reverse(@PathVariable @Positive long id,@Valid @RequestBody Action input){service.reverse(id,input);return AjaxResult.success();}
}
