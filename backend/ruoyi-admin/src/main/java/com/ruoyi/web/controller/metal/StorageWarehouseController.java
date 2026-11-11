package com.ruoyi.web.controller.metal;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.metal.service.StorageWarehouseService;
@RestController @RequestMapping("/metal/storage-warehouses")
public class StorageWarehouseController {
 private final StorageWarehouseService service;
 public StorageWarehouseController(StorageWarehouseService service){this.service=service;}
 @GetMapping("/list") @PreAuthorize("@ss.hasPermi('metal:warehouse:list')")
 public Map<String,Object> list(@RequestParam(required=false)Long factoryId,@RequestParam(required=false)String name,@RequestParam(required=false)String status,@RequestParam(defaultValue="1")int pageNum,@RequestParam(defaultValue="16")int pageSize){return service.list(factoryId,name,status,pageNum,pageSize);}
 @GetMapping("/options") @PreAuthorize("@ss.hasAnyPermi('metal:warehouse:list,metal:inventory:list,metal:inventory:query,metal:warehouse-order:list,metal:warehouse-order:add,metal:warehouse-order:edit')")
 public AjaxResult options(@RequestParam(required=false)Long factoryId){return AjaxResult.success(service.options(factoryId));}
 @GetMapping("/{id}") @PreAuthorize("@ss.hasAnyPermi('metal:warehouse:list,metal:warehouse:edit')")
 public AjaxResult detail(@PathVariable long id,@RequestParam(required=false)Long factoryId){return AjaxResult.success(service.detail(factoryId,id));}
 @PostMapping({"","/"}) @PreAuthorize("@ss.hasPermi('metal:warehouse:add')") @Log(title="新仓库管理",businessType=BusinessType.INSERT)
 public AjaxResult create(@RequestBody Map<String,Object> input){return AjaxResult.success(service.save(null,input));}
 @PutMapping("/{id}") @PreAuthorize("@ss.hasPermi('metal:warehouse:edit')") @Log(title="新仓库管理",businessType=BusinessType.UPDATE)
 public AjaxResult update(@PathVariable Long id,@RequestBody Map<String,Object> input){return AjaxResult.success(service.save(id,input));}
 @PostMapping("/batch-delete") @PreAuthorize("@ss.hasPermi('metal:warehouse:remove')") @Log(title="新仓库管理",businessType=BusinessType.DELETE)
 public AjaxResult delete(@RequestBody Map<String,Object> input){return AjaxResult.success(service.delete(input));}
 @GetMapping("/operations/{requestId}") @PreAuthorize("@ss.hasAnyPermi('metal:warehouse:add,metal:warehouse:edit,metal:warehouse:remove')")
 public AjaxResult operation(@PathVariable String requestId,@RequestParam(required=false)Long factoryId){return AjaxResult.success(service.operation(factoryId,requestId));}
}
