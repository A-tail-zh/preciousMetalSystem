package com.ruoyi.web.controller.metal;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.metal.service.ProductionExecutionService;
@RestController @RequestMapping("/metal/production-order")
public class ProductionExecutionController {
 private final ProductionExecutionService service;
 public ProductionExecutionController(ProductionExecutionService service){this.service=service;}
 @GetMapping("/{id}/flow") public AjaxResult flow(@PathVariable long id,@RequestParam(required=false) Long factoryId){return AjaxResult.success(service.flow(id,factoryId));}
 @GetMapping("/{id}/report") public AjaxResult report(@PathVariable long id,@RequestParam(required=false) Long factoryId){return AjaxResult.success(service.report(id,factoryId));}
 @GetMapping("/executions/operations/{requestId}") public AjaxResult operation(@PathVariable String requestId,@RequestParam(required=false) Long factoryId){return AjaxResult.success(service.operation(factoryId,requestId));}
 @GetMapping("/{id}/inheritance-options") public AjaxResult inheritanceOptions(@PathVariable long id,@RequestParam long orderStepId,@RequestParam(required=false) Long factoryId){return AjaxResult.success(service.inheritanceOptions(id,orderStepId,factoryId));}
 @PostMapping("/{id}/executions") @Log(title="创建生产执行批次",businessType=BusinessType.INSERT,isSaveRequestData=false,isSaveResponseData=false) public AjaxResult create(@PathVariable long id,@RequestBody Map<String,Object> body){return AjaxResult.success(service.create(id,body));}
 @PutMapping("/executions/{id}") @Log(title="编辑未开始生产批次",businessType=BusinessType.UPDATE,isSaveRequestData=false,isSaveResponseData=false) public AjaxResult edit(@PathVariable long id,@RequestBody Map<String,Object> body){return AjaxResult.success(service.edit(id,body));}
 @PostMapping("/executions/{id}/cancel") @Log(title="取消空生产批次",businessType=BusinessType.UPDATE,isSaveRequestData=false,isSaveResponseData=false) public AjaxResult cancel(@PathVariable long id,@RequestBody Map<String,Object> body){return AjaxResult.success(service.cancel(id,body));}
 @PostMapping("/executions/{id}/close") @Log(title="结算生产执行批次",businessType=BusinessType.UPDATE,isSaveRequestData=false,isSaveResponseData=false) public AjaxResult close(@PathVariable long id,@RequestBody Map<String,Object> body){return AjaxResult.success(service.close(id,body));}
 @PostMapping("/{id}/steps/{stepId}/complete") @Log(title="生产工序完成",businessType=BusinessType.UPDATE,isSaveRequestData=false,isSaveResponseData=false) public AjaxResult completeStep(@PathVariable long id,@PathVariable long stepId,@RequestBody Map<String,Object> body){return AjaxResult.success(service.completeStep(id,stepId,body));}
 @PostMapping("/{id}/complete") @Log(title="生产工单正常完工",businessType=BusinessType.UPDATE,isSaveRequestData=false,isSaveResponseData=false) public AjaxResult complete(@PathVariable long id,@RequestBody Map<String,Object> body){return AjaxResult.success(service.complete(id,body));}
 @PostMapping("/{id}/exception-close") @Log(title="申请异常完工",businessType=BusinessType.INSERT,isSaveRequestData=false,isSaveResponseData=false) public AjaxResult exceptional(@PathVariable long id,@RequestBody Map<String,Object> body){return AjaxResult.success(service.requestExceptional(id,body));}
 @PostMapping("/{id}/exception-close/{closeId}/review") @Log(title="审批异常完工",businessType=BusinessType.UPDATE,isSaveRequestData=false,isSaveResponseData=false) public AjaxResult review(@PathVariable long id,@PathVariable long closeId,@RequestBody Map<String,Object> body){return AjaxResult.success(service.reviewExceptional(id,closeId,body));}
}
