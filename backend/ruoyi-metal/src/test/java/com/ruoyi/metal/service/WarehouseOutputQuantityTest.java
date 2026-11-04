package com.ruoyi.metal.service;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
import java.util.*;import java.math.*;import java.time.*;import java.lang.reflect.*;import org.springframework.jdbc.core.*;import org.springframework.jdbc.support.*;import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;import com.ruoyi.metal.service.*;import com.ruoyi.metal.security.*;import com.ruoyi.metal.catalog.WarehouseRequests.*;
class WarehouseOutputQuantityTest {
 @Test void rejectsOverproductionBeforeStockAndLedgerWrites()throws Exception {
  JdbcTemplate j=mock(JdbcTemplate.class);FactoryScope scope=mock(FactoryScope.class);WarehouseService service=new WarehouseService(j,scope);
  Map<String,Object> stock=new HashMap<>();stock.put("inventory_id",99L);stock.put("factory_id",43L);stock.put("warehouse_id",1L);stock.put("category","FINISHED");stock.put("net_weight",BigDecimal.ZERO);stock.put("quantity",0L);stock.put("code","TEST");stock.put("name","test product");stock.put("specification","test");
  when(j.queryForList(anyString(),any(Object[].class))).thenAnswer(a->{String q=a.getArgument(0);if(q.contains("FROM metal_product"))return List.of(Map.of("code","TEST","name","test product","specification","test"));if(q.contains("FROM metal_inventory")&&q.contains("WHERE inventory_id=?"))return List.of(stock);if(q.contains("FROM metal_inventory"))return List.of();throw new IllegalStateException(q);});
  when(j.queryForObject(anyString(),eq(Long.class),any(Object[].class))).thenReturn(1L);
  doAnswer(a->{KeyHolder key=a.getArgument(1);key.getKeyList().add(new HashMap<>(Map.of("GENERATED_KEY",99L)));return 1;}).when(j).update(any(PreparedStatementCreator.class),any(KeyHolder.class));
  Line line=new Line(null,null,"FINISHED",7L,null,new BigDecimal("90"),2L,null,null,null,"piece",null,"isolated probe");
  Order order=new Order(UUID.randomUUID().toString(),43L,1L,"FINISHED_IN",LocalDate.now(),165L,null,165L,null,null,null,1L,20L,0,false,null,"isolated probe",List.of(line),5L,7L);
  Map<String,Object> batch=new HashMap<>(Map.of("batch_id",20L,"batch_no","PROBE","planned_quantity",1L,"sequence_no",1L,"item_id",5L));
  Class<?> type=Class.forName("com.ruoyi.metal.service.WarehouseService$Type");Constructor<?> ctor=type.getDeclaredConstructors()[0];ctor.setAccessible(true);
  Method method=Arrays.stream(WarehouseService.class.getDeclaredMethods()).filter(m->m.getName().equals("postLine")).findFirst().orElseThrow();method.setAccessible(true);
  var failure=assertThrows(InvocationTargetException.class,()->method.invoke(service,order,line,ctor.newInstance("IN","FINISHED",1),1L,"PROBE",1,batch,Map.of("product_id",7L)));
  assertInstanceOf(com.ruoyi.common.exception.ServiceException.class,failure.getCause());
  verify(j,never()).update(any(PreparedStatementCreator.class),any(KeyHolder.class));
  assertEquals(0L,stock.get("quantity"));
 }
 @Test void cumulativeWeightIncludesOutputsAndReturnsAndUsesExactDecimals(){
  assertDoesNotThrow(()->WarehouseService.validateProductionWeight(new BigDecimal("10"),new BigDecimal("10.000000")));
  assertThrows(com.ruoyi.common.exception.ServiceException.class,()->WarehouseService.validateProductionWeight(BigDecimal.ZERO,BigDecimal.ONE));
  assertThrows(com.ruoyi.common.exception.ServiceException.class,()->WarehouseService.validateProductionWeight(new BigDecimal("10"),new BigDecimal("10.000001")));
 }
 @Test void cumulativeQuantityAllowsPartialOutputsAndRejectsOverflow(){assertDoesNotThrow(()->WarehouseService.validateOutputQuantity(5,2,3));assertThrows(com.ruoyi.common.exception.ServiceException.class,()->WarehouseService.validateOutputQuantity(5,2,4));}
 @Test void downstreamCompletionRequiresActualSemiIssue() throws Exception {
  Method validate=WarehouseService.class.getDeclaredMethod("validateDownstreamQuantity",long.class,long.class,long.class);validate.setAccessible(true);
  assertDoesNotThrow(()->validate.invoke(null,1L,0L,10L));
  assertDoesNotThrow(()->validate.invoke(null,2L,6L,6L));
  assertDoesNotThrow(()->validate.invoke(null,2L,6L,4L));
  assertThrows(InvocationTargetException.class,()->validate.invoke(null,2L,0L,1L));
  assertThrows(InvocationTargetException.class,()->validate.invoke(null,2L,6L,7L));
 }
}