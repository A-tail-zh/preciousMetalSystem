package com.ruoyi.metal.service;

import java.util.*;
import java.util.function.IntFunction;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;

/** Modern workbook reads only frozen document values and PNG bytes. */
public final class QuotationWorkbook {
    private QuotationWorkbook(){}
    public static void write(Workbook book,Map<String,Object> quote,IntFunction<byte[]> image){
        var sheet=book.createSheet("报价单");var font=book.createFont();font.setFontName("微软雅黑");font.setFontHeightInPoints((short)11);
        var style=book.createCellStyle();style.setFont(font);style.setWrapText(true);style.setVerticalAlignment(VerticalAlignment.CENTER);
        for(int col=0;col<10;col++)sheet.setColumnWidth(col,14*256);
        int row=0;row=line(sheet,row,style,"报价单号："+quote.get("quoteNo"));row=line(sheet,row,style,"所属工厂："+quote.get("factoryName")+"    保存时间："+quote.get("createTime"));
        int index=0;
        for(Object value:(List<?>)quote.get("items")){
            Map<?,?> item=(Map<?,?>)value,rule=(Map<?,?>)item.get("rule"),volume=(Map<?,?>)item.get("volumeRule");index++;
            row=line(sheet,row+1,style,"商品 "+index+"  "+item.get("category")+"    "+item.get("processingTypeName")+"    数量："+item.get("quantity"));
            int start=row;
            List<String> lines=new ArrayList<>();Map<?,?> dims=(Map<?,?>)item.get("dimensions");
            for(Object field:(List<?>)item.get("dimensionFields")){Map<?,?> f=(Map<?,?>)field;lines.add(f.get("label")+"："+dims.get(f.get("key"))+f.get("unit"));}
            for(var field:List.of(new String[]{"powderWeight","来料粉料重量（g）"},new String[]{"nonPowderWeight","来料非粉料重量（g）"},new String[]{"lossWeight","理论损失重量（g）"},new String[]{"volume","单件体积（cm³）"},new String[]{"unitWeight","单件理论重量（g）"},new String[]{"totalWeight","总理论重量（g）"}))if(item.get(field[0])!=null)lines.add(field[1]+"："+item.get(field[0]));
            for(var field:List.of(new String[]{"density","密度（g/cm³）"},new String[]{"powderPrice","粉料单价（元/g）"},new String[]{"nonPowderPrice","非粉料单价（元/g）"},new String[]{"shortagePrice","欠料单价（元/g）"},new String[]{"lossPrice","理论损失单价（元/g）"}))lines.add(field[1]+"："+BusinessDecimal.display(rule.get(field[0])));
            lines.add("π："+volume.get("piValue")+"    弧底系数："+volume.get("curvedBottomFactor"));
            for(var field:List.of(new String[]{"powderAmount","粉料加工费（元）"},new String[]{"nonPowderAmount","非粉料加工费（元）"},new String[]{"productAmount","欠料产品费用（元）"},new String[]{"lossAmount","损失费用（元）"},new String[]{"totalPrice","商品金额（元）"}))lines.add(field[1]+"："+item.get(field[0]));
            for(String text:lines){var r=sheet.createRow(row);r.setHeightInPoints(28);var cell=r.createCell(0);cell.setCellStyle(style);cell.setCellValue(text);sheet.addMergedRegion(new CellRangeAddress(row,row,0,4));row++;}
            var anchor=book.getCreationHelper().createClientAnchor();anchor.setCol1(5);anchor.setCol2(10);anchor.setRow1(start);anchor.setRow2(start+14);anchor.setAnchorType(ClientAnchor.AnchorType.MOVE_AND_RESIZE);
            var picture=sheet.createDrawingPatriarch().createPicture(anchor,book.addPicture(image.apply(index),Workbook.PICTURE_TYPE_PNG));picture.resize(0.4);
        }
        line(sheet,row+1,style,"总报价（元）："+BusinessDecimal.display(quote.get("totalPrice")));sheet.createFreezePane(0,2);sheet.setFitToPage(true);sheet.getPrintSetup().setLandscape(true);sheet.getPrintSetup().setFitWidth((short)1);sheet.getPrintSetup().setFitHeight((short)0);
    }
    private static int line(Sheet sheet,int row,CellStyle style,String text){var r=sheet.createRow(row);r.setHeightInPoints(34);var cell=r.createCell(0);cell.setCellStyle(style);cell.setCellValue(text);sheet.addMergedRegion(new CellRangeAddress(row,row,0,9));return row+1;}
}
