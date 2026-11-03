package com.ruoyi.web.controller.metal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.metal.catalog.RawOriginRequests.Verification;
import com.ruoyi.metal.service.RawOriginService;

@RestController
@Validated
@RequestMapping("/metal/inventory")
public class RawOriginController extends BaseController {
    private final RawOriginService service;
    public RawOriginController(RawOriginService service) { this.service=service; }

    @PutMapping("/{id}/origin")
    @PreAuthorize("@ss.hasPermi('metal:inventory:verify-origin')")
    @Log(title="库存来源核对",businessType=BusinessType.UPDATE)
    public AjaxResult verify(@PathVariable @Positive Long id,@RequestBody @Valid Verification request) {
        return success(service.verify(id,request));
    }

    @GetMapping("/{id}/origin-history")
    @PreAuthorize("@ss.hasPermi('metal:inventory:verify-origin')")
    public AjaxResult history(@PathVariable @Positive Long id) { var context=service.historyContext(id);return success(context.history()).put("version",context.version()); }
}
