package com.ruoyi.web.controller.metal;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.metal.service.StorageLedgerService;
@RestController @RequestMapping("/metal/storage-inventory")
public class StorageInventoryController {
 private final StorageLedgerService service;
 public StorageInventoryController(StorageLedgerService service){this.service=service;}
 @GetMapping("/list") @PreAuthorize("@ss.hasPermi('metal:inventory:list')")
 public Map<String,Object> list(@RequestParam(required=false)Long factoryId,@RequestParam Map<String,String> filters,@RequestParam(defaultValue="1")int pageNum,@RequestParam(defaultValue="16")int pageSize){return service.inventory(factoryId,filters,pageNum,pageSize);}
 @GetMapping("/{id}") @PreAuthorize("@ss.hasAnyPermi('metal:inventory:query,metal:inventory:adjust')")
 public AjaxResult detail(@PathVariable long id,@RequestParam(required=false)Long factoryId){return AjaxResult.success(service.detail(factoryId,id));}
 @GetMapping("/{id}/ledger") @PreAuthorize("@ss.hasPermi('metal:inventory:query')")
 public AjaxResult ledger(@PathVariable long id,@RequestParam(required=false)Long factoryId){return AjaxResult.success(service.entries(factoryId,id));}
 @GetMapping("/export") @PreAuthorize("@ss.hasPermi('metal:inventory:export')")
 public AjaxResult export(@RequestParam(required=false)Long factoryId,@RequestParam Map<String,String> filters){return AjaxResult.success(service.exportInventory(factoryId,filters));}
 @GetMapping("/options") @PreAuthorize("@ss.hasAnyPermi('metal:inventory:list,metal:inventory:query,metal:warehouse-order:add,metal:warehouse-order:edit,metal:warehouse-order:receive,metal:warehouse-order:accept')")
 public AjaxResult options(@RequestParam(required=false)Long factoryId,@RequestParam Map<String,String> filters){return AjaxResult.success(service.options(factoryId,filters));}
}
