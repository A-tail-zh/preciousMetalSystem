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
import com.ruoyi.metal.catalog.ProcessRecord;
import com.ruoyi.metal.service.ProcessService;
/**
 * 工序 HTTP 接口，负责请求映射、参数校验和接口权限声明，业务处理交给对应服务。
 */
@RestController @Validated @RequestMapping("/metal/process")
public class ProcessController extends BaseController {
 private final ProcessService service; public ProcessController(ProcessService service){this.service=service;}
 /**
  * 查询列表并补充关联资料。
  */
 @GetMapping("/list") @Log(title="工序管理",businessType=BusinessType.OTHER,isSaveResponseData=false) @PreAuthorize("@ss.hasPermi('metal:process:list')") public TableDataInfo list(@RequestParam(defaultValue="1") @Min(1) int pageNum,@RequestParam(defaultValue="16") @Min(1) @Max(100) int pageSize, @ModelAttribute com.ruoyi.metal.catalog.ListQuery query){PageHelper.startPage(pageNum,pageSize);return getDataTable(service.list(query));}
 /**
  * 查询指定记录详情。
  */
 @GetMapping("/{id}") @Log(title="工序管理",businessType=BusinessType.OTHER,isSaveResponseData=false) @PreAuthorize("@ss.hasPermi('metal:process:query')") public AjaxResult detail(@PathVariable @Positive Long id){return success(service.detail(id));}
 /**
  * 新增记录并保存关联资料。
  */
 @PostMapping @Log(title="工序管理",businessType=BusinessType.INSERT) @PreAuthorize("@ss.hasPermi('metal:process:add')") public AjaxResult create(@Valid @RequestBody ProcessRecord form){return success().put("id",service.create(form));}
 /**
  * 修改记录并更新关联资料。
  */
 @PutMapping @Log(title="工序管理",businessType=BusinessType.UPDATE) @PreAuthorize("@ss.hasPermi('metal:process:edit')") public AjaxResult edit(@Valid @RequestBody ProcessRecord form){service.edit(form);return success();}
 /**
  * 提交批量删除请求，由业务层检查访问权限和引用限制。
  */
 @DeleteMapping("/{ids}") @Log(title="工序管理",businessType=BusinessType.DELETE) @PreAuthorize("@ss.hasPermi('metal:process:remove')") public AjaxResult delete(@PathVariable Long[] ids){service.delete(ids);return success();}
}
