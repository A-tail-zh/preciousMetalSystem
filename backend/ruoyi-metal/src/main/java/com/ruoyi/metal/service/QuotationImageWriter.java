package com.ruoyi.metal.service;
import java.util.Map;
import org.apache.poi.ss.usermodel.*;
/** Embeds a snapshot-derived image in column J, without moving existing quotation cells. */
public final class QuotationImageWriter {
 private QuotationImageWriter() {}
 public static void embed(Workbook book,Sheet sheet,Map<?,?> item,int row,ProductDimensionImage images){
  if(!ProductDimensionImage.supports(item.get("category")))return;
  byte[] png=images.render(String.valueOf(item.get("category")),item.get("dimensions") instanceof Map<?,?> dims?dims:null);
  sheet.getRow(row).setHeightInPoints(240);
  var anchor=book.getCreationHelper().createClientAnchor();
  anchor.setCol1(9);anchor.setRow1(row);anchor.setCol2(10);anchor.setRow2(row+1);anchor.setAnchorType(ClientAnchor.AnchorType.MOVE_AND_RESIZE);
  sheet.createDrawingPatriarch().createPicture(anchor,book.addPicture(png,Workbook.PICTURE_TYPE_PNG));
 }
}
