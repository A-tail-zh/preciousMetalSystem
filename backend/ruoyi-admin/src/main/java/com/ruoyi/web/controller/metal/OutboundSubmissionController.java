package com.ruoyi.web.controller.metal;
import jakarta.validation.Valid;import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;import org.springframework.validation.annotation.Validated;import org.springframework.security.access.prepost.PreAuthorize;
import com.ruoyi.common.annotation.Log;import com.ruoyi.common.enums.BusinessType;import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.metal.catalog.OutboundSubmissionRequests.*;import com.ruoyi.metal.service.*;
@RestController @Validated @RequestMapping("/metal/warehouse-order/outbound-submissions")
public class OutboundSubmissionController {
 private final OutboundSubmissionService service;private final OutboundDraftService drafts;private final WarehouseWorkflowService workflow;
 public OutboundSubmissionController(OutboundSubmissionService service,OutboundDraftService drafts,WarehouseWorkflowService workflow){this.service=service;this.drafts=drafts;this.workflow=workflow;}
 @PostMapping @PreAuthorize("@ss.hasPermi('metal:warehouse-order:add')") @Log(title="多仓生产出库",businessType=BusinessType.INSERT)
 public AjaxResult submit(@Valid @RequestBody Request r){try{return AjaxResult.success(service.hasRequest(r.requestId())?service.submit(r):workflow.outbound(r));}catch(OutboundFieldException e){return AjaxResult.error(e.getMessage()).put("fieldErrors",e.fields());}}
 @PostMapping("/preview") @PreAuthorize("@ss.hasPermi('metal:warehouse-order:add')")
 public AjaxResult preview(@Valid @RequestBody Preview p){return AjaxResult.success(drafts.preview(p));}
 @GetMapping("/{id}") @PreAuthorize("@ss.hasPermi('metal:warehouse-order:query')")
 public AjaxResult detail(@PathVariable @Positive long id){return AjaxResult.success(service.detail(id));}
}
