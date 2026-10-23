package com.ruoyi.web.controller.metal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.metal.catalog.BatchCompletionRequests.ReturnConfirmation;
import com.ruoyi.metal.service.BatchReturnConfirmationService;

@RestController @Validated @RequestMapping("/metal/process-batch")
public class BatchCompletionController {
 private final BatchReturnConfirmationService service;
 public BatchCompletionController(BatchReturnConfirmationService service){this.service=service;}
 @GetMapping("/{id}/completion-context")
 @PreAuthorize("@ss.hasAnyPermi('metal:warehouse-order:add,metal:warehouse-order:query,metal:process-batch:query')")
 public AjaxResult context(@PathVariable @Positive Long id){return AjaxResult.success(service.context(id));}
 @PostMapping("/{id}/confirm-return")
 @PreAuthorize("@ss.hasPermi('metal:warehouse-order:add') && @ss.hasPermi('metal:process-batch:settle')")
 @Log(title="批次全部还料确认并结算",businessType=BusinessType.UPDATE)
 public AjaxResult confirm(@PathVariable @Positive Long id,@Valid @RequestBody ReturnConfirmation input){return AjaxResult.success(service.confirmReturn(id,input));}
}
