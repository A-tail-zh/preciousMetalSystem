package com.ruoyi.web.controller.metal;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.metal.service.ProcessConsumptionService;

@RestController
@Validated
@RequestMapping("/metal/work-report/consumption")
public class ProcessConsumptionController extends BaseController {
 private final ProcessConsumptionService service;
 public ProcessConsumptionController(ProcessConsumptionService service){this.service=service;}
 @GetMapping("/steps")
 @PreAuthorize("@ss.hasAnyPermi('metal:work-order:query,metal:work-report:list')")
 public AjaxResult steps(@RequestParam @Positive Long workOrderId,@RequestParam @Positive Long itemId,@RequestParam(required=false) @Positive Long processId,@RequestParam(defaultValue="ALL") String batchScope){return success(service.steps(workOrderId,itemId,processId,batchScope));}
 @GetMapping("/batches")
 @PreAuthorize("@ss.hasAnyPermi('metal:work-order:query,metal:work-report:query')")
 public AjaxResult batches(@RequestParam(defaultValue="ALL") String batchScope,@RequestParam @Positive Long workOrderId,@RequestParam @Positive Long itemId,@RequestParam @Positive Long processId,@RequestParam(defaultValue="false") boolean includeVoided,@RequestParam(defaultValue="1") @Min(1) int pageNum,@RequestParam(defaultValue="16") @Min(1) @Max(100) int pageSize){return success(service.batches(workOrderId,itemId,processId,includeVoided,pageNum,pageSize,batchScope));}
 @GetMapping("/orders")
 @PreAuthorize("@ss.hasAnyPermi('metal:work-order:query,metal:work-report:query')")
 public AjaxResult orders(@RequestParam @Positive Long workOrderId,@RequestParam @Positive Long itemId,@RequestParam @Positive Long processId,@RequestParam @Positive Long batchId,@RequestParam(defaultValue="1") @Min(1) int pageNum,@RequestParam(defaultValue="16") @Min(1) @Max(100) int pageSize){return success(service.orders(workOrderId,itemId,processId,batchId,pageNum,pageSize));}
 @GetMapping("/order-lines")
 @PreAuthorize("@ss.hasAnyPermi('metal:work-order:query,metal:work-report:query')")
 public AjaxResult lines(@RequestParam @Positive Long workOrderId,@RequestParam @Positive Long itemId,@RequestParam @Positive Long processId,@RequestParam @Positive Long batchId,@RequestParam @Positive Long orderId,@RequestParam(defaultValue="1") @Min(1) int pageNum,@RequestParam(defaultValue="16") @Min(1) @Max(100) int pageSize){return success(service.lines(workOrderId,itemId,processId,batchId,orderId,pageNum,pageSize));}
}
