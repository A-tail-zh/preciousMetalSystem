package com.ruoyi.web.controller.metal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.metal.service.ProductionOrderService;

@RestController @Validated @RequestMapping("/metal/production-order")
public class ProductionOrderController {
    private final ProductionOrderService service;
    public ProductionOrderController(ProductionOrderService service){this.service=service;}
    @GetMapping("/list") @PreAuthorize("@ss.hasPermi('metal:work-order:list')")
    public TableDataInfo list(@RequestParam(required=false) Long factoryId,@RequestParam(defaultValue="1") int pageNum,@RequestParam(defaultValue="16") int pageSize){var page=service.list(factoryId,pageNum,pageSize);var result=new TableDataInfo(page.rows(),page.total());result.setCode(200);result.setMsg("查询成功");return result;}
    @GetMapping("/{id}") @PreAuthorize("@ss.hasPermi('metal:work-order:query')")
    public AjaxResult detail(@PathVariable @Positive Long id,@RequestParam(required=false) Long factoryId){return AjaxResult.success(service.detail(id,factoryId));}
    @GetMapping("/requests/{requestId}") @PreAuthorize("@ss.hasPermi('metal:work-order:add')")
    public AjaxResult submission(@PathVariable String requestId,@RequestParam(required=false) Long factoryId){return AjaxResult.success(service.submission(requestId,factoryId));}
    @PostMapping({"","/"}) @PreAuthorize("@ss.hasPermi('metal:work-order:add')")
    @Log(title="创建生产工单",businessType=BusinessType.INSERT,isSaveRequestData=false,isSaveResponseData=false)
    public AjaxResult create(@Valid @RequestBody ProductionOrderService.Create input){return AjaxResult.success(service.create(input));}
    @GetMapping("/{id}/edit-context") @PreAuthorize("@ss.hasPermi('metal:work-order:edit')")
    public AjaxResult editContext(@PathVariable @Positive Long id,@RequestParam(required=false) Long factoryId){return AjaxResult.success(service.editContext(id,factoryId));}
    @PutMapping("/{id}") @PreAuthorize("@ss.hasPermi('metal:work-order:edit')")
    @Log(title="编辑生产工单",businessType=BusinessType.UPDATE,isSaveRequestData=false,isSaveResponseData=false)
    public AjaxResult edit(@PathVariable @Positive Long id,@Valid @RequestBody ProductionOrderService.Edit input){return AjaxResult.success(service.edit(id,input));}
    @PostMapping("/batch-delete") @PreAuthorize("@ss.hasPermi('metal:work-order:remove')")
    @Log(title="删除生产工单",businessType=BusinessType.DELETE,isSaveRequestData=false,isSaveResponseData=false)
    public AjaxResult delete(@Valid @RequestBody ProductionOrderService.Delete input){return AjaxResult.success(service.delete(input));}
    @GetMapping("/operations/{requestId}") @PreAuthorize("@ss.hasAnyPermi('metal:work-order:edit,metal:work-order:remove')")
    public AjaxResult operation(@PathVariable String requestId,@RequestParam(required=false) Long factoryId,@RequestParam String action){return AjaxResult.success(service.operation(requestId,factoryId,action));}
}
