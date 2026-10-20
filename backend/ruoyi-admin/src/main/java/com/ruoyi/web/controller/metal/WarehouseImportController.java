package com.ruoyi.web.controller.metal;
import java.util.*;
import com.ruoyi.metal.service.WarehouseTemplateLayout;
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
 public WarehouseImportController(Validator validator,FactoryScope scope,com.ruoyi.metal.service.WarehouseImportService imports){this.validator=validator;this.scope=scope;this.imports=imports;}
 /**
  * 生成仓储明细导入模板及填写说明，不写入业务记录。
  */
 @com.ruoyi.common.annotation.Log(title="仓储模板下载",businessType=com.ruoyi.common.enums.BusinessType.EXPORT,isSaveResponseData=false)
 @GetMapping("/template") @PreAuthorize("@ss.hasPermi('metal:warehouse-order:import')")
 public void template(@RequestParam(required=false) String type,@RequestParam(defaultValue="false") boolean batchBound,HttpServletResponse response)throws Exception {
  var columns=type==null?WarehouseTemplateLayout.legacy():WarehouseTemplateLayout.inbound(type,batchBound);
  try(var book=new XSSFWorkbook()){
   var sheet=book.createSheet("物料清单");var row=sheet.createRow(0);var textStyle=book.createCellStyle();textStyle.setDataFormat(book.createDataFormat().getFormat("@"));
   for(int i=0;i<columns.size();i++){row.createCell(i).setCellValue(columns.get(i).label());sheet.setColumnWidth(i,4500);sheet.setDefaultColumnStyle(i,textStyle);}
   var note=book.createSheet("填写说明");String[] notes={
    type==null?"分类：RAW原材料、FINISHED成品、SEMI半成品":"本模板单据类型："+type+"；分类由当前入库类型确定，不需要填写。",
    type==null?"资料ID填写系统内部物料或产品ID，不能填写物料／产品编号；旧通用模板的库存ID、原出库明细ID请向系统管理员核对，不从下拉显示文字中提取。入库建议按入库类型重新下载含ID参考表的新模板。":
    "物料／产品ID：在本文件的“ID参考”工作表，按编号、名称和规格型号查找并复制ID列；原料使用物料ID，半成品和成品使用产品ID。ID不是编号，不要把下拉显示文字填入ID列。",
    "实测净重单位g，必填；原料还库允许0，其他单据须大于0，最多6位小数；原料填写毛重时，净重须等于毛重减杂质；净重留空且毛重已填时自动计算。",
    "原料毛重、杂质可不填；原料数量自动为0；成品和半成品数量(件)必填且为正整数；纯度、单位名称、用途、备注可不填。",
    batchBound?"已自动绑定加工批次，不填写批次号。":"非生产入库批次号可空，由系统生成；生产产出批次号必须留空。",
    "最多1000行，禁止公式；导入只填充待确认明细，点击单据确认后才过账。编号和ID请使用文本格式，避免Excel自动改写。"};
   for(int i=0;i<notes.length;i++)note.createRow(i).createCell(0).setCellValue(notes[i]);
   note.setColumnWidth(0,120*256);var noteStyle=book.createCellStyle();noteStyle.setWrapText(true);for(var noteRow:note){noteRow.getCell(0).setCellStyle(noteStyle);noteRow.setHeightInPoints(48);}
   if(type!=null){
    var reference=book.createSheet("ID参考");String[] headings={"ID","编号","名称","规格型号"};String[] keys={"id","code","name","specification"};var heading=reference.createRow(0);
    for(int i=0;i<headings.length;i++){heading.createCell(i).setCellValue(headings[i]);reference.setColumnWidth(i,(i==3?60:i==0?20:30)*256);reference.setDefaultColumnStyle(i,textStyle);}
    int rowIndex=1;for(var item:imports.templateReferences(type)){var referenceRow=reference.createRow(rowIndex++);for(int i=0;i<keys.length;i++){var cell=referenceRow.createCell(i);cell.setCellStyle(textStyle);cell.setCellValue(Objects.toString(item.get(keys[i]),""));}}
    reference.createFreezePane(0,1);
   }
   response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");response.setHeader("Content-Disposition","attachment; filename=warehouse-lines.xlsx");book.write(response.getOutputStream());
  }
 }
 @com.ruoyi.common.annotation.Log(title="仓储模板导入",businessType=com.ruoyi.common.enums.BusinessType.IMPORT)
 @PostMapping("/import") @PreAuthorize("@ss.hasPermi('metal:warehouse-order:import')")
 public AjaxResult parse(@RequestParam MultipartFile file,@RequestParam Long factoryId,@RequestParam Long warehouseId,@RequestParam String type,@RequestParam(required=false) Long workOrderId,@RequestParam(required=false) Long itemId,@RequestParam(required=false) Long processId)throws Exception{
  scope.check(factoryId);if(file.isEmpty()||file.getSize()>5*1024*1024)throw new ServiceException("请上传不超过5MB的xlsx模板");var result=new ArrayList<Line>();
  try(var book=new XSSFWorkbook(file.getInputStream())){
   if(book.getNumberOfSheets()==0)throw new ServiceException("模板没有物料清单");var sheet=book.getSheetAt(0);if(sheet.getLastRowNum()>1000)throw new ServiceException("模板最多1000行");
   var header=sheet.getRow(0);if(header==null)throw new ServiceException("模板缺少表头");var headers=new ArrayList<String>();for(int i=0;i<header.getLastCellNum();i++)headers.add(value(header,i));while(!headers.isEmpty()&&headers.getLast().isEmpty())headers.removeLast();var positions=WarehouseTemplateLayout.identify(headers,type);
   for(int n=1;n<=sheet.getLastRowNum();n++){
    var row=sheet.getRow(n);if(row==null)continue;boolean empty=true;for(int i=0;i<row.getLastCellNum();i++)if(!value(row,i).isEmpty())empty=false;if(empty)continue;
    final Row current=row;java.util.function.Function<String,String> at=key->positions.containsKey(key)?value(current,positions.get(key)):"";
    try{
     BigDecimal gross=dec(at.apply("grossWeight")),impurity=dec(at.apply("impurity")),net=dec(at.apply("netWeight"));if(net==null&&gross!=null)net=gross.subtract(impurity==null?BigDecimal.ZERO:impurity);
     String category=positions.containsKey("category")?at.apply("category"):WarehouseTemplateLayout.category(type);
     Line line=new Line(id(at.apply("inventoryId")),id(at.apply("sourceLineId")),category==null||category.isEmpty()?null:category,id(at.apply("referenceId")),at.apply("batchNo"),net,at.apply("quantity").isEmpty()?0L:Long.valueOf(at.apply("quantity")),gross,impurity,dec(at.apply("purity")),at.apply("organization"),at.apply("purpose"),at.apply("remark"),at.apply("receivingOrganization"));
     var errors=validator.validate(line);if(!errors.isEmpty())throw new IllegalArgumentException(errors.iterator().next().getMessage());if(net==null||net.signum()<0||(!"RAW_RETURN".equals(type)&&net.signum()==0)||line.quantity()<0)throw new IllegalArgumentException("RAW_RETURN".equals(type)?"净重不能小于0，数量不能为负":"净重须大于0，数量不能为负");result.add(line);
    }catch(Exception e){throw new ServiceException("第"+(n+1)+"行数据无效："+e.getMessage());}
   }
  }
  if(result.isEmpty())throw new ServiceException("模板没有物料明细");return AjaxResult.success(imports.preview(new com.ruoyi.metal.service.WarehouseImportService.Input(factoryId,warehouseId,type,workOrderId,itemId,processId,result,Map.of("name",file.getOriginalFilename()==null?"":file.getOriginalFilename(),"bytes",file.getSize(),"sha256",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(file.getBytes()))))));
 }
 private static String value(Row row,int index){if(row==null)return "";var cell=row.getCell(index);if(cell==null)return "";if(cell.getCellType()==CellType.FORMULA)throw new ServiceException("模板不支持公式单元格");return new DataFormatter().formatCellValue(cell).trim();}
 private static Long id(String value){return value.isEmpty()?null:Long.valueOf(value);}
 private static BigDecimal dec(String value){return value.isEmpty()?null:new BigDecimal(value);}
}
