package com.ruoyi.metal.service;
import java.util.*;import java.io.*;import javax.imageio.ImageIO;import org.junit.jupiter.api.Test;import org.apache.poi.xssf.usermodel.*;
import static org.junit.jupiter.api.Assertions.*;
class ProductDimensionImageTest {
 private Map<String,String> dims(){return new HashMap<>(Map.of("innerDiameter","12.3","innerHeight","8.7","wallThickness","0.25","bottomThickness","0.4","rHeight","1.2"));}
 @Test void fillsAllFiveFieldsWithoutChangingTemplateElsewhere()throws Exception{
  var renderer=new ProductDimensionImage();var image=ImageIO.read(new ByteArrayInputStream(renderer.render(ProductDimensionImage.CATEGORY,dims())));
  var source=ImageIO.read(getClass().getResourceAsStream("/templates/product-images/iridium-crucible-curved-bottom.png"));
  assertEquals(1536,image.getWidth());assertEquals(1024,image.getHeight());int[] ys={548,632,716,802,887};int[] changed=new int[5];
  for(int y=0;y<1024;y++)for(int x=0;x<1536;x++)if(image.getRGB(x,y)!=source.getRGB(x,y)){int at=-1;for(int i=0;i<5;i++)if(x>=1104&&x<1406&&y>=ys[i]&&y<ys[i]+44)at=i;assertTrue(at>=0,"Template outside value areas changed");changed[at]++;}
  for(int count:changed)assertTrue(count>20,"Every field must receive visible text");
 }
 @Test void changesAreIsolatedAndConcurrentRequestsDoNotMutateTemplate()throws Exception{
  var renderer=new ProductDimensionImage();byte[] original=renderer.render(ProductDimensionImage.CATEGORY,dims());var changed=dims();changed.put("innerHeight","9.9");
  var first=ImageIO.read(new ByteArrayInputStream(original));var second=ImageIO.read(new ByteArrayInputStream(renderer.render(ProductDimensionImage.CATEGORY,changed)));
  boolean different=false;for(int y=0;y<1024;y++)for(int x=0;x<1536;x++)if(first.getRGB(x,y)!=second.getRGB(x,y)){different=true;assertTrue(x>=1104&&x<1406&&y>=632&&y<676);}
  assertTrue(different);assertArrayEquals(original,renderer.render(ProductDimensionImage.CATEGORY,dims()));
  for(int i=0;i<4;i++)assertArrayEquals(original,java.util.concurrent.CompletableFuture.supplyAsync(()->renderer.render(ProductDimensionImage.CATEGORY,dims())).get());
 }
 @Test void invalidDimensionsRejectedAndLongestNumbersFit()throws Exception{
  var renderer=new ProductDimensionImage();for(String value:new String[]{"0","-1","text","0.0000001","100000000000000"}){var d=dims();d.put("rHeight",value);assertThrows(com.ruoyi.common.exception.ServiceException.class,()->renderer.render(ProductDimensionImage.CATEGORY,d));}
  var missing=dims();missing.remove("rHeight");assertThrows(com.ruoyi.common.exception.ServiceException.class,()->renderer.render(ProductDimensionImage.CATEGORY,missing));
  assertThrows(com.ruoyi.common.exception.ServiceException.class,()->renderer.render("未配置的类别",dims()));var maximum=dims();maximum.replaceAll((k,v)->"99999999999999.999999");assertNotNull(ImageIO.read(new ByteArrayInputStream(renderer.render(ProductDimensionImage.CATEGORY,maximum))));
 }
 @Test void workbookContainsImagesInCorrectRowsAndSnapshotDimensions()throws Exception{
  var renderer=new ProductDimensionImage();var first=dims();var second=dims();second.put("innerDiameter","20");
  try(var book=new XSSFWorkbook();var out=new ByteArrayOutputStream()){
   var sheet=book.createSheet("报价单");for(int row=5;row<8;row++){sheet.createRow(row).createCell(5).setCellValue("123.456789");}
   QuotationImageWriter.embed(book,sheet,Map.of("category",ProductDimensionImage.CATEGORY,"dimensions",first),5,renderer);
   QuotationImageWriter.embed(book,sheet,Map.of("category","未配置的类别","dimensions",Map.of()),6,renderer);
   QuotationImageWriter.embed(book,sheet,Map.of("category",ProductDimensionImage.CATEGORY,"dimensions",second),7,renderer);
   book.write(out);try(var saved=new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))){
    var shapes=saved.getSheetAt(0).getDrawingPatriarch().getShapes();assertEquals(2,shapes.size());
    for(int i=0;i<2;i++){var picture=(XSSFPicture)shapes.get(i);assertEquals(i==0?5:7,picture.getClientAnchor().getRow1());assertEquals(9,picture.getClientAnchor().getCol1());assertArrayEquals(renderer.render(ProductDimensionImage.CATEGORY,i==0?first:second),picture.getPictureData().getData());}
    assertEquals("123.456789",saved.getSheetAt(0).getRow(5).getCell(5).getStringCellValue());assertEquals(240,saved.getSheetAt(0).getRow(7).getHeightInPoints());
   }
  }
 }

 @Test void solidRodFillsOnlyDiameterAndLengthAndKeepsRequestsIndependent()throws Exception{
  var renderer=new ProductDimensionImage();var dimensions=Map.of("diameter","2.5","length","20");
  var bytes=renderer.render(ProductDimensionImage.SOLID_ROD,dimensions);var image=ImageIO.read(new ByteArrayInputStream(bytes));
  var source=ImageIO.read(getClass().getResourceAsStream("/templates/product-images/iridium-rod-solid.png"));
  int[] ys={462,625};int[] changed=new int[2];
  for(int y=0;y<1024;y++)for(int x=0;x<1536;x++)if(image.getRGB(x,y)!=source.getRGB(x,y)){int at=-1;for(int i=0;i<2;i++)if(x>=1134&&x<1386&&y>=ys[i]&&y<ys[i]+64)at=i;assertTrue(at>=0);changed[at]++;}
  assertTrue(changed[0]>20);assertTrue(changed[1]>20);
  var second=ImageIO.read(new ByteArrayInputStream(renderer.render(ProductDimensionImage.SOLID_ROD,Map.of("diameter","2.5","length","25"))));
  boolean different=false;for(int y=0;y<1024;y++)for(int x=0;x<1536;x++)if(image.getRGB(x,y)!=second.getRGB(x,y)){different=true;assertTrue(x>=1134&&x<1386&&y>=625&&y<689);}
  assertTrue(different);assertArrayEquals(bytes,renderer.render(ProductDimensionImage.SOLID_ROD,dimensions));
 }
 @Test void solidRodRejectsMissingOrInvalidDimensionsAndSupportsMaximumPrecision()throws Exception{
  var renderer=new ProductDimensionImage();assertTrue(ProductDimensionImage.supports("铱杆实心"));assertTrue(ProductDimensionImage.supports("铱坩埚弧底"));assertTrue(ProductDimensionImage.supports("铱杆空心"));assertFalse(ProductDimensionImage.supports(null));
  for(var bad:java.util.List.of(Map.of("diameter","2"),Map.of("diameter","0","length","10"),Map.of("diameter","2","length","-1"),Map.of("diameter","2","length","bad")))assertThrows(com.ruoyi.common.exception.ServiceException.class,()->renderer.render(ProductDimensionImage.SOLID_ROD,bad));
  assertNotNull(ImageIO.read(new ByteArrayInputStream(renderer.render(ProductDimensionImage.SOLID_ROD,Map.of("diameter","99999999999999.999999","length","99999999999999.999999")))));
  assertArrayEquals(renderer.render(ProductDimensionImage.SOLID_ROD,Map.of("diameter","2.500000","length","20.000000")),renderer.render(ProductDimensionImage.SOLID_ROD,Map.of("diameter","2.5","length","20")));
 }
 @Test void mixedTemplatesStayWithTheirOwnQuotationRows()throws Exception{
  var renderer=new ProductDimensionImage();try(var book=new XSSFWorkbook();var out=new ByteArrayOutputStream()){
   var sheet=book.createSheet("报价单");for(int row=5;row<=7;row++)sheet.createRow(row);
   var rod=Map.of("category",ProductDimensionImage.SOLID_ROD,"dimensions",Map.of("diameter","2.5","length","20"));
   var crucible=Map.of("category",ProductDimensionImage.CATEGORY,"dimensions",dims());
   QuotationImageWriter.embed(book,sheet,rod,5,renderer);QuotationImageWriter.embed(book,sheet,Map.of("category","未配置的类别"),6,renderer);QuotationImageWriter.embed(book,sheet,crucible,7,renderer);
   book.write(out);try(var reopened=new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))){var shapes=reopened.getSheetAt(0).getDrawingPatriarch().getShapes();assertEquals(2,shapes.size());
    var first=(XSSFPicture)shapes.get(0);var last=(XSSFPicture)shapes.get(1);assertEquals(5,first.getClientAnchor().getRow1());assertEquals(7,last.getClientAnchor().getRow1());
    assertArrayEquals(renderer.render(ProductDimensionImage.SOLID_ROD,Map.of("diameter","2.5","length","20")),first.getPictureData().getData());assertArrayEquals(renderer.render(ProductDimensionImage.CATEGORY,dims()),last.getPictureData().getData());
   }
  }
 }

 @Test void hollowRodFillsOnlyThreeFieldsAndLengthChangesStayInItsBox()throws Exception{
  var renderer=new ProductDimensionImage();var dims=Map.of("outerDiameter","4","innerDiameter","2.5","length","30");
  var bytes=renderer.render(ProductDimensionImage.HOLLOW_ROD,dims);var image=ImageIO.read(new ByteArrayInputStream(bytes));
  var source=ImageIO.read(getClass().getResourceAsStream("/templates/product-images/iridium-rod-hollow.png"));int[] ys={338,494,665},heights={74,77,76},changed=new int[3];
  for(int y=0;y<1024;y++)for(int x=0;x<1536;x++)if(image.getRGB(x,y)!=source.getRGB(x,y)){int at=-1;for(int i=0;i<3;i++)if(x>=1217&&x<1403&&y>=ys[i]&&y<ys[i]+heights[i])at=i;assertTrue(at>=0);changed[at]++;}
  for(int c:changed)assertTrue(c>20);
  var other=ImageIO.read(new ByteArrayInputStream(renderer.render(ProductDimensionImage.HOLLOW_ROD,Map.of("outerDiameter","4","innerDiameter","2.5","length","40"))));boolean different=false;
  for(int y=0;y<1024;y++)for(int x=0;x<1536;x++)if(image.getRGB(x,y)!=other.getRGB(x,y)){different=true;assertTrue(x>=1217&&x<1403&&y>=665&&y<741);}assertTrue(different);assertArrayEquals(bytes,renderer.render(ProductDimensionImage.HOLLOW_ROD,dims));
 }
 @Test void hollowRodRejectsMissingInvalidAndInvertedDiameters()throws Exception{
  var renderer=new ProductDimensionImage();for(var dims:java.util.List.of(Map.of("outerDiameter","4","length","30"),Map.of("outerDiameter","4","innerDiameter","4","length","30"),Map.of("outerDiameter","4","innerDiameter","5","length","30"),Map.of("outerDiameter","4","innerDiameter","0","length","30"),Map.of("outerDiameter","4","innerDiameter","2","length","bad")))assertThrows(com.ruoyi.common.exception.ServiceException.class,()->renderer.render(ProductDimensionImage.HOLLOW_ROD,dims));
  assertNotNull(ImageIO.read(new ByteArrayInputStream(renderer.render(ProductDimensionImage.HOLLOW_ROD,Map.of("outerDiameter","99999999999999.999999","innerDiameter","99999999999998.999999","length","99999999999999.999999")))));
 }
 @Test void hollowRodAndSolidRodRemainInTheirOwnWorkbookRows()throws Exception{
  var renderer=new ProductDimensionImage();var dims=Map.of("outerDiameter","4","innerDiameter","2.5","length","30");
  try(var book=new XSSFWorkbook();var out=new ByteArrayOutputStream()){
   var sheet=book.createSheet("报价单");sheet.createRow(5);sheet.createRow(6);
   QuotationImageWriter.embed(book,sheet,Map.of("category",ProductDimensionImage.HOLLOW_ROD,"dimensions",dims),5,renderer);
   QuotationImageWriter.embed(book,sheet,Map.of("category",ProductDimensionImage.SOLID_ROD,"dimensions",Map.of("diameter","2","length","20")),6,renderer);
   book.write(out);try(var saved=new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))){var shapes=saved.getSheetAt(0).getDrawingPatriarch().getShapes();assertEquals(2,shapes.size());var first=(XSSFPicture)shapes.get(0);assertEquals(5,first.getClientAnchor().getRow1());assertArrayEquals(renderer.render(ProductDimensionImage.HOLLOW_ROD,dims),first.getPictureData().getData());assertEquals(6,((XSSFPicture)shapes.get(1)).getClientAnchor().getRow1());}
  }
 }

 @Test void ringFillsThreeBoxesAndHeightDoesNotAffectOtherFields()throws Exception{
  var renderer=new ProductDimensionImage();var dimensions=Map.of("outerDiameter","6","innerDiameter","4","height","1.5");assertTrue(ProductDimensionImage.supports("铱环"));
  var bytes=renderer.render(ProductDimensionImage.RING,dimensions);var image=ImageIO.read(new ByteArrayInputStream(bytes));var source=ImageIO.read(getClass().getResourceAsStream("/templates/product-images/iridium-ring.png"));
  int[] xs={1178,1180,1180},ys={303,517,731},widths={217,215,215},changed=new int[3];
  for(int y=0;y<1024;y++)for(int x=0;x<1536;x++)if(image.getRGB(x,y)!=source.getRGB(x,y)){int at=-1;for(int i=0;i<3;i++)if(x>=xs[i]&&x<xs[i]+widths[i]&&y>=ys[i]&&y<ys[i]+109)at=i;assertTrue(at>=0);changed[at]++;}for(int c:changed)assertTrue(c>20);
  var other=ImageIO.read(new ByteArrayInputStream(renderer.render(ProductDimensionImage.RING,Map.of("outerDiameter","6","innerDiameter","4","height","2.5"))));boolean different=false;
  for(int y=0;y<1024;y++)for(int x=0;x<1536;x++)if(image.getRGB(x,y)!=other.getRGB(x,y)){different=true;assertTrue(x>=1180&&x<1395&&y>=731&&y<840);}assertTrue(different);assertArrayEquals(bytes,renderer.render(ProductDimensionImage.RING,dimensions));
 }
 @Test void ringValidatesDimensionsAndFitsMaximumValues()throws Exception{
  var renderer=new ProductDimensionImage();for(var dims:java.util.List.of(Map.of("outerDiameter","6","height","1"),Map.of("outerDiameter","4","innerDiameter","4","height","1"),Map.of("outerDiameter","4","innerDiameter","5","height","1"),Map.of("outerDiameter","6","innerDiameter","4","height","0"),Map.of("outerDiameter","6","innerDiameter","4","height","bad")))assertThrows(com.ruoyi.common.exception.ServiceException.class,()->renderer.render(ProductDimensionImage.RING,dims));
  assertNotNull(ImageIO.read(new ByteArrayInputStream(renderer.render(ProductDimensionImage.RING,Map.of("outerDiameter","99999999999999.999999","innerDiameter","99999999999998.999999","height","99999999999999.999999")))));
 }
 @Test void ringAndHollowRodUseTheirOwnTemplateInExcel()throws Exception{
  var renderer=new ProductDimensionImage();var dims=Map.of("outerDiameter","6","innerDiameter","4","height","1.5");
  try(var book=new XSSFWorkbook();var output=new ByteArrayOutputStream()){
   var sheet=book.createSheet("报价单");sheet.createRow(5);sheet.createRow(6);
   QuotationImageWriter.embed(book,sheet,Map.of("category",ProductDimensionImage.RING,"dimensions",dims),5,renderer);
   QuotationImageWriter.embed(book,sheet,Map.of("category",ProductDimensionImage.HOLLOW_ROD,"dimensions",Map.of("outerDiameter","4","innerDiameter","2","length","30")),6,renderer);
   book.write(output);try(var saved=new XSSFWorkbook(new ByteArrayInputStream(output.toByteArray()))){var shapes=saved.getSheetAt(0).getDrawingPatriarch().getShapes();assertEquals(2,shapes.size());var first=(XSSFPicture)shapes.get(0);assertEquals(5,first.getClientAnchor().getRow1());assertArrayEquals(renderer.render(ProductDimensionImage.RING,dims),first.getPictureData().getData());assertEquals(6,((XSSFPicture)shapes.get(1)).getClientAnchor().getRow1());}
  }
 }

 @Test void flatBottomFillsAllFourFieldsWithoutChangingDrawing()throws Exception{
  var renderer=new ProductDimensionImage();var dims=Map.of("innerDiameter","10","innerHeight","8","wallThickness","0.2","bottomThickness","0.3");assertTrue(ProductDimensionImage.supports("铱坩埚平底"));
  var bytes=renderer.render(ProductDimensionImage.FLAT_BOTTOM,dims);var image=ImageIO.read(new ByteArrayInputStream(bytes));var source=ImageIO.read(getClass().getResourceAsStream("/templates/product-images/iridium-crucible-flat-bottom.png"));int[] ys={536,644,755,865},heights={54,54,54,53},changed=new int[4];
  for(int y=0;y<1024;y++)for(int x=0;x<1536;x++)if(image.getRGB(x,y)!=source.getRGB(x,y)){int at=-1;for(int i=0;i<4;i++)if(x>=1045&&x<1377&&y>=ys[i]&&y<ys[i]+heights[i])at=i;assertTrue(at>=0);changed[at]++;}for(int c:changed)assertTrue(c>20);
  var second=ImageIO.read(new ByteArrayInputStream(renderer.render(ProductDimensionImage.FLAT_BOTTOM,Map.of("innerDiameter","10","innerHeight","8","wallThickness","0.2","bottomThickness","0.5"))));boolean different=false;
  for(int y=0;y<1024;y++)for(int x=0;x<1536;x++)if(image.getRGB(x,y)!=second.getRGB(x,y)){different=true;assertTrue(x>=1045&&x<1377&&y>=865&&y<918);}assertTrue(different);assertArrayEquals(bytes,renderer.render(ProductDimensionImage.FLAT_BOTTOM,dims));
 }
 @Test void flatBottomRejectsInvalidDimensionsAndFitsMaximumPrecision()throws Exception{
  var renderer=new ProductDimensionImage();for(String key:java.util.List.of("innerDiameter","innerHeight","wallThickness","bottomThickness")){var d=new HashMap<>(Map.of("innerDiameter","10","innerHeight","8","wallThickness","0.2","bottomThickness","0.3"));d.remove(key);assertThrows(com.ruoyi.common.exception.ServiceException.class,()->renderer.render(ProductDimensionImage.FLAT_BOTTOM,d));d.put(key,"0");assertThrows(com.ruoyi.common.exception.ServiceException.class,()->renderer.render(ProductDimensionImage.FLAT_BOTTOM,d));}
  assertNotNull(ImageIO.read(new ByteArrayInputStream(renderer.render(ProductDimensionImage.FLAT_BOTTOM,Map.of("innerDiameter","99999999999999.999999","innerHeight","99999999999999.999999","wallThickness","99999999999999.999999","bottomThickness","99999999999999.999999")))));
 }
 @Test void flatAndCurvedCruciblesUseDistinctPicturesInCorrespondingRows()throws Exception{
  var renderer=new ProductDimensionImage();var dims=Map.of("innerDiameter","10","innerHeight","8","wallThickness","0.2","bottomThickness","0.3");
  try(var book=new XSSFWorkbook();var output=new ByteArrayOutputStream()){
   var sheet=book.createSheet("报价单");sheet.createRow(5);sheet.createRow(6);QuotationImageWriter.embed(book,sheet,Map.of("category",ProductDimensionImage.FLAT_BOTTOM,"dimensions",dims),5,renderer);QuotationImageWriter.embed(book,sheet,Map.of("category",ProductDimensionImage.CATEGORY,"dimensions",this.dims()),6,renderer);
   book.write(output);try(var saved=new XSSFWorkbook(new ByteArrayInputStream(output.toByteArray()))){var shapes=saved.getSheetAt(0).getDrawingPatriarch().getShapes();assertEquals(2,shapes.size());var first=(XSSFPicture)shapes.get(0);assertEquals(5,first.getClientAnchor().getRow1());assertArrayEquals(renderer.render(ProductDimensionImage.FLAT_BOTTOM,dims),first.getPictureData().getData());assertEquals(6,((XSSFPicture)shapes.get(1)).getClientAnchor().getRow1());}
  }
 }

 @Test void taperedFillsAllFourBoxesAndKeepsBottomThicknessIsolated()throws Exception{
  var renderer=new ProductDimensionImage();var dims=Map.of("innerDiameter","12","innerHeight","9","wallThickness","0.25","bottomThickness","0.4");assertTrue(ProductDimensionImage.supports("铱坩埚梯形"));
  var bytes=renderer.render(ProductDimensionImage.TAPERED,dims);var image=ImageIO.read(new ByteArrayInputStream(bytes));var source=ImageIO.read(getClass().getResourceAsStream("/templates/product-images/iridium-crucible-tapered.png"));int[] ys={550,654,760,867},heights={52,53,54,54},changed=new int[4];
  for(int y=0;y<1024;y++)for(int x=0;x<1536;x++)if(image.getRGB(x,y)!=source.getRGB(x,y)){int at=-1;for(int i=0;i<4;i++)if(x>=1103&&x<1394&&y>=ys[i]&&y<ys[i]+heights[i])at=i;assertTrue(at>=0);changed[at]++;}for(int c:changed)assertTrue(c>20);
  var other=ImageIO.read(new ByteArrayInputStream(renderer.render(ProductDimensionImage.TAPERED,Map.of("innerDiameter","12","innerHeight","9","wallThickness","0.25","bottomThickness","0.6"))));boolean different=false;
  for(int y=0;y<1024;y++)for(int x=0;x<1536;x++)if(image.getRGB(x,y)!=other.getRGB(x,y)){different=true;assertTrue(x>=1103&&x<1394&&y>=867&&y<921);}assertTrue(different);assertArrayEquals(bytes,renderer.render(ProductDimensionImage.TAPERED,dims));
 }
 @Test void taperedValidatesAllDimensionsAndFitsLongValues()throws Exception{
  var renderer=new ProductDimensionImage();for(String key:java.util.List.of("innerDiameter","innerHeight","wallThickness","bottomThickness")){var d=new HashMap<>(Map.of("innerDiameter","12","innerHeight","9","wallThickness","0.25","bottomThickness","0.4"));d.remove(key);assertThrows(com.ruoyi.common.exception.ServiceException.class,()->renderer.render(ProductDimensionImage.TAPERED,d));d.put(key,"-1");assertThrows(com.ruoyi.common.exception.ServiceException.class,()->renderer.render(ProductDimensionImage.TAPERED,d));}
  assertNotNull(ImageIO.read(new ByteArrayInputStream(renderer.render(ProductDimensionImage.TAPERED,Map.of("innerDiameter","99999999999999.999999","innerHeight","99999999999999.999999","wallThickness","99999999999999.999999","bottomThickness","99999999999999.999999")))));
 }
 @Test void taperedAndFlatBottomDoNotShareAnExcelPicture()throws Exception{
  var renderer=new ProductDimensionImage();var dims=Map.of("innerDiameter","12","innerHeight","9","wallThickness","0.25","bottomThickness","0.4");
  try(var book=new XSSFWorkbook();var output=new ByteArrayOutputStream()){
   var sheet=book.createSheet("报价单");sheet.createRow(5);sheet.createRow(6);QuotationImageWriter.embed(book,sheet,Map.of("category",ProductDimensionImage.TAPERED,"dimensions",dims),5,renderer);QuotationImageWriter.embed(book,sheet,Map.of("category",ProductDimensionImage.FLAT_BOTTOM,"dimensions",dims),6,renderer);
   book.write(output);try(var saved=new XSSFWorkbook(new ByteArrayInputStream(output.toByteArray()))){var shapes=saved.getSheetAt(0).getDrawingPatriarch().getShapes();assertEquals(2,shapes.size());var first=(XSSFPicture)shapes.get(0);var last=(XSSFPicture)shapes.get(1);assertEquals(5,first.getClientAnchor().getRow1());assertEquals(6,last.getClientAnchor().getRow1());assertArrayEquals(renderer.render(ProductDimensionImage.TAPERED,dims),first.getPictureData().getData());assertArrayEquals(renderer.render(ProductDimensionImage.FLAT_BOTTOM,dims),last.getPictureData().getData());assertFalse(java.util.Arrays.equals(first.getPictureData().getData(),last.getPictureData().getData()));}
  }
 }
}
