package com.ruoyi.metal.service;
import java.util.*;import java.math.*;import java.time.*;import java.lang.reflect.*;import java.sql.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.*;import org.springframework.jdbc.support.*;
import com.ruoyi.metal.security.FactoryScope;import com.ruoyi.metal.catalog.WarehouseRequests.*;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;
class WarehouseOriginTest {
 private record Fixture(WarehouseService service,JdbcTemplate jdbc,List<String> inserts,List<List<Object>> values,Map<String,Object> stock){}
 private Fixture fixture(boolean destinationExists,Long destinationOrigin,boolean returning)throws Exception{
  var j=mock(JdbcTemplate.class);var scope=mock(FactoryScope.class);var stock=new HashMap<String,Object>(Map.of("inventory_id",99L,"factory_id",1L,"warehouse_id",2L,"category","SEMI","reference_id",7L,"batch_no","PRODUCTION_LOT","net_weight",new BigDecimal("10"),"quantity",2L,"del_flag","0"));stock.put("origin_batch_id",destinationOrigin);stock.put("code","FIXTURE");stock.put("name","fixture");
  var source=new HashMap<String,Object>(Map.of("inventory_id",77L,"source_type","PRODUCT_OUT","source_status","POSTED","source_factory",1L,"category","FINISHED","reference_id",7L,"batch_no","PRODUCTION_LOT","net_weight",new BigDecimal("10"),"quantity",2L));
  when(j.queryForList(anyString(),any(Object[].class))).thenAnswer(a->{String q=a.getArgument(0);if(q.startsWith("SELECT l.*,o.type"))return List.of(source);if(q.startsWith("SELECT COALESCE(SUM"))return List.of(Map.of("weight",BigDecimal.ZERO,"quantity",0L));if(q.startsWith("SELECT origin_batch_id"))return List.of(Map.of("origin_batch_id",42L));if(q.contains("FROM metal_product"))return List.of(Map.of("code","FIXTURE","name","fixture"));if(q.contains("FROM metal_inventory")&&q.contains("inventory_id=?"))return List.of(stock);if(q.contains("FROM metal_inventory"))return destinationExists?List.of(stock):List.of();throw new IllegalStateException(q);});
  List<String> inserts=new ArrayList<>();List<List<Object>> values=new ArrayList<>();
  doAnswer(a->{var creator=(PreparedStatementCreator)a.getArgument(0);var connection=mock(Connection.class);when(connection.prepareStatement(anyString(),anyInt())).thenAnswer(call->{inserts.add(call.getArgument(0));List<Object> row=new ArrayList<>();values.add(row);var statement=mock(PreparedStatement.class);doAnswer(set->{int at=set.getArgument(0);while(row.size()<at)row.add(null);row.set(at-1,set.getArgument(1));return null;}).when(statement).setObject(anyInt(),any());return statement;});creator.createPreparedStatement(connection);KeyHolder key=a.getArgument(1);key.getKeyList().add(new HashMap<>(Map.of("GENERATED_KEY",99L)));return 1;}).when(j).update(any(PreparedStatementCreator.class),any(KeyHolder.class));
  return new Fixture(new WarehouseService(j,scope),j,inserts,values,stock);
 }
 private void post(Fixture f,String type,boolean returning)throws Exception{
  String category=returning?"FINISHED":"SEMI";f.stock.put("category",category);
  var line=new Line(null,returning?5L:null,category,7L,"PRODUCTION_LOT",BigDecimal.ONE,1L,null,null,null,null,null,null);
  var input=new Order(UUID.randomUUID().toString(),1L,2L,type,LocalDate.now(),1L,List.of(1L),1L,null,null,null,null,null,null,false,null,null,List.of(line),null,null);
  var clazz=Class.forName("com.ruoyi.metal.service.WarehouseService$Type");var ctor=clazz.getDeclaredConstructors()[0];ctor.setAccessible(true);var method=Arrays.stream(WarehouseService.class.getDeclaredMethods()).filter(m->m.getName().equals("postLine")).findFirst().orElseThrow();method.setAccessible(true);
  method.invoke(f.service,input,line,ctor.newInstance(returning?"RET":"IN",category,1),1L,"FIXTURE",1,null,null);
 }
 @Test void externalReceiptCannotMergeIntoProductionOrigin()throws Exception{
  var f=fixture(true,42L,false);var failure=assertThrows(InvocationTargetException.class,()->post(f,"SEMI_IN",false));assertInstanceOf(com.ruoyi.common.exception.ServiceException.class,failure.getCause());assertTrue(f.inserts.isEmpty());
 }
 @Test void crossWarehouseProductReturnRetainsOriginalProductionOrigin()throws Exception{
  var f=fixture(false,null,true);post(f,"PRODUCT_RETURN",true);int index=f.inserts.indexOf(f.inserts.stream().filter(q->q.startsWith("INSERT INTO metal_inventory")).findFirst().orElseThrow());assertEquals(42L,f.values.get(index).get(10));
 }
 @Test void returnCanMergeIntoMatchingProductionOrigin()throws Exception{
  var f=fixture(true,42L,true);post(f,"PRODUCT_RETURN",true);assertEquals(new BigDecimal("11"),f.stock.get("net_weight"));assertEquals(3L,f.stock.get("quantity"));
 }
}
