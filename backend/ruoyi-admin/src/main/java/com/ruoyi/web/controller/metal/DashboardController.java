package com.ruoyi.web.controller.metal;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import com.ruoyi.common.exception.ServiceException;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.metal.catalog.QuotationSaveRequest;
import com.ruoyi.metal.service.DashboardService;
/**
 * 首页概览与报价单 HTTP 接口，负责请求映射、参数校验和接口权限声明，业务处理交给对应服务。
 */
@RestController @RequestMapping("/metal")
public class DashboardController extends BaseController {
 private final DashboardService service;private final com.ruoyi.metal.service.ProductDimensionImage images;public DashboardController(DashboardService service,com.ruoyi.metal.service.ProductDimensionImage images){this.service=service;this.images=images;}
 /**
  * 获取当前用户可访问的首页概览数据。
  */
 @GetMapping("/dashboard") @PreAuthorize("isAuthenticated()") public AjaxResult dashboard(){return success(service.dashboard());}
 /**
  * 获取当前用户可访问的工厂选项。
  */
 @GetMapping("/quotation/factories") @PreAuthorize("@ss.hasPermi('metal:quotation:list')") public AjaxResult factories(){return success(service.quotationFactories());}
 /**
  * 保存报价单并返回保存结果。
  */
 @PostMapping("/quotation/save") @PreAuthorize("@ss.hasPermi('metal:quotation:add')") @Log(title="保存报价单",businessType=BusinessType.INSERT) public AjaxResult save(@Valid @RequestBody QuotationSaveRequest input){return success(service.saveQuotation(input));}
 /**
  * 查询指定报价单详情。
  */
 @GetMapping("/quotation/orders/{id}") @PreAuthorize("@ss.hasPermi('metal:quotation:list')") public AjaxResult quote(@PathVariable Long id){return success(service.quotation(id));}

 /**
  * 生成并下载指定报价单文件。
  */
 @GetMapping("/quotation/orders/{id}/download")
 @PreAuthorize("@ss.hasPermi('metal:quotation:list')")
 @Log(title="下载报价单",businessType=BusinessType.EXPORT,isSaveResponseData=false)
 public void download(@PathVariable Long id,HttpServletResponse response)throws Exception {
  var quote=service.quotation(id);
  try(var book=new XSSFWorkbook()){
   var sheet=book.createSheet("报价单");var font=book.createFont();font.setFontName("微软雅黑");font.setFontHeightInPoints((short)11);
   var style=book.createCellStyle();style.setFont(font);style.setWrapText(true);style.setVerticalAlignment(VerticalAlignment.CENTER);
   var heading=book.createCellStyle();heading.cloneStyleFrom(style);heading.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());heading.setFillPattern(FillPatternType.SOLID_FOREGROUND);var bold=book.createFont();bold.setFontName("微软雅黑");bold.setBold(true);heading.setFont(bold);
   var headerStyle=book.createCellStyle();headerStyle.cloneStyleFrom(style);headerStyle.setAlignment(HorizontalAlignment.LEFT);
   String factoryHeader="所属工厂："+quote.get("factoryName");
   var header=sheet.createRow(0);write(header,headerStyle,"报价单号："+quote.get("quoteNo"),"","",factoryHeader,"","","保存时间："+quote.get("createTime"),"","");
   for(int start:new int[]{0,3,6})sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0,0,start,start+2));
   int factoryTextWidth=factoryHeader.codePoints().map(c->c>255?2:1).sum();
   header.setHeightInPoints(Math.max(34,((factoryTextWidth+79)/80)*18+12));
   boolean hasImages=((List<?>)quote.get("items")).stream().anyMatch(o->o instanceof Map<?,?> item && com.ruoyi.metal.service.ProductDimensionImage.supports(item.get("category")));
   write(sheet.createRow(4),heading,"序号","商品类型","数量","规格尺寸（cm）","产品体积（cm³）","单件报价（元）","金额（元）","基础单价（元）","π值");
   var labels=Map.of("innerDiameter","内径","outerDiameter","外径","diameter","直径","innerHeight","内高","height","高度","length","长度","wallThickness","壁厚","bottomThickness","底厚","rHeight","R高度");
   if(!(quote.get("items") instanceof List<?> items))throw new ServiceException("报价明细格式异常");int n=5;
   for(Object obj:items){if(!(obj instanceof Map<?,?> item))throw new ServiceException("报价明细格式异常");StringJoiner specs=new StringJoiner("；");if(item.get("dimensions") instanceof Map<?,?> dims)for(var entry:dims.entrySet())specs.add(labels.getOrDefault(String.valueOf(entry.getKey()),String.valueOf(entry.getKey()))+"："+entry.getValue());Map<?,?> rule=item.get("rule") instanceof Map<?,?> map?map:Map.of();write(sheet.createRow(n),style,n-4,item.get("category"),item.get("quantity"),specs.toString(),item.get("volume"),item.get("unitPrice"),item.get("totalPrice"),rule.get("basePrice"),rule.get("piValue"));
    com.ruoyi.metal.service.QuotationImageWriter.embed(book,sheet,item,n,images);
    n++;}
   write(sheet.createRow(n+1),heading,"总报价（元）",quote.get("totalPrice"));sheet.createFreezePane(0,5);for(int i=0;i<9;i++)sheet.setColumnWidth(i,(i==3?48:i==0?8:22)*256);
   if(hasImages){var imageHeading=sheet.getRow(4).createCell(9);imageHeading.setCellValue("产品图片");imageHeading.setCellStyle(heading);sheet.setColumnWidth(9,68*256);}
   response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");response.setHeader("Content-Disposition","attachment; filename="+quote.get("quoteNo")+".xlsx");book.write(response.getOutputStream());
  }
 }
 @GetMapping(value="/quotation/orders/{id}/items/{index}/image",produces="image/png")
 @PreAuthorize("@ss.hasPermi('metal:quotation:list')")
 public byte[] itemImage(@PathVariable Long id,@PathVariable int index,HttpServletResponse response){
  var quote=service.quotation(id);var items=(List<?>)quote.get("items");
  if(index<1||index>items.size())throw new ServiceException("报价明细不存在");
  var item=(Map<?,?>)items.get(index-1);response.setHeader("Cache-Control","no-store");
  return images.render(String.valueOf(item.get("category")),item.get("dimensions") instanceof Map<?,?> dims?dims:null);
 }
 private static void write(Row row,CellStyle style,Object... values){row.setHeightInPoints(34);for(int i=0;i<values.length;i++){var cell=row.createCell(i);cell.setCellStyle(style);cell.setCellValue(values[i]==null?"":String.valueOf(values[i]));}}
}
