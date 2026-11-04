package com.ruoyi.web.controller.metal;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import com.ruoyi.metal.service.DashboardService;
import com.ruoyi.metal.service.ProductDimensionImage;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QuotationDisplayExportTest {
 @Test void legacyDownloadOmitsVolumeAndPricingInputsWithoutChangingTotals()throws Exception{
  var service=mock(DashboardService.class);
  when(service.quotation(1L)).thenReturn(Map.of("snapshotVersion",1,"quoteNo","QT-OLD","factoryName","历史厂","createTime","2026-11-04","totalPrice","42.00","items",List.of(Map.of("category","历史商品","quantity",2,"dimensions",Map.of("diameter","2.00"),"volume","9876.54","unitPrice","21.00","totalPrice","42.00","rule",Map.of("basePrice","1111.11","piValue","3.141592")))));
  var response=new MockHttpServletResponse();new DashboardController(service,new ProductDimensionImage()).download(1L,response);
  try(var book=new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))){
   var text=new StringBuilder();for(var row:book.getSheetAt(0))for(var cell:row)text.append(cell.getStringCellValue()).append('\n');
   for(String hidden:List.of("产品体积","基础单价","π值","9876.54","1111.11","3.141592"))assertFalse(text.toString().contains(hidden),hidden);
   assertTrue(text.toString().contains("42.00"));assertTrue(text.toString().contains("21.00"));assertTrue(text.toString().contains("直径：2.00"));
  }
 }
}
