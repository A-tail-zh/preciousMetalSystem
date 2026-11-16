package com.ruoyi.web.controller.metal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.metal.catalog.WarehouseFlowRequests.*;
import com.ruoyi.metal.service.WarehouseWorkflowService;

@RestController @Validated @RequestMapping("/metal/warehouse-flows")
public class WarehouseFlowController {
    private final WarehouseWorkflowService service;
    public WarehouseFlowController(WarehouseWorkflowService service){this.service=service;}
    @GetMapping("/list") @PreAuthorize("@ss.hasAnyPermi('metal:warehouse-order:query,metal:warehouse-order:add,metal:warehouse-order:receive,metal:warehouse-order:accept,metal:warehouse-order:handover')")
    public AjaxResult list(@RequestParam @Positive long factoryId){return AjaxResult.success(service.list(factoryId));}
    @GetMapping("/{id}") @PreAuthorize("@ss.hasAnyPermi('metal:warehouse-order:query,metal:warehouse-order:add,metal:warehouse-order:receive,metal:warehouse-order:accept,metal:warehouse-order:handover')")
    public AjaxResult detail(@PathVariable @Positive long id){return AjaxResult.success(service.detail(id));}
    @PostMapping @PreAuthorize("@ss.hasPermi('metal:warehouse-order:add')") @Log(title="创建待确认仓储单",businessType=BusinessType.INSERT)
    public AjaxResult create(@Valid @RequestBody Create request){return AjaxResult.success(service.create(request));}
    @PostMapping("/{id}/groups/{group}/confirm") @PreAuthorize("@ss.hasAnyPermi('metal:warehouse-order:receive,metal:warehouse-order:accept')") @Log(title="仓储逐仓真实确认",businessType=BusinessType.UPDATE)
    public AjaxResult confirm(@PathVariable @Positive long id,@PathVariable @Positive long group,@Valid @RequestBody Confirm request){return AjaxResult.success(service.confirm(id,group,request));}
    @PostMapping("/{id}/reject") @PreAuthorize("@ss.hasAnyPermi('metal:warehouse-order:receive,metal:warehouse-order:accept')") @Log(title="仓储申请退回",businessType=BusinessType.UPDATE)
    public AjaxResult reject(@PathVariable @Positive long id,@Valid @RequestBody Change request){return AjaxResult.success(service.reject(id,request));}
    @PostMapping("/{id}/cancel") @PreAuthorize("@ss.hasAnyPermi('metal:warehouse-order:add,metal:warehouse-order:handover')") @Log(title="仓储申请取消",businessType=BusinessType.UPDATE)
    public AjaxResult cancel(@PathVariable @Positive long id,@Valid @RequestBody Change request){return AjaxResult.success(service.cancel(id,request));}
    @PostMapping("/{id}/revise") @PreAuthorize("@ss.hasPermi('metal:warehouse-order:add')") @Log(title="仓储申请修订",businessType=BusinessType.UPDATE)
    public AjaxResult revise(@PathVariable @Positive long id,@Valid @RequestBody Change request){return AjaxResult.success(service.revise(id,request));}
    @PostMapping("/{id}/reverse") @PreAuthorize("@ss.hasPermi('metal:warehouse-order:reverse')") @Log(title="仓储申请整体冲销",businessType=BusinessType.UPDATE)
    public AjaxResult reverse(@PathVariable @Positive long id,@Valid @RequestBody Change request){return AjaxResult.success(service.reverse(id,request));}
    @GetMapping("/closeout-context/{work}") @PreAuthorize("@ss.hasAnyPermi('metal:work-order:query,metal:warehouse-order:query,metal:warehouse-order:handover,metal:warehouse-order:accept,metal:warehouse-order:add')")
    public AjaxResult closeoutContext(@PathVariable @Positive long work){return AjaxResult.success(service.closeoutContext(work));}
    @PostMapping("/closeout/{work}") @PreAuthorize("@ss.hasPermi('metal:warehouse-order:handover')") @Log(title="客户余料收尾",businessType=BusinessType.INSERT)
    public AjaxResult closeout(@PathVariable @Positive long work,@Valid @RequestBody Closeout request){return AjaxResult.success(service.closeout(work,request));}
    @PostMapping("/closeout/{work}/reverse") @PreAuthorize("@ss.hasPermi('metal:warehouse-order:reverse')") @Log(title="客户余料收尾整体撤回",businessType=BusinessType.UPDATE)
    public AjaxResult reverseCloseout(@PathVariable @Positive long work,@Valid @RequestBody Change request){return AjaxResult.success(service.reverseCloseout(work,request));}
    @GetMapping("/handover-batches") @PreAuthorize("@ss.hasAnyPermi('metal:warehouse-order:handover,metal:warehouse-order:query,metal:warehouse-order:accept,metal:warehouse-order:add')")
    public AjaxResult batches(@RequestParam @Positive long factoryId){return AjaxResult.success(service.handoverBatches(factoryId));}
    @GetMapping("/handover-context/{batch}") @PreAuthorize("@ss.hasAnyPermi('metal:warehouse-order:handover,metal:warehouse-order:query,metal:warehouse-order:accept,metal:warehouse-order:add')")
    public AjaxResult context(@PathVariable @Positive long batch){return AjaxResult.success(service.handoverContext(batch));}
    @PostMapping("/handover/{batch}") @PreAuthorize("@ss.hasPermi('metal:warehouse-order:handover')") @Log(title="加工人员产出及全部余料交接",businessType=BusinessType.INSERT)
    public AjaxResult handover(@PathVariable @Positive long batch,@Valid @RequestBody Handover request){return AjaxResult.success(service.handover(batch,request));}
}
