package com.ruoyi.web.controller.metal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import com.github.pagehelper.PageHelper;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.metal.catalog.WorkOrderRequest;
import com.ruoyi.metal.service.WorkOrderService;

/**
 * 生产工单 HTTP 接口，负责请求映射、参数校验和接口权限声明，业务处理交给对应服务。
 */
@RestController
@Validated
@RequestMapping("/metal/work-order")
public class WorkOrderController extends BaseController {
    private final WorkOrderService service;

    public WorkOrderController(WorkOrderService service) {
        this.service = service;
    }

    /**
     * 查询列表并补充关联资料。
     */
    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('metal:work-order:list')")
    public TableDataInfo list(@RequestParam(defaultValue = "1") @Min(1) int pageNum, @RequestParam(defaultValue = "16") @Min(1) @Max(100) int pageSize) {
        return com.ruoyi.common.utils.PageUtils.withPage(pageNum, pageSize, () -> getDataTable(service.list()));
    }

    /**
     * 查询指定记录详情。
     */
    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('metal:work-order:query')")
    public AjaxResult detail(@PathVariable @Positive Long id) {
        return success(service.detail(id));
    }

    public record NumberRequest(
            @NotBlank @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String requestId) {
    }

    /**
     * 根据请求标识预留工单编号。
     */
    @PostMapping("/number/reserve")
    @PreAuthorize("@ss.hasPermi('metal:work-order:add')")
    public AjaxResult reserve(@Valid @RequestBody NumberRequest input) {
        return success().put("data", service.reserveNumber(input.requestId()));
    }

    /**
     * 新增记录并保存关联资料。
     */
    @PostMapping
    @PreAuthorize("@ss.hasPermi('metal:work-order:add')")
    @Log(title = "生产工单", businessType = BusinessType.INSERT)
    public AjaxResult create(@Valid @RequestBody WorkOrderRequest request) {
        return success().put("id", service.create(request));
    }

    /**
     * 修改记录并更新关联资料。
     */
    @PutMapping
    @PreAuthorize("@ss.hasPermi('metal:work-order:edit')")
    @Log(title = "生产工单", businessType = BusinessType.UPDATE)
    public AjaxResult edit(@Valid @RequestBody WorkOrderRequest request) {
        service.edit(request);
        return success();
    }

    /**
     * 提交批量删除请求，由业务层检查访问权限和引用限制。
     */
    @DeleteMapping("/{ids}")
    @PreAuthorize("@ss.hasPermi('metal:work-order:remove')")
    @Log(title = "生产工单", businessType = BusinessType.DELETE)
    public AjaxResult delete(@PathVariable Long[] ids) {
        service.delete(ids);
        return success();
    }

    /**
     * 校验工单状态与版本后提交审核通过操作。
     */
    @PostMapping("/{id}/approve")
    @PreAuthorize("@ss.hasPermi('metal:work-order:approve')")
    @Log(title = "工单审核通过", businessType = BusinessType.UPDATE)
    public AjaxResult approve(@PathVariable @Positive Long id, @Valid @RequestBody WorkOrderRequest.Action input) {
        service.transition(id, input, "approve");
        return success();
    }

    /**
     * 提交工单驳回操作。
     */
    @PostMapping("/{id}/reject")
    @PreAuthorize("@ss.hasPermi('metal:work-order:reject')")
    @Log(title = "工单驳回", businessType = BusinessType.UPDATE)
    public AjaxResult reject(@PathVariable @Positive Long id, @Valid @RequestBody WorkOrderRequest.Action input) {
        service.transition(id, input, "reject");
        return success();
    }

    /**
     * 提交工单重新审核操作。
     */
    @PostMapping("/{id}/resubmit")
    @PreAuthorize("@ss.hasPermi('metal:work-order:resubmit')")
    @Log(title = "工单重新提交", businessType = BusinessType.UPDATE)
    public AjaxResult resubmit(@PathVariable @Positive Long id, @Valid @RequestBody WorkOrderRequest.Action input) {
        service.transition(id, input, "resubmit");
        return success();
    }

    /**
     * 获取当前用户可访问的工厂选项。
     */
    @GetMapping("/options/factories")
    @PreAuthorize("@ss.hasAnyPermi('metal:work-order:query,metal:work-order:add,metal:work-order:edit')")
    public AjaxResult factories() {
        return success(service.factories());
    }

    /**
     * 获取指定工厂的产线选项。
     */
    @GetMapping("/options/lines")
    @PreAuthorize("@ss.hasAnyPermi('metal:work-order:query,metal:work-order:add,metal:work-order:edit')")
    public AjaxResult lines(@RequestParam @Positive Long factoryId) {
        return success(service.lines(factoryId));
    }

    /**
     * 获取指定工厂的负责人选项。
     */
    @GetMapping("/options/owners")
    @PreAuthorize("@ss.hasAnyPermi('metal:work-order:query,metal:work-order:add,metal:work-order:edit')")
    public AjaxResult owners(@RequestParam @Positive Long factoryId) {
        return success(service.owners(factoryId));
    }

    /**
     * 获取可选择的产品资料。
     */
    @GetMapping("/options/products")
    @PreAuthorize("@ss.hasAnyPermi('metal:work-order:query,metal:work-order:add,metal:work-order:edit')")
    public AjaxResult products(@RequestParam @Positive Long lineId) {
        return success(service.availableProducts(lineId));
    }

    /**
     * 读取指定产品在所选产线上的工序计划。
     */
    @GetMapping("/options/products/{id}/processes")
    @PreAuthorize("@ss.hasAnyPermi('metal:work-order:query,metal:work-order:add,metal:work-order:edit')")
    public AjaxResult processes(@PathVariable @Positive Long id, @RequestParam @Positive Long lineId) {
        return success(service.productPlans(id, lineId));
    }
}
