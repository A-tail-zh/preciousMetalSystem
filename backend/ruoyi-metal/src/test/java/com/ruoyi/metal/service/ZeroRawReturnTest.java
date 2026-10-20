package com.ruoyi.metal.service;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.metal.catalog.WarehouseRequests.Line;
import com.ruoyi.metal.catalog.WarehouseRequests.Order;
import com.ruoyi.metal.security.FactoryScope;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.KeyHolder;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ZeroRawReturnTest {
 JdbcTemplate jdbc; FactoryScope scope; WarehouseService warehouse;
 Map<String,Object> stock,origin;
 @BeforeEach void setup(){
  jdbc=mock(JdbcTemplate.class);scope=mock(FactoryScope.class);warehouse=new WarehouseService(jdbc,scope);
  stock=new HashMap<>(Map.of("inventory_id",9L,"factory_id",1L,"warehouse_id",2L,"category","RAW","net_weight",new BigDecimal("100"),"quantity",0L,"code","RAW","name","原料","specification","粉料","del_flag","0"));
  origin=new HashMap<>();origin.putAll(Map.of("source_type","PRODUCTION_OUT","source_status","POSTED","source_factory",1L,"source_batch",8L,"category","RAW","reference_id",3L,"inventory_id",9L,"batch_no","","net_weight",new BigDecimal("300"),"quantity",0L));origin.putAll(Map.of("type","PRODUCTION_OUT","status","POSTED","factory_id",1L,"batch_id",8L,"code","RAW","name","原料","organization","供应企业"));
  when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(a->{String sql=a.getArgument(0);
   if(sql.contains("FROM metal_warehouse WHERE"))return List.of(Map.of("factory_id",1L,"status","0"));
   if(sql.contains("FROM metal_work_order WHERE"))return List.of(Map.of("factory_id",1L,"status","IN_PROGRESS"));
   if(sql.contains("FROM metal_work_order_step"))return List.of(Map.of("sequence_no",1L,"product_id",6L,"work_order_id",7L));
   if(sql.startsWith("SELECT batch_id FROM metal_process_batch"))return List.of(Map.of("batch_id",8L));
   if(sql.contains("FROM metal_process_batch b LEFT"))return List.of(Map.of("issued_weight",new BigDecimal("300"),"output_weight",new BigDecimal("190"),"returned_weight",new BigDecimal("20"),"batch_status","OPEN"));
   if(sql.contains("SELECT COALESCE(SUM(l.net_weight)"))return List.of(Map.of("weight",new BigDecimal("20"),"quantity",0L));
   if(sql.contains("FROM metal_warehouse_order_line l JOIN"))return List.of(origin);
   if(sql.contains("FROM metal_inventory"))return List.of(stock);
   if(sql.contains("FROM metal_material"))return List.of(Map.of("code","RAW","name","原料","specification","粉料"));
   throw new IllegalStateException(sql);
  });
  doAnswer(a->{KeyHolder key=a.getArgument(1);key.getKeyList().add(new HashMap<>(Map.of("GENERATED_KEY",99L)));return 1;}).when(jdbc).update(any(PreparedStatementCreator.class),any(KeyHolder.class));
 }
 Line line(String weight){return new Line(null,10L,"RAW",3L,null,new BigDecimal(weight),0L,null,null,null,null,null,null);}
 void postLine(String type,Line line)throws Exception{
  var order=new Order(UUID.randomUUID().toString(),1L,2L,type,LocalDate.of(2026,10,20),1L,null,1L,null,null,null,7L,8L,0,false,null,null,List.of(line),4L,5L);
  Class<?> t=Class.forName("com.ruoyi.metal.service.WarehouseService$Type");var ctor=t.getDeclaredConstructors()[0];ctor.setAccessible(true);
  Method method=Arrays.stream(WarehouseService.class.getDeclaredMethods()).filter(m->m.getName().equals("postLine")).findFirst().orElseThrow();method.setAccessible(true);
  method.invoke(warehouse,order,line,ctor.newInstance("RET","RAW",1),11L,"RET-TEST",1,Map.of("batch_id",8L),Map.of());
 }
 WarehouseImportService.Input input(String type,Line line){return new WarehouseImportService.Input(1L,2L,type,7L,4L,5L,List.of(line),Map.of());}
 @Test void zeroReturnPostsAndLeavesBalanceUnchanged(){assertDoesNotThrow(()->postLine("RAW_RETURN",line("0.000000")));assertEquals(0,new BigDecimal("100").compareTo((BigDecimal)stock.get("net_weight")));assertEquals(0L,stock.get("quantity"));verify(jdbc).update(startsWith("UPDATE metal_inventory SET net_weight"),any(Object[].class));}
 @Test void zeroReturnImportKeepsSourceAndQuantity(){var rows=new WarehouseImportService(jdbc,scope).preview(input("RAW_RETURN",line("0.000000")));assertEquals("0.000000",rows.get(0).get("netWeight"));assertEquals("10",rows.get(0).get("sourceLineId"));assertEquals(0L,rows.get(0).get("quantity"));verify(jdbc,never()).update(any(PreparedStatementCreator.class),any(KeyHolder.class));}
 @Test void negativeRawReturnRejectedBeforeWrites(){var e=assertThrows(InvocationTargetException.class,()->postLine("RAW_RETURN",line("-0.000001")));assertInstanceOf(ServiceException.class,e.getCause());assertThrows(ServiceException.class,()->new WarehouseImportService(jdbc,scope).preview(input("RAW_RETURN",line("-0.000001"))));verify(jdbc,never()).update(any(PreparedStatementCreator.class),any(KeyHolder.class));}
 @Test void otherTypesStillRejectZeroBeforeWrites(){for(String type:List.of("RAW_IN","PRODUCTION_OUT","SEMI_IN","FINISHED_IN","SEMI_OUT","PRODUCT_RETURN")){var e=assertThrows(InvocationTargetException.class,()->postLine(type,line("0")));assertInstanceOf(ServiceException.class,e.getCause());}verify(jdbc,never()).update(any(PreparedStatementCreator.class),any(KeyHolder.class));}
}
