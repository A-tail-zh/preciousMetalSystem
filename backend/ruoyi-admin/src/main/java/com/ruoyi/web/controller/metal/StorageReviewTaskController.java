package com.ruoyi.web.controller.metal;

import java.util.Map;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.metal.service.StorageOrderService;

@RestController
@RequestMapping("/metal/storage-review-tasks")
public class StorageReviewTaskController {
 private final StorageOrderService service;
 public StorageReviewTaskController(StorageOrderService service){this.service=service;}
 @GetMapping("/count") public AjaxResult count(@RequestParam(required=false)Long factoryId,@RequestParam(defaultValue="mine")String scope){return AjaxResult.success(Map.of("count",service.tasks(factoryId,scope,1,1).get("count")));}
 @GetMapping("/list") public Map<String,Object> list(@RequestParam(required=false)Long factoryId,@RequestParam(defaultValue="mine")String scope,@RequestParam(defaultValue="1")int pageNum,@RequestParam(defaultValue="16")int pageSize){var result=new java.util.LinkedHashMap<>(service.tasks(factoryId,scope,pageNum,pageSize));result.put("code",200);return result;}
 @GetMapping("/{id}") public AjaxResult detail(@PathVariable long id,@RequestParam(required=false)Long factoryId){return AjaxResult.success(service.task(id,factoryId));}
 @PostMapping("/{id}/reassign") @Log(title="转派仓储复验",businessType=BusinessType.UPDATE,isSaveRequestData=false,isSaveResponseData=false)
 public AjaxResult reassign(@PathVariable long id,@RequestBody Map<String,Object> body){return AjaxResult.success(service.reassign(id,body));}
}
