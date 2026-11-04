package com.ruoyi.web.controller.metal;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.metal.service.ContinuousBatchPlanning;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @Validated @RequestMapping("/metal")
public class ContinuousBatchController {
    private final ContinuousBatchPlanning service;
    public ContinuousBatchController(ContinuousBatchPlanning service){this.service=service;}
    @GetMapping("/warehouse/batch-planning")
    @PreAuthorize("@ss.hasAnyPermi('metal:warehouse-order:add,metal:warehouse-order:import,metal:warehouse-order:query,metal:process-batch:add')")
    public AjaxResult context(@RequestParam @Positive long factoryId,@RequestParam @Positive long workOrderId,@RequestParam @Positive long itemId,@RequestParam @Positive long processId){return AjaxResult.success(service.context(factoryId,workOrderId,itemId,processId));}
    @GetMapping("/work-order/{id}/batch-progress") @PreAuthorize("@ss.hasPermi('metal:work-order:query')")
    public AjaxResult progress(@PathVariable @Positive long id){return AjaxResult.success(service.progress(id));}
}
