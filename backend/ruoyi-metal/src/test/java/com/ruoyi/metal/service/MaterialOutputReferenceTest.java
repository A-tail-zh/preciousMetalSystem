package com.ruoyi.metal.service;
import java.util.*;
import java.lang.reflect.InvocationTargetException;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.metal.security.FactoryScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class MaterialOutputReferenceTest {
 @ParameterizedTest
 @ValueSource(strings={"FROM metal_product_process", "FROM metal_storage_stock", "FROM metal_storage_document_line", "FROM metal_production_order_step"})
 void referencedMaterialsCannotBeDeleted(String table){
  var jdbc=mock(JdbcTemplate.class);
  when(jdbc.queryForList(contains(table),any(Object[].class))).thenReturn(List.of(Map.of("id",1L)));
  assertThrows(ServiceException.class,()->WarehouseReferences.material(jdbc,7L));
 }
 @Test void productReferenceExplainsHowToResolveIt(){
  var jdbc=mock(JdbcTemplate.class);
  when(jdbc.queryForList(contains("FROM metal_product_process"),any(Object[].class))).thenReturn(List.of(Map.of("id",1L)));
  assertTrue(assertThrows(ServiceException.class,()->WarehouseReferences.material(jdbc,7L)).getMessage().contains("先调整产品工序配置"));
 }
 @Test void unreferencedMaterialCanBeDeleted(){assertDoesNotThrow(()->WarehouseReferences.material(mock(JdbcTemplate.class),7L));}
 @Test void invalidOutputMaterialNamesTheStepAndMaterial()throws Exception{
  var jdbc=mock(JdbcTemplate.class);var service=new ProductionOrderService(jdbc,mock(FactoryScope.class));
  var route=List.<Map<String,Object>>of(Map.of("processName","酸洗","semanticCode","ACID_WASH","outputCategory","RAW","outputForm","METAL","measurementMode","WEIGHT","outputMaterialId","12965"),Map.of("processName","成型","semanticCode","NORMAL","outputCategory","PRODUCT","measurementMode","PIECE"));
  var method=ProductionOrderService.class.getDeclaredMethod("validateRoute",List.class,long.class);method.setAccessible(true);
  var exception=assertThrows(InvocationTargetException.class,()->method.invoke(service,route,13516L));var failure=assertInstanceOf(ServiceException.class,exception.getCause());
  assertTrue(failure.getMessage().contains("酸洗"));assertTrue(failure.getMessage().contains("12965"));assertTrue(failure.getMessage().contains("产品管理"));
  verify(jdbc).queryForList(contains("factory_id=? AND del_flag='0' FOR UPDATE"),eq(12965L),eq(13516L));
  when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(Map.of("measurement_mode","WEIGHT","material_form","METAL")));
  assertDoesNotThrow(()->method.invoke(service,route,13516L));
 }
}
