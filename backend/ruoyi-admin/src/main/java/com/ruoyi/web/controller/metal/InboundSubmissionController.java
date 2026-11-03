package com.ruoyi.web.controller.metal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.metal.catalog.InboundSubmissionRequests.Request;
import com.ruoyi.metal.service.InboundSubmissionService;import com.ruoyi.metal.service.WarehouseWorkflowService;
@RestController @Validated @RequestMapping("/metal/warehouse-order/inbound-submissions")
public class InboundSubmissionController {
 private final InboundSubmissionService service;private final WarehouseWorkflowService workflow;
 public InboundSubmissionController(InboundSubmissionService service,WarehouseWorkflowService workflow){this.service=service;this.workflow=workflow;}
 @PostMapping @PreAuthorize("@ss.hasPermi('metal:warehouse-order:add')") @Log(title="多仓入库",businessType=BusinessType.INSERT)
 public AjaxResult submit(@Valid @RequestBody Request request){try{return AjaxResult.success(service.hasRequest(request.requestId())||!"RAW_IN".equals(request.type())&&workflow.legacyBatch(request.batchId(),request.itemId(),request.processId())?service.submit(request):workflow.inbound(request));}catch(com.ruoyi.metal.service.InboundGroupException e){return AjaxResult.error(e.getMessage()).put("fieldErrors",java.util.List.of(java.util.Map.of("field","groups["+e.getIndex()+"].lines","message",e.getMessage(),"code","INVALID_GROUP")));}}
 @GetMapping("/{id}") @PreAuthorize("@ss.hasPermi('metal:warehouse-order:query')")
 public AjaxResult detail(@PathVariable @Positive long id){return AjaxResult.success(service.detail(id));}
}
