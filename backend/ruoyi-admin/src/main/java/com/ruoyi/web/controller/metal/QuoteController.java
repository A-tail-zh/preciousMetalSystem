package com.ruoyi.web.controller.metal;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.metal.catalog.QuoteRule;
import com.ruoyi.metal.service.QuoteRuleService;

/**
 * 报价 HTTP 接口，负责请求映射、参数校验和接口权限声明，业务处理交给对应服务。
 */
@RestController
public class QuoteController extends BaseController {
    private final QuoteRuleService service;
    private final com.ruoyi.metal.service.ProductDimensionImage images;

    public QuoteController(QuoteRuleService service, com.ruoyi.metal.service.ProductDimensionImage images) {
        this.service = service;
        this.images = images;
    }

    /** Uses exactly the same renderer as the downloaded quotation workbook. */
    @PostMapping("/metal/quotation/image")
    @PreAuthorize("@ss.hasPermi('metal:quotation:list')")
    public AjaxResult image(@Valid @RequestBody com.ruoyi.metal.catalog.CalculationRequest input) {
        return success((Object) ("data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(images.render(input.category(), input.dimensions()))));
    }

    /**
     * 查询当前报价规则。
     */
    @GetMapping("/metal/quote-rule")
    @PreAuthorize("@ss.hasPermi('metal:quote-rule:list')")
    public AjaxResult get() {
        return success(service.get());
    }

    /**
     * 根据请求版本号更新记录，返回受影响的行数。
     */
    @PutMapping("/metal/quote-rule")
    @PreAuthorize("@ss.hasPermi('metal:quote-rule:edit')")
    @Log(title = "报价规则", businessType = BusinessType.UPDATE)
    public AjaxResult update(@Valid @RequestBody QuoteRule rule) {
        service.update(rule);
        return success();
    }

    /**
     * 获取报价所需的基础资料和规则上下文。
     */
    @GetMapping("/metal/quotation/context")
    @PreAuthorize("@ss.hasPermi('metal:quotation:list')")
    public AjaxResult context() {
        return success(service.context());
    }

    /**
     * 获取产品计算所需的报价规则。
     */
    @GetMapping("/metal/product/calculation-rule")
    @PreAuthorize("@ss.hasAnyPermi('metal:product:query,metal:product:add,metal:product:edit')")
    public AjaxResult productRule() {
        return success(service.get());
    }

    /**
     * 校验请求参数后执行产品体积计算。
     */
    @PostMapping("/metal/quotation/calculate")
    @PreAuthorize("@ss.hasPermi('metal:quotation:list')")
    public AjaxResult calculate(@Valid @RequestBody com.ruoyi.metal.catalog.CalculationRequest input) {
        return success(service.calculate(input));
    }
}
