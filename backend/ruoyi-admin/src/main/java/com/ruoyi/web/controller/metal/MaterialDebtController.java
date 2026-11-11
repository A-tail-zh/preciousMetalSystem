package com.ruoyi.web.controller.metal;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.metal.service.StorageLedgerService;
@RestController @RequestMapping("/metal/material-debts")
public class MaterialDebtController {
 private final StorageLedgerService service;
 public MaterialDebtController(StorageLedgerService service){this.service=service;}
 @GetMapping("/list") @PreAuthorize("@ss.hasPermi('metal:material-debt:list')")
 public Map<String,Object> list(@RequestParam(required=false)Long factoryId,@RequestParam(required=false)String customerId,@RequestParam(required=false)String direction,@RequestParam(required=false)Long orderId,@RequestParam(defaultValue="false")boolean includeSettled,@RequestParam(defaultValue="1")int pageNum,@RequestParam(defaultValue="16")int pageSize){return service.debts(factoryId,customerId,direction,orderId,includeSettled,pageNum,pageSize);}
 @GetMapping("/{id}") @PreAuthorize("@ss.hasPermi('metal:material-debt:list')")
 public AjaxResult detail(@PathVariable long id,@RequestParam(required=false)Long factoryId){return AjaxResult.success(service.debtDetail(factoryId,id));}
}
