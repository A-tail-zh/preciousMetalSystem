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


    @GetMapping("/metal/metal-types")
    @PreAuthorize("@ss.hasAnyPermi('metal:material:list,metal:material:query,metal:material:add,metal:material:edit,metal:material:remove,metal:product:list,metal:product:query,metal:product:add,metal:product:edit,metal:product:remove,metal:quote-rule:list,metal:quote-rule:edit,metal:quotation:list')")
    public AjaxResult metals(@RequestParam(name="factoryId",required=false) Long factoryId){return success(service.metals(factoryId));}
    public record MetalName(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=100) String name,@jakarta.validation.constraints.Positive Long factoryId){}
    @PostMapping("/metal/metal-types")
    @PreAuthorize("@ss.hasAnyPermi('metal:material:add,metal:material:edit,metal:product:add,metal:product:edit,metal:quote-rule:edit')")
    @Log(title="新增金属类型",businessType=BusinessType.INSERT)
    public AjaxResult addMetal(@Valid @RequestBody MetalName input){return success().put("id",service.addMetal(input.name(),input.factoryId()));}
    @DeleteMapping("/metal/metal-types/{id}")
    @PreAuthorize("@ss.hasAnyPermi('metal:material:remove,metal:product:remove,metal:quote-rule:edit')")
    @Log(title="删除金属类型",businessType=BusinessType.DELETE)
    public AjaxResult removeMetal(@PathVariable Long id){service.removeMetal(id);return success();}
    @GetMapping("/metal/quote-rule/list")
    @PreAuthorize("@ss.hasPermi('metal:quote-rule:list')")
    public AjaxResult rules(@RequestParam(required=false) Long metalTypeId,@RequestParam(name="factoryId",required=false) Long factoryId){return success(service.rules(metalTypeId,factoryId));}

    /**
     * 查询当前报价规则。
     */
    @GetMapping("/metal/quote-rule")
    @PreAuthorize("@ss.hasPermi('metal:quote-rule:list')")
    public AjaxResult get(@RequestParam Long metalTypeId) {
        return success(service.rule(metalTypeId));
    }

    /**
     * 根据请求版本号更新记录，返回受影响的行数。
     */
    @PutMapping("/metal/quote-rule")
    @PreAuthorize("@ss.hasPermi('metal:quote-rule:edit')")
    @Log(title = "报价规则", businessType = BusinessType.UPDATE)
    public AjaxResult update(@Valid @RequestBody com.ruoyi.metal.catalog.MetalQuoteRule rule) {
        service.update(rule);
        return success();
    }

    public record RuleVersion(@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Min(0) Integer version){}
    @DeleteMapping("/metal/quote-rule/{metalTypeId}")
    @PreAuthorize("@ss.hasPermi('metal:quote-rule:edit')")
    @Log(title="删除金属报价规则",businessType=BusinessType.DELETE)
    public AjaxResult remove(@PathVariable Long metalTypeId,@Valid @RequestBody RuleVersion input){
        service.remove(metalTypeId,input.version());
        return success();
    }

    @GetMapping("/metal/volume-rule")
    @PreAuthorize("@ss.hasAnyPermi('metal:quote-rule:list,metal:quotation:list,metal:product:query,metal:product:add,metal:product:edit')")
    public AjaxResult volumeRule(@RequestParam(name="factoryId",required=false) Long factoryId){return success(service.volumeRule(factoryId));}

    @PutMapping("/metal/volume-rule")
    @PreAuthorize("@ss.hasPermi('metal:quote-rule:edit')")
    @Log(title="本厂体积参数",businessType=BusinessType.UPDATE)
    public AjaxResult updateVolumeRule(@Valid @RequestBody com.ruoyi.metal.catalog.VolumeRule rule,@RequestParam(name="factoryId",required=false) Long factoryId){
        service.updateVolumeRule(rule,factoryId);
        return success();
    }

    /**
     * 获取报价所需的基础资料和规则上下文。
     */
    @GetMapping("/metal/quotation/context")
    @PreAuthorize("@ss.hasPermi('metal:quotation:list')")
    public AjaxResult context(@RequestParam(name="factoryId",required=false) Long factoryId) {
        return success(service.context(factoryId));
    }

    /**
     * 获取产品计算所需的报价规则。
     */
    @GetMapping("/metal/product/calculation-rule")
    @PreAuthorize("@ss.hasAnyPermi('metal:product:query,metal:product:add,metal:product:edit')")
    public AjaxResult productRule(@RequestParam(required=false) Long metalTypeId,@RequestParam(name="factoryId",required=false) Long factoryId) {
        return success(metalTypeId==null?service.productRules(null,factoryId):service.productRules(metalTypeId,factoryId).stream().findFirst().orElseThrow(()->new com.ruoyi.common.exception.ServiceException("请先配置金属密度"))).put("volumeRule",service.volumeRule(factoryId));
    }

    /**
     * 校验请求参数后执行产品体积计算。
     */
    @PostMapping("/metal/quotation/calculate")
    @PreAuthorize("@ss.hasPermi('metal:quotation:list')")
    public AjaxResult calculate(@Valid @RequestBody com.ruoyi.metal.catalog.CalculationRequest input,@RequestParam(name="factoryId",required=false) Long factoryId) {
        return success(service.calculate(input,factoryId));
    }
}
