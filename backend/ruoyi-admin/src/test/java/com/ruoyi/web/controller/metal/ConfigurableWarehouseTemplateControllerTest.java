package com.ruoyi.web.controller.metal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import com.ruoyi.metal.service.*;
import com.ruoyi.metal.security.FactoryScope;
import jakarta.validation.Validator;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ConfigurableWarehouseTemplateControllerTest {
 @Test void boundRawReturnDownloadDelegatesToItsActualSourceColumns()throws Exception{
  var service=mock(WarehouseImportService.class);when(service.templateColumns("RAW_RETURN",7L,19L)).thenReturn(WarehouseTemplateLayout.raw("RAW_RETURN",true));
  var controller=new WarehouseImportController(mock(Validator.class),mock(FactoryScope.class),service,mock(OutboundDraftService.class));var response=new MockHttpServletResponse();controller.template("RAW_RETURN",true,7L,19L,response);
  try(var workbook=new XSSFWorkbook(new java.io.ByteArrayInputStream(response.getContentAsByteArray()))){assertEquals("数量(件)",workbook.getSheetAt(0).getRow(0).getCell(2).getStringCellValue());}
  verify(service).templateColumns("RAW_RETURN",7L,19L);
 }
 @Test void rawProcessDownloadDelegatesRealFactoryAndBatchToTheService()throws Exception{
  var service=mock(WarehouseImportService.class);when(service.templateColumns("RAW_PROCESS_IN",7L,19L)).thenReturn(List.of(new WarehouseTemplateLayout.Column("referenceId","物料／产品ID"),new WarehouseTemplateLayout.Column("netWeight","实测净重(g)"),new WarehouseTemplateLayout.Column("quantity","数量(件)")));
  var controller=new WarehouseImportController(mock(Validator.class),mock(FactoryScope.class),service,mock(OutboundDraftService.class));var response=new MockHttpServletResponse();controller.template("RAW_PROCESS_IN",true,7L,19L,response);
  try(var workbook=new XSSFWorkbook(new java.io.ByteArrayInputStream(response.getContentAsByteArray()))){assertEquals("数量(件)",workbook.getSheetAt(0).getRow(0).getCell(2).getStringCellValue());}
  verify(service).templateColumns("RAW_PROCESS_IN",7L,19L);
 }
 @Test void oldJavaCallAndOrdinaryRawDownloadKeepTheOriginalColumns()throws Exception{
  var service=mock(WarehouseImportService.class);var controller=new WarehouseImportController(mock(Validator.class),mock(FactoryScope.class),service,mock(OutboundDraftService.class));var response=new MockHttpServletResponse();controller.template("RAW_PROCESS_IN",true,7L,response);
  try(var workbook=new XSSFWorkbook(new java.io.ByteArrayInputStream(response.getContentAsByteArray()))){for(var cell:workbook.getSheetAt(0).getRow(0))assertFalse(cell.getStringCellValue().contains("数量"));}
  verify(service,never()).templateColumns(any(),any(),any());
 }
}
