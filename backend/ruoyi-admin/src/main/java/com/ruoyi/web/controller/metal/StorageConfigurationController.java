package com.ruoyi.web.controller.metal;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.metal.service.StorageConfigurationService;
@RestController @RequestMapping("/metal/storage-config")
public class StorageConfigurationController {
 private final StorageConfigurationService service;
 public StorageConfigurationController(StorageConfigurationService service){this.service=service;}
 @GetMapping("/{kind}/{id}") public AjaxResult detail(@PathVariable String kind,@PathVariable long id,@RequestParam(required=false) Long factoryId){return AjaxResult.success(service.detail(kind,id,factoryId));}
 @PutMapping("/{kind}/{id}") @Log(title="维护仓储工艺配置",businessType=BusinessType.UPDATE) public AjaxResult save(@PathVariable String kind,@PathVariable long id,@RequestBody Map<String,Object> input){return AjaxResult.success(service.save(kind,id,input));}
}
