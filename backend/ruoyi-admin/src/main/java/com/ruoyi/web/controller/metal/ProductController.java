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
import com.ruoyi.metal.catalog.ProductRecord;
import com.ruoyi.metal.service.ProductService;
/**
 * 产品 HTTP 接口，负责请求映射、参数校验和接口权限声明，业务处理交给对应服务。
 */
@RestController @Validated @RequestMapping("/metal/product")
public class ProductController extends BaseController {
 private final ProductService service;private final com.ruoyi.metal.service.ProductDimensionImage images; public ProductController(ProductService service,com.ruoyi.metal.service.ProductDimensionImage images){this.service=service;this.images=images;}
 /**
  * 查询列表并补充关联资料。
  */
 @GetMapping("/list") @Log(title="产品管理",businessType=BusinessType.OTHER,isSaveResponseData=false) @PreAuthorize("@ss.hasPermi('metal:product:list')") public TableDataInfo list(@RequestParam(defaultValue="1") @Min(1) int pageNum,@RequestParam(defaultValue="16") @Min(1) @Max(100) int pageSize, @ModelAttribute com.ruoyi.metal.catalog.ListQuery query){return com.ruoyi.common.utils.PageUtils.withPage(pageNum,pageSize,()->getDataTable(service.list(query)));}
 /**
  * 查询指定记录详情。
  */
 @GetMapping("/{id}") @Log(title="产品管理",businessType=BusinessType.OTHER,isSaveResponseData=false) @PreAuthorize("@ss.hasPermi('metal:product:query')") public AjaxResult detail(@PathVariable @Positive Long id){return success(service.detail(id));}
 /**
  * 新增记录并保存关联资料。
  */
 @GetMapping(value="/{id}/specification-image",produces="image/png") @PreAuthorize("@ss.hasPermi('metal:product:query')")
 public byte[] image(@PathVariable @Positive Long id,jakarta.servlet.http.HttpServletResponse response){var product=service.detail(id);response.setHeader("Cache-Control","no-store");return images.render(product.getCategory(),product.getDimensions());}
 @PostMapping @Log(title="产品管理",businessType=BusinessType.INSERT) @PreAuthorize("@ss.hasPermi('metal:product:add')") public AjaxResult create(@Valid @RequestBody ProductRecord form){return success().put("id",service.create(form));}
 /**
  * 修改记录并更新关联资料。
  */
 @PutMapping @Log(title="产品管理",businessType=BusinessType.UPDATE) @PreAuthorize("@ss.hasPermi('metal:product:edit')") public AjaxResult edit(@Valid @RequestBody ProductRecord form){service.edit(form);return success();}
 /**
  * 提交批量删除请求，由业务层检查访问权限和引用限制。
  */
 @DeleteMapping("/{ids}") @Log(title="产品管理",businessType=BusinessType.DELETE) @PreAuthorize("@ss.hasPermi('metal:product:remove')") public AjaxResult delete(@PathVariable Long[] ids){service.delete(ids);return success();}
}
