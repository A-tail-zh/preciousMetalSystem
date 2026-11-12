package com.ruoyi.web.controller.metal;

import java.util.Map;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.metal.service.StorageOrderService;
import com.ruoyi.metal.service.StorageReservationService;

@RestController
@RequestMapping("/metal/storage-orders")
public class StorageOrderController {
 private final StorageOrderService service; private final StorageReservationService reservations;
 public StorageOrderController(StorageOrderService service,StorageReservationService reservations){this.service=service;this.reservations=reservations;}
 @GetMapping("/list") public TableDataInfo list(@RequestParam Map<String,String> params){var page=service.list(params);var result=new TableDataInfo(page.rows(),page.total());result.setCode(200);result.setMsg("查询成功");return result;}
 @GetMapping("/export") public AjaxResult export(@RequestParam Map<String,String> params){return AjaxResult.success(service.export(params));}
 @GetMapping("/options") public AjaxResult options(@RequestParam(required=false)Long factoryId,@RequestParam(required=false)Long executionId,@RequestParam(required=false)String type,@RequestParam(required=false)String sessionId,@RequestParam(required=false)Long batchId,@RequestParam(required=false)Long orderStepId){return AjaxResult.success(service.options(factoryId,executionId,type,sessionId,batchId,orderStepId));}
 @PostMapping("/reservations") public AjaxResult reserve(@RequestBody Map<String,Object> body){return AjaxResult.success(reservations.replace(body));}
 @GetMapping("/reservations/{sessionId}") public AjaxResult reservation(@PathVariable String sessionId,@RequestParam(required=false)Long factoryId){return AjaxResult.success(reservations.status(sessionId,factoryId));}
 @GetMapping("/reservations/operations/{requestId}") public AjaxResult reservationOperation(@PathVariable String requestId,@RequestParam(required=false)Long factoryId){return AjaxResult.success(reservations.operation(requestId,factoryId));}
 @PostMapping("/reservations/{sessionId}/{action:renew|release}") public AjaxResult reservationChange(@PathVariable String sessionId,@PathVariable String action,@RequestBody Map<String,Object> body){return AjaxResult.success(reservations.change(sessionId,action,body));}
 @GetMapping("/{id}") public AjaxResult detail(@PathVariable long id,@RequestParam(required=false)Long factoryId){return AjaxResult.success(service.detail(id,factoryId));}
 @GetMapping("/operations/{requestId}") public AjaxResult operation(@PathVariable String requestId,@RequestParam(required=false)Long factoryId){return AjaxResult.success(service.operation(requestId,factoryId));}
 @PostMapping({"","/"}) @Log(title="创建仓储单据",businessType=BusinessType.INSERT,isSaveRequestData=false,isSaveResponseData=false)
 public AjaxResult create(@RequestBody Map<String,Object> body){return AjaxResult.success(service.create(body));}
 @PutMapping("/{id}") @Log(title="修改仓储单据",businessType=BusinessType.UPDATE,isSaveRequestData=false,isSaveResponseData=false)
 public AjaxResult edit(@PathVariable long id,@RequestBody Map<String,Object> body){return AjaxResult.success(service.edit(id,body));}
 @PostMapping("/{id}/{action}") @Log(title="仓储单据操作",businessType=BusinessType.UPDATE,isSaveRequestData=false,isSaveResponseData=false)
 public AjaxResult action(@PathVariable long id,@PathVariable String action,@RequestBody Map<String,Object> body){return AjaxResult.success(service.action(id,action,body));}
}
