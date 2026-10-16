package com.ruoyi.web.controller.metal;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.metal.catalog.WarehouseRequests.*;
import com.ruoyi.metal.service.WarehouseService;
/**
 * 仓储 HTTP 接口，负责请求映射、参数校验和接口权限声明，业务处理交给对应服务。
 */
@RestController @Validated @RequestMapping("/metal")
public class WarehouseController extends BaseController {
 private final WarehouseService service;public WarehouseController(WarehouseService service){this.service=service;}
 /**
  * 分页查询库房列表。
  */
 @GetMapping("/warehouse/list") @PreAuthorize("@ss.hasPermi('metal:warehouse:list')") public Map<String,Object> warehouseList(@RequestParam(defaultValue="1") @Min(1) int pageNum,@RequestParam(defaultValue="16") @Min(1) @Max(100) int pageSize,@RequestParam(required=false) String filter,@RequestParam(required=false) Long factoryId){return service.list("warehouse",pageNum,pageSize,filter,factoryId);}
 /**
  * 查询库房详情。
  */
 @GetMapping("/warehouse/{id}") @PreAuthorize("@ss.hasPermi('metal:warehouse:query')") public AjaxResult warehouseDetail(@PathVariable @Positive Long id){return success(service.detail("warehouse",id));}
 /**
  * 批量归档库房记录。
  */
 @DeleteMapping("/warehouse/{ids}") @PreAuthorize("@ss.hasPermi('metal:warehouse:remove')") @Log(title="warehouse归档",businessType=BusinessType.DELETE) public AjaxResult warehouseArchive(@PathVariable Long[] ids){service.archive("warehouse",ids);return success();}
 /**
  * 分页查询库存列表。
  */
 @GetMapping("/inventory/list") @PreAuthorize("@ss.hasPermi('metal:inventory:list')") public Map<String,Object> inventoryList(@RequestParam(defaultValue="1") @Min(1) int pageNum,@RequestParam(defaultValue="16") @Min(1) @Max(100) int pageSize,@RequestParam(required=false) String filter,@RequestParam(required=false) Long factoryId){return service.list("inventory",pageNum,pageSize,filter,factoryId);}
 /**
  * 查询库存详情。
  */
 @GetMapping("/inventory/{id}") @PreAuthorize("@ss.hasPermi('metal:inventory:query')") public AjaxResult inventoryDetail(@PathVariable @Positive Long id){return success(service.detail("inventory",id));}
 /**
  * 批量归档库存记录。
  */
 @DeleteMapping("/inventory/{ids}") @PreAuthorize("@ss.hasPermi('metal:inventory:remove')") @Log(title="inventory归档",businessType=BusinessType.DELETE) public AjaxResult inventoryArchive(@PathVariable Long[] ids){service.archive("inventory",ids);return success();}
 /**
  * 分页查询仓储单据列表。
  */
 @GetMapping("/warehouse-order/list") @PreAuthorize("@ss.hasPermi('metal:warehouse-order:list')") public Map<String,Object> warehouseOrderList(@RequestParam(defaultValue="1") @Min(1) int pageNum,@RequestParam(defaultValue="16") @Min(1) @Max(100) int pageSize,@ModelAttribute com.ruoyi.metal.catalog.OrderListQuery query){return service.listOrders(pageNum,pageSize,query);}
 /**
  * 查询仓储单据详情。
  */
 @GetMapping("/warehouse-order/{id}") @PreAuthorize("@ss.hasPermi('metal:warehouse-order:query')") public AjaxResult warehouseOrderDetail(@PathVariable @Positive Long id){return success(service.detail("warehouse-order",id));}
 /**
  * 分页查询加工批次列表。
  */
 @GetMapping("/process-batch/list") @PreAuthorize("@ss.hasPermi('metal:process-batch:list')") public Map<String,Object> processBatchList(@RequestParam(defaultValue="1") @Min(1) int pageNum,@RequestParam(defaultValue="16") @Min(1) @Max(100) int pageSize,@RequestParam(required=false) String filter,@RequestParam(required=false) Long factoryId){return service.list("process-batch",pageNum,pageSize,filter,factoryId);}
 /**
  * 查询加工批次详情。
  */
 @GetMapping("/process-batch/{id}") @PreAuthorize("@ss.hasPermi('metal:process-batch:query')") public AjaxResult processBatchDetail(@PathVariable @Positive Long id){return success(service.detail("process-batch",id));}
 /**
  * 分页查询报工列表。
  */
 @GetMapping("/work-report/list") @PreAuthorize("@ss.hasPermi('metal:work-report:list')") public Map<String,Object> workReportList(@RequestParam(defaultValue="1") @Min(1) int pageNum,@RequestParam(defaultValue="16") @Min(1) @Max(100) int pageSize,@RequestParam(required=false) String filter,@RequestParam(required=false) Long factoryId){return service.list("work-report",pageNum,pageSize,filter,factoryId);}
 /**
  * 查询报工详情。
  */
 @GetMapping("/work-report/{id}") @PreAuthorize("@ss.hasPermi('metal:work-report:query')") public AjaxResult workReportDetail(@PathVariable @Positive Long id){return success(service.detail("work-report",id));}
 /**
  * 分页查询预警规则列表。
  */
 @GetMapping("/warning-rule/list") @PreAuthorize("@ss.hasPermi('metal:warning-rule:list')") public Map<String,Object> warningRuleList(@RequestParam(defaultValue="1") @Min(1) int pageNum,@RequestParam(defaultValue="16") @Min(1) @Max(100) int pageSize,@RequestParam(required=false) String filter,@RequestParam(required=false) Long factoryId){return service.list("warning-rule",pageNum,pageSize,filter,factoryId);}
 /**
  * 查询预警规则详情。
  */
 @GetMapping("/warning-rule/{id}") @PreAuthorize("@ss.hasPermi('metal:warning-rule:query')") public AjaxResult warningRuleDetail(@PathVariable @Positive Long id){return success(service.detail("warning-rule",id));}
 /**
  * 批量归档预警规则记录。
  */
 @DeleteMapping("/warning-rule/{ids}") @PreAuthorize("@ss.hasPermi('metal:warning-rule:remove')") @Log(title="warning-rule归档",businessType=BusinessType.DELETE) public AjaxResult warningRuleArchive(@PathVariable Long[] ids){service.archive("warning-rule",ids);return success();}
 /**
  * 分页查询预警记录列表。
  */
 @GetMapping("/warning-record/list") @PreAuthorize("@ss.hasPermi('metal:warning-record:list')") public Map<String,Object> warningRecordList(@RequestParam(defaultValue="1") @Min(1) int pageNum,@RequestParam(defaultValue="16") @Min(1) @Max(100) int pageSize,@RequestParam(required=false) String filter,@RequestParam(required=false) Long factoryId){return service.list("warning-record",pageNum,pageSize,filter,factoryId);}
 /**
  * 查询预警记录详情。
  */
 @GetMapping("/warning-record/{id}") @PreAuthorize("@ss.hasPermi('metal:warning-record:query')") public AjaxResult warningRecordDetail(@PathVariable @Positive Long id){return success(service.detail("warning-record",id));}
 /**
  * 批量归档预警记录记录。
  */
 @DeleteMapping("/warning-record/{ids}") @PreAuthorize("@ss.hasPermi('metal:warning-record:remove')") @Log(title="warning-record归档",businessType=BusinessType.DELETE) public AjaxResult warningRecordArchive(@PathVariable Long[] ids){service.archive("warning-record",ids);return success();}
 /**
  * 提交库房新增请求。
  */
 @PostMapping("/warehouse") @PreAuthorize("@ss.hasPermi('metal:warehouse:add')") @Log(title="库房新增",businessType=BusinessType.INSERT) public AjaxResult warehouseCreate(@Valid @RequestBody Warehouse input){if(input.id()!=null)throw new com.ruoyi.common.exception.ServiceException("新增不能指定ID");return success().put("id",service.saveWarehouse(input));}
 /**
  * 提交库房修改请求。
  */
 @PutMapping("/warehouse") @PreAuthorize("@ss.hasPermi('metal:warehouse:edit')") @Log(title="库房编辑",businessType=BusinessType.UPDATE) public AjaxResult warehouseEdit(@Valid @RequestBody Warehouse input){if(input.id()==null)throw new com.ruoyi.common.exception.ServiceException("编辑须指定ID");return success().put("id",service.saveWarehouse(input));}
 /**
  * 提交预警规则配置请求，由业务层判断该入口是否可用。
  */
 @PostMapping("/warning-rule") @PreAuthorize("@ss.hasPermi('metal:warning-rule:add')") @Log(title="预警规则新增",businessType=BusinessType.INSERT) public AjaxResult ruleCreate(@Valid @RequestBody Rule input){if(input.id()!=null)throw new com.ruoyi.common.exception.ServiceException("新增不能指定ID");return success().put("id",service.saveRule(input));}
 /**
  * 提交预警规则配置请求，由业务层判断该入口是否可用。
  */
 @PutMapping("/warning-rule") @PreAuthorize("@ss.hasPermi('metal:warning-rule:edit')") @Log(title="预警规则编辑",businessType=BusinessType.UPDATE) public AjaxResult ruleEdit(@Valid @RequestBody Rule input){if(input.id()==null)throw new com.ruoyi.common.exception.ServiceException("编辑须指定ID");return success().put("id",service.saveRule(input));}
 /**
  * 校验期初录入权限和单据类型后登记期初库存。
  */
 @PostMapping("/inventory/opening") @PreAuthorize("@ss.hasPermi('metal:inventory:add')") @Log(title="期初库存登记",businessType=BusinessType.INSERT) public AjaxResult opening(@Valid @RequestBody Order input){return success().put("id",service.opening(input));}
 /**
  * 按版本号修改库存基础信息，并限制已有单据引用的记录。
  */
 @PutMapping("/inventory") @PreAuthorize("@ss.hasPermi('metal:inventory:edit')") @Log(title="库存资料修改",businessType=BusinessType.UPDATE) public AjaxResult editStock(@Valid @RequestBody Stock input){service.editStock(input);return success();}
 /**
  * 提交库存调整请求。
  */
 @PostMapping("/inventory/{id}/adjust") @PreAuthorize("@ss.hasPermi('metal:inventory:adjust')") @Log(title="库存调整",businessType=BusinessType.UPDATE) public AjaxResult adjust(@PathVariable Long id,@Valid @RequestBody Adjustment input){return success().put("id",service.adjustStock(id,input));}
 public record NumberRequest(@NotBlank String requestId,@NotBlank String kind){}
 /**
  * 根据请求标识预留单据编号。
  */
 @PostMapping("/warehouse-order/number/reserve") @PreAuthorize("@ss.hasAnyPermi('metal:warehouse-order:add,metal:inventory:add')") public AjaxResult reserveNumber(@Valid @RequestBody NumberRequest input){if("ADJ".equals(input.kind())){if(!com.ruoyi.common.utils.SecurityUtils.hasPermi("metal:inventory:add"))throw new com.ruoyi.common.exception.ServiceException("无期初登记权限",403);}else{if(!com.ruoyi.common.utils.SecurityUtils.hasPermi("metal:warehouse-order:add"))throw new com.ruoyi.common.exception.ServiceException("无仓储登记权限",403);}return success().put("data",service.reserveOrderNumber(input.requestId(),input.kind()));}
 /**
  * 校验并过账仓储单据，同时更新相应库存。
  */
 @PostMapping("/warehouse-order") @PreAuthorize("@ss.hasPermi('metal:warehouse-order:add')") @Log(title="仓储过账",businessType=BusinessType.INSERT) public AjaxResult post(@Valid @RequestBody Order input){return success().put("id",service.post(input));}
 /**
  * 提交加工批次创建请求。
  */
 @PostMapping("/process-batch") @PreAuthorize("@ss.hasPermi('metal:process-batch:add')") @Log(title="加工批次新增",businessType=BusinessType.INSERT) public AjaxResult batch(@Valid @RequestBody Batch input){return success().put("id",service.createBatch(input));}
 /**
  * 校验加工批次状态和数量后结算，生成报工及损耗统计。
  */
 @PostMapping("/process-batch/{id}/settle") @PreAuthorize("@ss.hasPermi('metal:process-batch:settle')") @Log(title="批次损耗结算",businessType=BusinessType.UPDATE) public AjaxResult settle(@PathVariable Long id,@Valid @RequestBody Action input){service.settle(id,input);return success();}
 /**
  * 校验版本和后续业务引用后冲销加工批次。
  */
 @PostMapping("/process-batch/{id}/reverse") @PreAuthorize("@ss.hasPermi('metal:process-batch:reverse')") @Log(title="批次整体冲销",businessType=BusinessType.UPDATE) public AjaxResult reverseBatch(@PathVariable Long id,@Valid @RequestBody Action input){service.reverseBatch(id,input);return success();}
 /**
  * 提交仓储单据冲销请求。
  */
 @PostMapping("/warehouse-order/{id}/reverse") @PreAuthorize("@ss.hasPermi('metal:warehouse-order:reverse')") @Log(title="仓储单据冲销",businessType=BusinessType.UPDATE) public AjaxResult reverse(@PathVariable Long id,@Valid @RequestBody Action input){service.reverseOrder(id,input);return success();}
 /**
  * 提交预警处理请求。
  */
 @PostMapping("/warning-record/{id}/handle") @PreAuthorize("@ss.hasPermi('metal:warning-record:handle')") @Log(title="预警处理",businessType=BusinessType.UPDATE) public AjaxResult handle(@PathVariable Long id,@Valid @RequestBody Action input){service.handleWarning(id,input);return success();}
 /**
  * 获取工单生产损耗汇总。
  */
 @GetMapping("/work-report/summary") @PreAuthorize("@ss.hasAnyPermi('metal:work-report:list,metal:work-order:query')") public AjaxResult summary(@RequestParam Long workOrderId){return success(service.summaries(workOrderId));}
 /**
  * 获取指定工序的报工记录。
  */
 @GetMapping("/work-report/step") @PreAuthorize("@ss.hasAnyPermi('metal:work-report:query,metal:work-order:query')") public AjaxResult stepReports(@RequestParam Long itemId,@RequestParam Long processId){return success(service.reportsForStep(itemId,processId));}
 /**
  * 获取当前业务可选择的关联资料。
  */
 @GetMapping("/warehouse/options/{kind}") @PreAuthorize("@ss.hasAnyPermi('metal:warehouse:list,metal:inventory:list,metal:warehouse-order:list,metal:process-batch:list,metal:warning-rule:list,metal:warning-record:list')") public AjaxResult options(@PathVariable String kind,@RequestParam(required=false) Long factoryId,@RequestParam(required=false) Long referenceId){return success(service.options(kind,factoryId,referenceId));}
}
