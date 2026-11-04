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
 private final com.ruoyi.metal.service.OutboundDraftService outboundDrafts;private final Validator validator;private final FactoryScope scope;private final com.ruoyi.metal.service.WarehouseImportService imports;
 public WarehouseImportController(Validator validator,FactoryScope scope,com.ruoyi.metal.service.WarehouseImportService imports,com.ruoyi.metal.service.OutboundDraftService outboundDrafts){this.outboundDrafts=outboundDrafts;this.validator=validator;this.scope=scope;this.imports=imports;}
 /**
  * 生成仓储明细导入模板及填写说明，不写入业务记录。
  */
 @com.ruoyi.common.annotation.Log(title="仓储模板下载",businessType=com.ruoyi.common.enums.BusinessType.EXPORT,isSaveResponseData=false)
 @GetMapping("/template") @PreAuthorize("@ss.hasPermi('metal:warehouse-order:import')")
 public void template(@RequestParam(required=false) String type,@RequestParam(defaultValue="false") boolean batchBound,@RequestParam(required=false) Long factoryId,@RequestParam(required=false) Long batchId,HttpServletResponse response)throws Exception {
  boolean raw=type!=null&&Set.of("RAW_IN","RAW_PROCESS_IN","RAW_RETURN","PROCESS_RAW_RETURN","EXCHANGE_OUT").contains(type);
  var customerReferences="RAW_IN".equals(type)?imports.customerReferences(factoryId):List.<Map<String,Object>>of();
  var columns=Set.of("RAW_PROCESS_IN","RAW_RETURN").contains(Objects.toString(type,""))&&batchId!=null?imports.templateColumns(type,factoryId,batchId):raw?WarehouseTemplateLayout.raw(type):type==null?WarehouseTemplateLayout.production():WarehouseTemplateLayout.inbound(type,batchBound);
  try(var book=new XSSFWorkbook()){
   var sheet=book.createSheet("物料清单");var row=sheet.createRow(0);var textStyle=book.createCellStyle();textStyle.setDataFormat(book.createDataFormat().getFormat("@"));
   for(int i=0;i<columns.size();i++){row.createCell(i).setCellValue(columns.get(i).label());sheet.setColumnWidth(i,4500);sheet.setDefaultColumnStyle(i,textStyle);}
   var note=book.createSheet("填写说明");String[] notes={
    type==null?"分类：RAW原材料、FINISHED成品、SEMI半成品":"本模板单据类型："+type+"；分类由当前入库类型确定，不需要填写。",
    type==null?"资料ID填写系统内部物料或产品ID，不能填写物料／产品编号；旧通用模板的库存ID、原出库明细ID请向系统管理员核对，不从下拉显示文字中提取。入库建议按入库类型重新下载含ID参考表的新模板。":
    "物料／产品ID：在本文件的“ID参考”工作表，按编号、名称和规格型号查找并复制ID列；原料使用物料ID，半成品和成品使用产品ID。ID不是编号，不要把下拉显示文字填入ID列。",
    "实测净重单位g，必填；原料还库允许0，其他单据须大于0，最多6位小数；原料填写毛重时，净重须等于毛重减杂质；净重留空且毛重已填时自动计算。",
    "原料毛重、杂质可不填；原料数量自动为0；按件成品/半成品数量(件)必填；重量型半成品数量列留空，参考件数列选填正整数；纯度、单位名称、用途、备注可不填。",
    batchBound?"已自动绑定加工批次，不填写批次号。":"非生产入库批次号可空，由系统生成；生产产出批次号必须留空。",
    "加工余料还库：人工选择物料ID，客户沿用工单；不填原出库行、库存ID和批次号。毛重、杂质成对填写；与本单已有明细共享批次可还额度。",
    "跨工单半成品领料须填写有效的半成品释放记录ID；半成品还库填写原出库明细ID，不能填成新产出。最多1000行，禁止公式；导入只填充待确认明细，点击单据确认后才过账。编号和ID请使用文本格式，避免Excel自动改写。"};
   if(raw)notes=new String[]{"原材料按净重管理，单位g；净重必填，原料还库允许0，其余须大于0。输入最多6位小数，过账前按两位小数核对。","毛重与杂质为可选重量；填写毛重时净重须等于毛重减杂质。铱粉来源须填写原生或回收再生；仅明确来源的原生铱粉可免酸洗，回收再生粉料不能冒充原生。","RAW_IN".equals(type)?"材料归属填写客户料或厂料；客户料须填写客户ID，厂料客户ID留空。客户必须属于当前工厂，不能按公司名称自动匹配。":"来源必须使用对应库存ID或原出库明细ID，客户沿用原来源。","物料ID请从ID参考表复制；最多1000行，禁止公式。导入只预览，确认单据后才过账。"};
   for(int i=0;i<notes.length;i++)note.createRow(i).createCell(0).setCellValue(notes[i]);
   note.setColumnWidth(0,120*256);var noteStyle=book.createCellStyle();noteStyle.setWrapText(true);for(var noteRow:note){noteRow.getCell(0).setCellStyle(noteStyle);noteRow.setHeightInPoints(48);}
   if("RAW_IN".equals(type)){var customers=book.createSheet("客户参考");String[] labels={"客户ID","客户名称","客户公司名称"},keys={"id","name","companyName"};var h=customers.createRow(0);for(int i=0;i<labels.length;i++){h.createCell(i).setCellValue(labels[i]);customers.setColumnWidth(i,32*256);customers.setDefaultColumnStyle(i,textStyle);}int n=1;for(var customer:customerReferences){var r=customers.createRow(n++);for(int i=0;i<keys.length;i++)r.createCell(i).setCellValue(Objects.toString(customer.get(keys[i]),""));}}
   if(type!=null&&Set.of("RAW_IN","RAW_PROCESS_IN","PROCESS_RAW_RETURN","SEMI_IN","FINISHED_IN").contains(type)){
    var reference=book.createSheet("ID参考");boolean rawReference=Set.of("RAW_IN","RAW_PROCESS_IN","PROCESS_RAW_RETURN").contains(type);String[] headings=rawReference?new String[]{"ID","编号","名称","规格型号","物料类型","金属类型"}:new String[]{"ID","编号","名称","规格型号"};String[] keys=rawReference?new String[]{"id","code","name","specification","material_type","metal_type_name"}:new String[]{"id","code","name","specification"};var heading=reference.createRow(0);
    for(int i=0;i<headings.length;i++){heading.createCell(i).setCellValue(headings[i]);reference.setColumnWidth(i,(i==3?60:i==0?20:30)*256);reference.setDefaultColumnStyle(i,textStyle);}
    int rowIndex=1;for(var item:imports.templateReferences(type,factoryId)){var referenceRow=reference.createRow(rowIndex++);for(int i=0;i<keys.length;i++){var cell=referenceRow.createCell(i);cell.setCellStyle(textStyle);cell.setCellValue(Objects.toString(item.get(keys[i]),""));}}
    reference.createFreezePane(0,1);
   }
   response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");response.setHeader("Content-Disposition","attachment; filename=warehouse-lines.xlsx");book.write(response.getOutputStream());
  }
 }
 public void template(String type,boolean batchBound,Long factoryId,HttpServletResponse response)throws Exception {template(type,batchBound,factoryId,null,response);}
 @com.ruoyi.common.annotation.Log(title="仓储模板导入",businessType=com.ruoyi.common.enums.BusinessType.IMPORT)
 @PostMapping("/import") @PreAuthorize("@ss.hasPermi('metal:warehouse-order:import')")
 public AjaxResult parse(@RequestParam MultipartFile file,@RequestParam Long factoryId,@RequestParam Long warehouseId,@RequestParam String type,@RequestParam(required=false) Long workOrderId,@RequestParam(required=false) Long itemId,@RequestParam(required=false) Long processId,@RequestParam(required=false) Long batchId,@RequestParam(required=false) String existingLines,@RequestParam(required=false) String submissionDraft)throws Exception{
  scope.check(factoryId);var customerVersions=new HashMap<Long,Integer>();if("RAW_IN".equals(type))for(var c:imports.customerReferences(factoryId))customerVersions.put(Long.valueOf(c.get("id").toString()),((Number)c.get("version")).intValue());if(file.isEmpty()||file.getSize()>5*1024*1024)throw new ServiceException("请上传不超过5MB的xlsx模板");var result=new ArrayList<Line>();
  try(var book=new XSSFWorkbook(file.getInputStream())){
   if(book.getNumberOfSheets()==0)throw new ServiceException("模板没有物料清单");var sheet=book.getSheetAt(0);if(sheet.getLastRowNum()>1000)throw new ServiceException("模板最多1000行");
   var header=sheet.getRow(0);if(header==null)throw new ServiceException("模板缺少表头");var headers=new ArrayList<String>();for(int i=0;i<header.getLastCellNum();i++)headers.add(value(header,i));while(!headers.isEmpty()&&headers.get(headers.size()-1).isEmpty())headers.remove(headers.size()-1);var positions=WarehouseTemplateLayout.identify(headers,type);
   for(int n=1;n<=sheet.getLastRowNum();n++){
    var row=sheet.getRow(n);if(row==null)continue;boolean empty=true;for(int i=0;i<row.getLastCellNum();i++)if(!value(row,i).isEmpty())empty=false;if(empty)continue;
    final Row current=row;java.util.function.Function<String,String> at=key->positions.containsKey(key)?value(current,positions.get(key)):"";
    try{
     BigDecimal gross=dec(at.apply("grossWeight"),"毛重"),impurity=dec(at.apply("impurity"),"杂质"),net=dec(at.apply("netWeight"),"实测净重");if(net==null&&gross!=null)net=com.ruoyi.metal.service.BusinessDecimal.round(gross).subtract(impurity==null?BigDecimal.ZERO:com.ruoyi.metal.service.BusinessDecimal.round(impurity));
     String category=positions.containsKey("category")?at.apply("category"):WarehouseTemplateLayout.category(type);if(category==null&&Set.of("RAW_RETURN","EXCHANGE_OUT").contains(type))category="RAW";
     Line line=new Line(id(at.apply("inventoryId")),id(at.apply("sourceLineId")),category==null||category.isEmpty()?null:category,id(at.apply("referenceId")),at.apply("batchNo"),net,at.apply("quantity").isEmpty()?0L:Long.valueOf(at.apply("quantity")),gross,impurity,dec(at.apply("purity"),"纯度"),at.apply("organization"),at.apply("purpose"),at.apply("remark"),at.apply("receivingOrganization"),id(at.apply("releaseId")),id(at.apply("skipPermitId")),at.apply("skipPermitVersion").isEmpty()?null:Integer.valueOf(at.apply("skipPermitVersion")),id(at.apply("referenceQuantity")),id(at.apply("customerId")),customerVersions.get(id(at.apply("customerId"))),switch(at.apply("materialOwnership")){case "工厂自有","厂料","FACTORY"->"FACTORY";case "客户料","CUSTOMER"->"CUSTOMER";case ""->null;default->throw new ServiceException("材料归属须填写客户料或厂料");},switch(at.apply("powderOrigin")){case "原生","NATIVE"->"NATIVE";case "回收再生","回收","RECYCLED"->"RECYCLED";case ""->null;default->throw new ServiceException("铱粉来源须填写原生或回收再生");});
     var errors=validator.validate(line);if(!errors.isEmpty()){var error=errors.iterator().next();throw new IllegalArgumentException(com.ruoyi.framework.web.exception.InputValidationErrors.label(error.getPropertyPath().toString())+"格式、长度或精度不符合要求");}if(net==null||net.signum()<0||(!"RAW_RETURN".equals(type)&&net.signum()==0)||line.quantity()<0)throw new IllegalArgumentException("RAW_RETURN".equals(type)?"净重不能小于0，数量不能为负":"净重须大于0，数量不能为负");result.add(line);
    }catch(NumberFormatException e){throw new ServiceException("第"+(n+1)+"行：编号、数量或版本须为范围内的整数");}catch(Exception e){throw new ServiceException("第"+(n+1)+"行数据无效："+e.getMessage());}
   }
  }
  if(result.isEmpty())throw new ServiceException("模板没有物料明细");
  int existingCount=0;
  if("PROCESS_RAW_RETURN".equals(type)&&existingLines!=null&&!existingLines.isBlank()){
   if(existingLines.length()>1048576)throw new ServiceException("已有明细过长");
   List<Line> drafts;try{var inputErrors=com.ruoyi.framework.web.exception.InputPayloadValidation.check(com.ruoyi.framework.web.exception.InputPayloadValidation.parse(existingLines),Line[].class);if(!inputErrors.isEmpty())throw new ServiceException(inputErrors.get(0).get("message"));drafts=com.alibaba.fastjson2.JSON.parseArray(existingLines,Line.class);}catch(Exception e){throw new ServiceException("已有加工余料明细格式无效");}
   if(drafts==null||drafts.size()>1000)throw new ServiceException("已有明细超过限制");
   existingCount=drafts.size();var combined=new ArrayList<Line>(drafts);combined.addAll(result);result=combined;
  }
  boolean newBatch=false;
  if(submissionDraft!=null&&!submissionDraft.isBlank()){
   if(!"PRODUCTION_OUT".equals(type)||submissionDraft.length()>1048576)throw new ServiceException("多仓草稿仅支持生产出库，且不能超过1MB");
   com.ruoyi.metal.catalog.OutboundSubmissionRequests.Preview draft;
   try{
    var errors=com.ruoyi.framework.web.exception.InputPayloadValidation.check(com.ruoyi.framework.web.exception.InputPayloadValidation.parse(submissionDraft),com.ruoyi.metal.catalog.OutboundSubmissionRequests.Preview.class);
    if(!errors.isEmpty())throw new ServiceException(errors.get(0).get("message"));
    draft=com.alibaba.fastjson2.JSON.parseObject(submissionDraft,com.ruoyi.metal.catalog.OutboundSubmissionRequests.Preview.class);
    if(draft==null||!validator.validate(draft).isEmpty())throw new ServiceException("多仓草稿字段不完整");
   }catch(Exception e){throw new ServiceException("多仓领料草稿格式无效，请刷新后重试");}
   if(!Objects.equals(factoryId,draft.factoryId())||!Objects.equals(workOrderId,draft.workOrderId())||!Objects.equals(itemId,draft.itemId())||!Objects.equals(processId,draft.processId())||!Objects.equals(batchId,draft.batchId()))throw new ServiceException("导入与多仓草稿的生产关联不一致，请重新选择");
   var groups=new ArrayList<com.ruoyi.metal.catalog.OutboundSubmissionRequests.Group>();boolean found=false;
   for(var group:draft.groups()){
    if(Objects.equals(warehouseId,group.warehouseId())){found=true;var lines=new ArrayList<Line>(group.lines());lines.addAll(result);groups.add(new com.ruoyi.metal.catalog.OutboundSubmissionRequests.Group(group.warehouseId(),null,lines));}
    else groups.add(new com.ruoyi.metal.catalog.OutboundSubmissionRequests.Group(group.warehouseId(),null,group.lines()));
   }
   if(!found)throw new ServiceException("导入仓库不在本次提交中");
   newBatch="NEW".equals(draft.batchAction());
   try{outboundDrafts.requireValid(outboundDrafts.previewImport(new com.ruoyi.metal.catalog.OutboundSubmissionRequests.Preview(factoryId,workOrderId,itemId,processId,batchId,draft.batchVersion(),groups,draft.batchAction(),draft.plannedQuantity())));}
   catch(com.ruoyi.metal.service.OutboundFieldException e){var response=AjaxResult.error(e.getMessage());response.put("fieldErrors",e.fields());return response;}
  }
  var input=new com.ruoyi.metal.service.WarehouseImportService.Input(factoryId,warehouseId,type,workOrderId,itemId,processId,result,Map.of("name",file.getOriginalFilename()==null?"":file.getOriginalFilename(),"bytes",file.getSize(),"sha256",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(file.getBytes()))),batchId);var preview=newBatch?imports.previewNewBatch(input):imports.preview(input);return AjaxResult.success(preview.subList(existingCount,preview.size()));
 }
 private static String value(Row row,int index){if(row==null)return "";var cell=row.getCell(index);if(cell==null)return "";if(cell.getCellType()==CellType.FORMULA)throw new ServiceException("模板不支持公式单元格");return new DataFormatter().formatCellValue(cell).trim();}
 private static Long id(String value){return value.isEmpty()?null:Long.valueOf(value);}
 private static BigDecimal dec(String value,String label){if(value.isEmpty())return null;if(!value.matches("[0-9]+(\\.[0-9]{1,6})?"))throw new ServiceException(label+"须为非负十进制数字，最多6位小数，不支持科学计数法");var number=new BigDecimal(value);if(number.precision()-number.scale()>14)throw new ServiceException(label+"最多14位整数");return number;}
}
