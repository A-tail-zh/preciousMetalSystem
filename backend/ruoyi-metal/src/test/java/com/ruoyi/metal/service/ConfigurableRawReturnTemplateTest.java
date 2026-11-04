package com.ruoyi.metal.service;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ruoyi.metal.security.FactoryScope;
import com.ruoyi.common.exception.ServiceException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class ConfigurableRawReturnTemplateTest {
 private WarehouseImportService service(int marker,String sourceMode,long factory,boolean hasSource){
  var jdbc=mock(JdbcTemplate.class);
  when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(call->{
   String q=call.getArgument(0);
   if(q.startsWith("SELECT b.*,"))return List.of(Map.of("batch_id",19L,"factory_id",factory,"product_id",4L,"measurement_mode","WEIGHT","product_snapshot","{\"measurementPolicyVersion\":3}"));
   if(q.startsWith("SELECT l.*"))return hasSource?List.of(new HashMap<>(Map.of("line_id",1L,"inventory_id",2L,"category","RAW","quantity",5L))):List.of();
   if(q.startsWith("SELECT inventory_id,origin_batch_id"))return List.of(Map.of("inventory_id",2L,"origin_batch_id",3L));
   if(q.startsWith("SELECT b.batch_id,"))return List.of(Map.of("batch_id",3L,"measurement_mode",sourceMode,"product_snapshot","{\"measurementPolicyVersion\":"+marker+"}"));
   return List.of();
  });
  return new WarehouseImportService(jdbc,mock(FactoryScope.class));
 }
 @Test void returnTemplateUsesActualIssuedSourceModeRatherThanTargetOutputMode(){
  var columns=service(3,"PIECE",7L,true).templateColumns("RAW_RETURN",7L,19L);
  assertTrue(columns.stream().anyMatch(c->c.key().equals("quantity")));
  assertTrue(WarehouseTemplateLayout.identify(columns.stream().map(WarehouseTemplateLayout.Column::label).toList(),"RAW_RETURN").containsKey("quantity"));
 }
 @Test void legacyWeightAndUnboundRawReturnsKeepTheirOriginalColumns(){
  for(var service:List.of(service(2,"PIECE",7L,true),service(3,"WEIGHT",7L,true),service(3,"PIECE",7L,false)))assertEquals(WarehouseTemplateLayout.raw("RAW_RETURN"),service.templateColumns("RAW_RETURN",7L,19L));
  assertEquals(WarehouseTemplateLayout.raw("RAW_RETURN"),service(3,"PIECE",7L,true).templateColumns("RAW_RETURN",7L,null));
 }
 @Test void aForeignFactoryCannotRevealBatchSourceMeasurement(){
  assertEquals(403,assertThrows(ServiceException.class,()->service(3,"PIECE",8L,true).templateColumns("RAW_RETURN",7L,19L)).getCode());
 }
}
