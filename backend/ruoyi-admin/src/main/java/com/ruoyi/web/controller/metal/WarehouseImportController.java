package com.ruoyi.web.controller.metal;
import java.util.*;
import java.math.BigDecimal;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Validator;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.metal.catalog.WarehouseRequests.Line;
import com.ruoyi.metal.security.FactoryScope;
/**
 * 仓储明细导入 HTTP 接口，负责请求映射、参数校验和接口权限声明，业务处理交给对应服务。
 */
@RestController @RequestMapping("/metal/warehouse-order")
public class WarehouseImportController {
 private final Validator validator;private final FactoryScope scope;private final com.ruoyi.metal.service.WarehouseImportService imports;
 private static final String[] HEADERS={"库存ID","资料ID","原出库明细ID","分类","批次号","净重(g)","数量","毛重(g)","杂质(g)","纯度(%)","单位名称","用途","备注"};
 public WarehouseImportController(Validator validator,FactoryScope scope,com.ruoyi.metal.service.WarehouseImportService imports){this.validator=validator;this.scope=scope;this.imports=imports;}
 /**
  * 生成仓储明细导入模板及填写说明，不写入业务记录。
  */
 @com.ruoyi.common.annotation.Log(title="仓储模板下载",businessType=com.ruoyi.common.enums.BusinessType.EXPORT,isSaveResponseData=false) @GetMapping("/template") @PreAuthorize("@ss.hasPermi('metal:warehouse-order:import')") public void template(HttpServletResponse response)throws Exception {response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");response.setHeader("Content-Disposition","attachment; filename=warehouse-lines.xlsx");try(var book=new XSSFWorkbook()){var sheet=book.createSheet("物料清单");var row=sheet.createRow(0);for(int i=0;i<HEADERS.length;i++){row.createCell(i).setCellValue(HEADERS[i]);sheet.setColumnWidth(i,4500);}var note=book.createSheet("填写说明");String[] notes={"分类：RAW原材料、FINISHED成品、SEMI半成品","出库填写库存ID；入库填写资料ID（物料或产品ID）；还库填写原出库明细ID。可在物料选择框中查看ID。","净重单位g，最多6位小数；原料数量为0；成品半成品数量为正整数。","批次号可空，由系统生成；生产产出批次号必须留空。","最多1000行；禁止公式单元格；导入只填充明细，确认后才过账。"};for(int i=0;i<notes.length;i++)note.createRow(i).createCell(0).setCellValue(notes[i]);book.write(response.getOutputStream());}}
 /**
  * 校验上传模板和明细参数，返回待确认的单据明细，不执行过账。
  */
 @com.ruoyi.common.annotation.Log(title="仓储模板导入",businessType=com.ruoyi.common.enums.BusinessType.IMPORT) @PostMapping("/import") @PreAuthorize("@ss.hasPermi('metal:warehouse-order:import')") public AjaxResult parse(@RequestParam MultipartFile file,@RequestParam Long factoryId,@RequestParam Long warehouseId,@RequestParam String type,@RequestParam(required=false) Long workOrderId,@RequestParam(required=false) Long itemId,@RequestParam(required=false) Long processId)throws Exception{scope.check(factoryId);if(file.isEmpty()||file.getSize()>5*1024*1024)throw new ServiceException("请上传不超过5MB的xlsx模板");var result=new ArrayList<Line>();try(var book=new XSSFWorkbook(file.getInputStream())){var sheet=book.getSheetAt(0);if(sheet.getLastRowNum()>1000)throw new ServiceException("模板最多1000行");for(int i=0;i<HEADERS.length;i++)if(!HEADERS[i].equals(value(sheet.getRow(0),i)))throw new ServiceException("模板表头不匹配，请重新下载模板");for(int n=1;n<=sheet.getLastRowNum();n++){var row=sheet.getRow(n);if(row==null)continue;String[] a=new String[13];boolean empty=true;for(int i=0;i<a.length;i++){a[i]=value(row,i);if(!a[i].isEmpty())empty=false;}if(empty)continue;try{BigDecimal gross=dec(a[7]),impurity=dec(a[8]),net=dec(a[5]);if(net==null&&gross!=null)net=gross.subtract(impurity==null?BigDecimal.ZERO:impurity);Line line=new Line(id(a[0]),id(a[2]),a[3].isEmpty()?null:a[3],id(a[1]),a[4],net,a[6].isEmpty()?0L:Long.valueOf(a[6]),gross,impurity,dec(a[9]),a[10],a[11],a[12]);var errors=validator.validate(line);if(!errors.isEmpty())throw new IllegalArgumentException(errors.iterator().next().getMessage());if(net.signum()<=0||line.quantity()<0)throw new IllegalArgumentException("净重须大于0，数量不能为负");result.add(line);}catch(Exception e){throw new ServiceException("第"+(n+1)+"行数据无效："+e.getMessage());}}}if(result.isEmpty())throw new ServiceException("模板没有物料明细");return AjaxResult.success(imports.preview(new com.ruoyi.metal.service.WarehouseImportService.Input(factoryId,warehouseId,type,workOrderId,itemId,processId,result,Map.of("name",file.getOriginalFilename()==null?"":file.getOriginalFilename(),"bytes",file.getSize(),"sha256",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(file.getBytes()))))));}
 private static String value(Row row,int index){if(row==null)return "";var cell=row.getCell(index);if(cell==null)return "";if(cell.getCellType()==CellType.FORMULA)throw new ServiceException("模板不支持公式单元格");return new DataFormatter().formatCellValue(cell).trim();}
 private static Long id(String value){return value.isEmpty()?null:Long.valueOf(value);}
 private static BigDecimal dec(String value){return value.isEmpty()?null:new BigDecimal(value);}
}
