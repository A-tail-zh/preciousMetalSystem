package com.ruoyi.metal.service;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.*;import java.math.*;import java.time.*;import java.lang.reflect.*;
import org.springframework.jdbc.core.*;import org.springframework.jdbc.support.*;
import com.ruoyi.metal.security.FactoryScope;
import com.ruoyi.metal.catalog.WarehouseRequests.*;
class WarehouseArchivedStockTest {
 private void post(BigDecimal oldWeight,boolean referenced)throws Exception{
  var j=mock(JdbcTemplate.class);var scope=mock(FactoryScope.class);var service=new WarehouseService(j,scope);
  var stock=new HashMap<String,Object>(Map.of("inventory_id",99L,"del_flag","2","net_weight",oldWeight,"quantity",0L,"category","RAW","code","TEST","name","fixture"));
  when(j.queryForList(anyString(),any(Object[].class))).thenAnswer(a->{String q=a.getArgument(0);if(q.contains("FROM metal_material"))return List.of(Map.of("code","TEST","name","fixture"));if(q.contains("FROM metal_inventory"))return List.of(stock);if(q.contains("FROM metal_warehouse_order_line"))return referenced?List.of(Map.of("line_id",1L)):List.of();throw new IllegalStateException(q);});
  doAnswer(a->{KeyHolder key=a.getArgument(1);key.getKeyList().add(new HashMap<>(Map.of("GENERATED_KEY",1L)));return 1;}).when(j).update(any(PreparedStatementCreator.class),any(KeyHolder.class));
  Line line=new Line(null,null,"RAW",7L,null,new BigDecimal("10"),0L,null,null,null,null,null,null);
  Order input=new Order(UUID.randomUUID().toString(),1L,1L,"RAW_IN",LocalDate.now(),1L,List.of(1L),1L,null,null,null,null,null,null,false,null,null,List.of(line),null,null);
  Class<?> type=Class.forName("com.ruoyi.metal.service.WarehouseService$Type");var ctor=type.getDeclaredConstructors()[0];ctor.setAccessible(true);
  var method=Arrays.stream(WarehouseService.class.getDeclaredMethods()).filter(m->m.getName().equals("postLine")).findFirst().orElseThrow();method.setAccessible(true);
  if(oldWeight.signum()!=0||referenced){var failure=assertThrows(InvocationTargetException.class,()->method.invoke(service,input,line,ctor.newInstance("IN","RAW",1),1L,"TEST",1,null,null));assertInstanceOf(com.ruoyi.common.exception.ServiceException.class,failure.getCause());verify(j,never()).update(contains("SET del_flag='0'"),any(Object[].class));return;}
  method.invoke(service,input,line,ctor.newInstance("IN","RAW",1),1L,"TEST",1,null,null);
  assertEquals("0",stock.get("del_flag"));assertEquals(new BigDecimal("10"),stock.get("net_weight"));assertEquals(99L,stock.get("inventory_id"));
  verify(j).update(contains("SET del_flag='0'"),any(Object[].class));verify(j,times(2)).update(any(PreparedStatementCreator.class),any(KeyHolder.class));
 }
 @Test void reentryRestoresSameStockAndWritesLedger()throws Exception{post(BigDecimal.ZERO,false);}
 @Test void nonzeroArchivedStockCannotBeRestored()throws Exception{post(BigDecimal.ONE,false);}
 @Test void referencedArchivedStockCannotBeRestored()throws Exception{post(BigDecimal.ZERO,true);}
}
