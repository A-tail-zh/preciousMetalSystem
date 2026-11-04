package com.ruoyi.metal.service;
import java.util.*;
import java.math.BigDecimal;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.metal.catalog.*;
import com.ruoyi.metal.mapper.ProductMapper;
import com.ruoyi.metal.security.FactoryScope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ProductSpecificationTest {
 private ProductRecord row(String category) {
  var row=new ProductRecord();row.setFactoryId(7L);row.setCategory(category);var dimensions=new HashMap<String,String>();
  for(String key:ProductService.DIMENSIONS.get(category))dimensions.put(key,"0.2");
  if(dimensions.containsKey("outerDiameter"))dimensions.put("outerDiameter","10");
  row.setDimensions(dimensions);return row;
 }
 private ProductService service(ProductMapper mapper) {
  var rules=mock(QuoteRuleService.class);var rule=new QuoteRule();rule.setPiValue(new BigDecimal("3.141593"));when(rules.get()).thenReturn(rule);when(rules.metalName(any())).thenReturn("铱");when(rules.pi(any())).thenReturn(rule.getPiValue());when(rules.volumeRuleForMetal(any())).thenReturn(Map.of("piValue","3.14","curvedBottomFactor","1.03"));
  return new ProductService(mapper,FactoryCatalogTestFixtures.scope(),mock(JdbcTemplate.class),rules);
 }
 @Test void allCategoriesIncludeEveryDimensionInFixedOrderWithinStorageLimit() {
  var expected=Map.of("铱坩埚弧底","内径0.2cm；内高0.2cm；壁厚0.2cm；底厚0.2cm；R高度0.2cm",
   "铱坩埚平底","内径0.2cm；内高0.2cm；壁厚0.2cm；底厚0.2cm",
   "铱坩埚梯形","内径0.2cm；内高0.2cm；壁厚0.2cm；底厚0.2cm",
   "铱环","外径10cm；内径0.2cm；高度0.2cm","铱杆实心","直径0.2cm；长度0.2cm",
   "铱杆空心","外径10cm；内径0.2cm；长度0.2cm");
  expected.forEach((category,text)->{var row=row(category);assertEquals(text,ProductService.specification(row));row.getDimensions().replaceAll((k,v)->"99999999999999.999999");assertTrue(ProductService.specification(row).length()<=200);});
 }
 @Test void createAndEditAlwaysReplaceCallerSpecification() {
  var service=service(mock(ProductMapper.class));var row=new ProductRecord();row.setFactoryId(7L);row.setMetalTypeId(1L);row.setProductType("平底坩埚");row.setDimensions(ProductGeometryTest.FLAT);row.setSpecification("arbitrary input");service.prepare(row,null);
  assertEquals("内径2cm；外径4cm；壁厚0.5cm；内高3cm；底厚1cm",row.getSpecification());
  var old=new ProductRecord();old.setFactoryId(7L);old.setCalculationVersion(2);old.setCategory(row.getCategory());old.setDimensions(row.getDimensions());
  row.setProductType("杆");row.setCategory("铱杆");row.setDimensions(Map.of("innerDiameter","0","outerDiameter","2.000000","length","10.50"));row.setSpecification("old text");service.prepare(row,old);
  assertEquals("内径0cm；外径2cm；长度10.5cm",row.getSpecification());
 }
 @Test void historicalDetailDisplaysDimensionsInsteadOfLegacyFreeText() {
  var mapper=mock(ProductMapper.class);var row=row("铱环");row.setId(7L);row.setSpecification("legacy text");when(mapper.detail(7L)).thenReturn(row);
  assertEquals("外径10cm；内径0.2cm；高度0.2cm",service(mapper).detail(7L).getSpecification());verify(mapper,never()).update(any());
 }
 @Test void jsonIgnoresInputSpecificationButIncludesGeneratedOutput() throws Exception {
  var json=new ObjectMapper();var row=json.readValue("{\"specification\":\"untrusted input\"}",ProductRecord.class);assertNull(row.getSpecification());
  row.setSpecification("直径2cm；长度10.5cm");assertEquals(row.getSpecification(),json.readTree(json.writeValueAsString(row)).get("specification").asText());
 }
}
