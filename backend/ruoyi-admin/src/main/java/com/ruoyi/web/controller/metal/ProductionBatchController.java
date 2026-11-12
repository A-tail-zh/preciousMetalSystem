package com.ruoyi.web.controller.metal;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.metal.service.ProductionBatchService;
import com.ruoyi.metal.service.ProductionBatchReverseService;
import com.ruoyi.metal.service.ProductionBatchTraceService;
import com.ruoyi.metal.service.ProductionBatchTransformService;
import jakarta.validation.constraints.Positive;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/metal/production-batches")
public class ProductionBatchController {
    private final ProductionBatchService service;
    private final ProductionBatchReverseService reversals;
    private final ProductionBatchTraceService traces;
    private final ProductionBatchTransformService transforms;
    public ProductionBatchController(ProductionBatchService service,ProductionBatchTransformService transforms,ProductionBatchReverseService reversals,ProductionBatchTraceService traces){this.service=service;this.transforms=transforms;this.reversals=reversals;this.traces=traces;}

    @PostMapping("/splits/preview")
    public AjaxResult splitPreview(@RequestBody Map<String,Object> body){return AjaxResult.success(transforms.splitPreview(body));}

    @PostMapping("/splits")
    @Log(title="生产批次拆分",businessType=BusinessType.UPDATE,isSaveRequestData=false,isSaveResponseData=false)
    public AjaxResult split(@RequestBody Map<String,Object> body){return AjaxResult.success(transforms.split(body));}

    @PostMapping("/merges/preview")
    public AjaxResult mergePreview(@RequestBody Map<String,Object> body){return AjaxResult.success(transforms.mergePreview(body));}

    @PostMapping("/merges")
    @Log(title="生产批次合并",businessType=BusinessType.UPDATE,isSaveRequestData=false,isSaveResponseData=false)
    public AjaxResult merge(@RequestBody Map<String,Object> body){return AjaxResult.success(transforms.merge(body));}

    @GetMapping("/{id}/trace")
    public AjaxResult trace(@PathVariable @Positive long id,@RequestParam(required=false)Long factoryId,@RequestParam(defaultValue="BOTH")String direction,@RequestParam(defaultValue="50")int pageSize,@RequestParam(required=false)String cursor){return AjaxResult.success(traces.trace(id,factoryId,direction,pageSize,cursor));}
    @GetMapping("/operations/{operationId}/reverse-context")
    public AjaxResult reverseContext(@PathVariable @Positive long operationId,@RequestParam(required=false)Long factoryId){return AjaxResult.success(reversals.context(operationId,factoryId));}
    @PostMapping("/operations/{operationId}/reverse")
    @Log(title="撤销生产批次拆合",businessType=BusinessType.UPDATE,isSaveRequestData=false,isSaveResponseData=false)
    public AjaxResult reverse(@PathVariable @Positive long operationId,@RequestBody Map<String,Object> body){return AjaxResult.success(reversals.reverse(operationId,body));}
    @GetMapping("/list")
    public TableDataInfo list(@RequestParam Map<String,String> params){var page=service.list(params);var result=new TableDataInfo(page.rows(),page.total());result.setCode(200);result.setMsg("查询成功");return result;}

    @GetMapping("/{id}")
    public AjaxResult detail(@PathVariable @Positive long id,@RequestParam(required=false)Long factoryId){return AjaxResult.success(service.detail(id,factoryId));}

    @GetMapping("/{id}/context")
    public AjaxResult context(@PathVariable @Positive long id,@RequestParam(required=false)Long factoryId,@RequestParam String intent){return AjaxResult.success(service.context(id,factoryId,intent));}

    @PostMapping({"","/"})
    @Log(title="创建生产批次身份",businessType=BusinessType.INSERT,isSaveRequestData=false,isSaveResponseData=false)
    public AjaxResult create(@RequestBody Map<String,Object> body){return AjaxResult.success(service.create(body));}

    @PostMapping("/{id}/executions")
    @Log(title="批次进入工序",businessType=BusinessType.INSERT,isSaveRequestData=false,isSaveResponseData=false)
    public AjaxResult start(@PathVariable @Positive long id,@RequestBody Map<String,Object> body){return AjaxResult.success(service.start(id,body));}

    @PostMapping("/{id}/complete")
    @Log(title="生产批次完工",businessType=BusinessType.UPDATE,isSaveRequestData=false,isSaveResponseData=false)
    public AjaxResult complete(@PathVariable @Positive long id,@RequestBody Map<String,Object> body){return AjaxResult.success(service.complete(id,body));}

    @GetMapping("/operations/{requestId}")
    public AjaxResult operation(@PathVariable String requestId,@RequestParam(required=false)Long factoryId){return AjaxResult.success(service.operation(requestId,factoryId));}
}
