package com.ruoyi.metal.service;

import java.util.*;
import java.io.*;
import javax.imageio.ImageIO;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.metal.catalog.*;
import com.ruoyi.metal.security.FactoryScope;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class QuotationDocumentTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final AtomicReference<String> inserted=new AtomicReference<>();
    private QuoteRuleService rules(){return new QuoteRuleService(null,FactoryCatalogTestFixtures.scope()){
        @Override public String metalName(Long id){return "铱";}
        @Override public Map<String,Object> rule(Long id){return Map.of("density","22.65","powderPrice","2.00","nonPowderPrice","3.00","shortagePrice","4.00","lossPrice","7.00","version",3,"updateTime",UUID.randomUUID().toString());}
        @Override public Map<String,Object> volumeRuleForMetal(Long id){return Map.of("piValue","3.14","curvedBottomFactor","1.03","version",2,"updateTime",UUID.randomUUID().toString());}
    };}
    private DashboardService service(){
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of());
        when(jdbc.queryForObject(contains("sequence_value"),eq(Integer.class),any(Object[].class))).thenReturn(0);
        when(jdbc.queryForObject(startsWith("SELECT quote_id"),eq(Long.class),any(Object[].class))).thenReturn(7L);
        when(jdbc.queryForObject(startsWith("SELECT factory_name"),eq(String.class),any(Object[].class))).thenReturn("测试工厂");
        doAnswer(i->{if(i.getArgument(0).toString().startsWith("INSERT INTO metal_saved_quotation"))inserted.set(i.getArgument(7));return 1;}).when(jdbc).update(anyString(),any(Object[].class));
        return new DashboardService(jdbc,FactoryCatalogTestFixtures.scope(),rules()){@Override public Map<String,Object> quotation(Long id){return new LinkedHashMap<>(Map.of("id",id.toString(),"totalPrice","42.00"));}};
    }
    private CalculationRequest item(String powder){return new CalculationRequest(null,Map.of("innerDiameter","0","outerDiameter","2","length","1"),2,1L,"杆","MATERIAL_CUSTOM",powder,"5","1");}
    private QuotationSaveRequest request(CalculationRequest... items){return new QuotationSaveRequest(UUID.randomUUID().toString(),1L,List.of(items));}
    @Test void saveFreezesCalculatedInputsPricesVersionsAndImageDigest(){
        var service=service();try(var security=mockStatic(SecurityUtils.class)){security.when(SecurityUtils::getUserId).thenReturn(1L);assertEquals(false,service.saveQuotation(request(item("10"))).get("reused"));}
        var frozen=com.alibaba.fastjson2.JSON.parseArray(inserted.get()).getJSONObject(0);
        assertEquals("42.00",frozen.getString("totalPrice"));assertEquals("10.00",frozen.getString("powderWeight"));assertEquals("7.00",frozen.getJSONObject("rule").getString("lossPrice"));assertEquals(2,frozen.getJSONObject("volumeRule").getIntValue("version"));assertEquals(64,frozen.getString("imageHash").length());assertFalse(frozen.containsKey("image_png"));
        verify(jdbc).update(startsWith("INSERT INTO metal_quotation_image"),any(Object[].class));verify(jdbc).update(startsWith("INSERT INTO metal_quotation_request"),any(Object[].class));
    }
    @Test void normalizedInputsAndMapOrderingKeepLongLivedContentHash(){
        var service=service();try(var security=mockStatic(SecurityUtils.class)){security.when(SecurityUtils::getUserId).thenReturn(1L);service.saveQuotation(request(item("10")));service.saveQuotation(request(item("10.000000")));}
        var hashes=mockingDetails(jdbc).getInvocations().stream().filter(i->i.getMethod().getName().equals("update")&&i.getArgument(0).toString().startsWith("INSERT INTO metal_saved_quotation")).map(i->i.getArgument(10).toString()).toList();
        assertEquals(2,hashes.size());assertEquals(hashes.get(0),hashes.get(1));
    }
    @Test void existingContentOnlyAddsRequestAlias(){
        var service=service();when(jdbc.queryForList(startsWith("SELECT quote_id FROM metal_saved_quotation WHERE factory_id"),any(Object[].class))).thenReturn(List.of(Map.of("quote_id",7L)));
        try(var security=mockStatic(SecurityUtils.class)){security.when(SecurityUtils::getUserId).thenReturn(1L);assertEquals(true,service.saveQuotation(request(item("10"))).get("reused"));}
        assertNull(inserted.get());verify(jdbc,never()).update(startsWith("INSERT INTO metal_quotation_image"),any(Object[].class));verify(jdbc).update(startsWith("INSERT INTO metal_quotation_request"),any(Object[].class));
    }
    @Test void invalidSecondItemWritesNoDocumentImagesOrSequence(){
        var service=service();var bad=new CalculationRequest(null,Map.of("innerDiameter","0","outerDiameter","2","length","1"),2,1L,"杆","BAD","10","5","1");
        try(var security=mockStatic(SecurityUtils.class)){security.when(SecurityUtils::getUserId).thenReturn(1L);assertThrows(RuntimeException.class,()->service.saveQuotation(request(item("10"),bad)));}
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void allEightDrawingsAreReadablePngsIncludingZeroRodAndAngle()throws Exception{
        for(String type:ProductTypes.ALL){Map<String,String> dimensions=new LinkedHashMap<>();for(String key:ProductGeometry.FIELDS.get(type))dimensions.put(key,"2.00");if(type.equals("杆"))dimensions.put("innerDiameter","0.00");if(type.equals("梯形"))dimensions.put("angle","0.00");
            byte[] bytes=QuotationDiagram.render(Map.of("productType",type,"metalTypeName","铂","dimensions",dimensions));var image=ImageIO.read(new ByteArrayInputStream(bytes));assertEquals(1200,image.getWidth());assertEquals(800,image.getHeight());long ink=0;for(int y=150;y<650;y+=4)for(int x=50;x<580;x+=4)if((image.getRGB(x,y)&0xffffff)!=0xffffff)ink++;assertTrue(ink>100,type);
        }
    }
    @Test void workbookUsesFrozenPricesDimensionsAndEmbeddedPng()throws Exception{
        var input=item("10");var calculated=new LinkedHashMap<>(rules().calculate(input));calculated.put("dimensionFields",List.of(Map.of("key","innerDiameter","label","内径","unit","cm")));
        byte[] png=QuotationDiagram.render(calculated);var quote=Map.<String,Object>of("id","7","quoteNo","QT-TEST","factoryName","冻结工厂","createTime","2026-11-02 10:00:00","totalPrice","42.00","items",List.of(calculated));
        try(var book=new XSSFWorkbook()){QuotationWorkbook.write(book,quote,index->{assertEquals(1,index);return png;});assertEquals(1,book.getAllPictures().size());String text=book.getSheetAt(0).toString();var values=new StringBuilder();for(var row:book.getSheetAt(0))for(var cell:row)values.append(cell.getStringCellValue()).append('\n');assertTrue(values.toString().contains("商品金额（元）：42.00"));for(String hidden:List.of("单件体积","密度","粉料单价","非粉料单价","欠料单价","理论损失单价","π：","弧底系数","粉料加工费","非粉料加工费","欠料产品费用"))assertFalse(values.toString().contains(hidden),hidden);assertTrue(values.toString().contains("单件理论重量"));assertTrue(values.toString().contains("损失费用"));assertEquals("7.00",((Map<?,?>)calculated.get("rule")).get("lossPrice"));assertTrue(values.toString().contains("冻结工厂"));assertFalse(values.toString().contains("金属规则版本"));assertFalse(values.toString().contains("公共参数版本"));}
    }
}
