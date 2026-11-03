package com.ruoyi.metal.service;

import java.lang.reflect.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.metal.catalog.*;
import com.ruoyi.metal.security.FactoryScope;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.KeyHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class WorkOrderRawSnapshotTest {
 private WorkOrderRequest.Plan plan(long process,BigDecimal loss){return new WorkOrderRequest.Plan(process,LocalDateTime.of(2026,11,3,11,0),LocalDateTime.of(2026,11,3,12,0),loss);}
 private WorkOrderRequest request(long material,long process,BigDecimal loss){return new WorkOrderRequest(1L,0,null,null,1L,2L,3L,LocalDate.of(2026,11,3),List.of(new WorkOrderRequest.Item(2L,null,0,List.of(plan(process,loss)),material,BigDecimal.TEN)),null,null,"DIRECT","RAW_PROCESSING");}
 private JdbcTemplate database(String currentType){
  var jdbc=mock(JdbcTemplate.class);
  when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(call->{String sql=call.getArgument(0);
   if(sql.startsWith("SELECT factory_id,production_line_name"))return List.of(Map.of("factory_id",1L,"name","产线"));
   if(sql.startsWith("SELECT material_id"))return List.of(Map.of("material_id",4L));
   if(sql.startsWith("SELECT product_id"))return List.of(Map.of("product_id",4L));
   if(sql.startsWith("SELECT process_id"))return List.of(Map.of("process_id",5L));
   if(sql.contains("FROM metal_material m"))return List.of(Map.of("code","M4","name","当前物料","metalTypeId","1"));
   if(sql.contains("JOIN metal_process_type"))return List.of(Map.of("processId",call.getArgument(1).toString(),"processName","当前名称","typeName",currentType));
   if(sql.startsWith("SELECT COALESCE(NULLIF(nick_name"))return List.of(Map.of("name","负责人"));
   if(sql.startsWith("SELECT metal_type_id"))return List.of(Map.of("metal_type_id",1L));
   if(sql.contains("FROM metal_customer"))return List.of(Map.of("customer_id",9L,"factory_id",1L,"name","客户","version",1));
   if(sql.contains("FROM metal_work_order WHERE request_id="))return List.of();
   throw new AssertionError(sql);
  });
  return jdbc;
 }
 @SuppressWarnings("unchecked")
 private List<Map<String,Object>> prepare(JdbcTemplate jdbc,WorkOrderRequest request,Map<String,Object> old)throws Exception{
  var service=new WorkOrderService(jdbc,null,mock(FactoryScope.class),null);
  var method=WorkOrderService.class.getDeclaredMethod("prepare",WorkOrderRequest.class,List.class,int.class,String.class);method.setAccessible(true);
  try{var prepared=(List<?>)method.invoke(service,request,List.of(old),2,"RAW_PROCESSING");var steps=prepared.get(0).getClass().getDeclaredMethod("steps");steps.setAccessible(true);return (List<Map<String,Object>>)steps.invoke(prepared.get(0));}
  catch(InvocationTargetException error){throw (Exception)error.getCause();}
 }
 private Map<String,Object> oldItem(Map<String,Object> step){return Map.of("id","2","materialId","4","snapshot",Map.of("name","原物料","metalTypeId","1"),"plans",List.of(step));}
 private Map<String,Object> oldStep(){return Map.of("processId","5","processName","原酸洗名称","processRole","ACID_WASH","outputStage","RAW","measurementMode","WEIGHT","lossUnit","PERCENT","loss","1.00");}
 @Test void sameRawRouteKeepsItsSnapshotAfterProcessTypeAndNameChange()throws Exception{
  var jdbc=database("融定");var old=oldStep();var step=prepare(jdbc,request(4,5,new BigDecimal("1.00")),oldItem(old)).get(0);
  assertEquals("原酸洗名称",step.get("processName"));assertEquals("ACID_WASH",step.get("processRole"));assertEquals("RAW",step.get("outputStage"));assertEquals("WEIGHT",step.get("measurementMode"));assertEquals("PERCENT",step.get("lossUnit"));assertEquals("1.00",step.get("loss"));assertEquals("1.00",old.get("loss"));
  verify(jdbc,never()).queryForList(contains("JOIN metal_process_type"),any(Object[].class));
 }
 @Test void retainedRawLossCannotBeRewritten(){assertThrows(ServiceException.class,()->prepare(database("酸洗"),request(4,5,new BigDecimal("2.50")),oldItem(oldStep())));}
 @Test void omittedLossOnRetainedRawRouteKeepsPreviousLoss()throws Exception{
  assertEquals("1.00",prepare(database("融定"),request(4,5,null),oldItem(oldStep())).get(0).get("loss"));
 }
 @Test void changedMaterialOrProcessUsesCurrentRouteRules(){
  assertThrows(ServiceException.class,()->prepare(database("融定"),request(6,5,BigDecimal.ONE),oldItem(oldStep())));
  assertThrows(ServiceException.class,()->prepare(database("融定"),request(4,6,BigDecimal.ONE),oldItem(oldStep())));
 }
 @Test void rawCreationNeverSchedulesSemiReleaseWhileProductionHonorsTheSwitch()throws Exception{
  assertFalse(createEligibility(true,true));assertTrue(createEligibility(false,true));assertFalse(createEligibility(false,false));
 }
 private boolean createEligibility(boolean raw,boolean enabled)throws Exception{
  var jdbc=database(raw?"酸洗":"融定");var products=mock(ProductService.class);
  var product=new ProductRecord();product.setCode("P4");product.setName("产品");product.setCategory("铱杆实心");product.setDimensions(Map.of("diameter","1"));product.setMetalTypeId(1L);
  var process=new ProductStep();process.setProcessId(5L);process.setProcessName("融定");process.setLoss(BigDecimal.ONE);product.setProcesses(List.of(process));when(products.detail(4L)).thenReturn(product);
  when(jdbc.queryForMap(anyString(),any(Object[].class))).thenReturn(Map.of("actor_id",7L,"order_no","WO1"));
  when(jdbc.queryForObject(anyString(),eq(String.class),any(Object[].class))).thenReturn("工厂");
  when(jdbc.update(any(PreparedStatementCreator.class),any(KeyHolder.class))).thenAnswer(call->{call.<KeyHolder>getArgument(1).getKeyList().add(new HashMap<>(Map.of("GENERATED_KEY",100L)));return 1;});
  var eligible=new AtomicReference<Boolean>();
  doAnswer(call->{eligible.set(call.getArgument(1));assertEquals(Integer.valueOf(3),call.<Integer>getArgument(7));return 1;}).when(jdbc).update(startsWith("UPDATE metal_work_order SET auto_semi_release_eligible="),any(Object[].class));
  var service=new WorkOrderService(jdbc,null,mock(FactoryScope.class),products);var auto=WorkOrderService.class.getDeclaredField("autoSemiReleaseEnabled");auto.setAccessible(true);auto.set(service,enabled);
  var item=raw?new WorkOrderRequest.Item(null,null,0,List.of(plan(5,BigDecimal.ONE)),4L,BigDecimal.TEN):new WorkOrderRequest.Item(null,4L,1,List.of(plan(5,null)));
  var input=new WorkOrderRequest(null,null,UUID.randomUUID().toString(),null,1L,2L,3L,LocalDate.of(2026,11,3),List.of(item),raw?null:9L,raw?null:1,"DIRECT",raw?"RAW_PROCESSING":"PRODUCTION");
  try(var security=mockStatic(SecurityUtils.class)){
   security.when(SecurityUtils::getUserId).thenReturn(7L);security.when(SecurityUtils::getUsername).thenReturn("planner");
   var user=new SysUser();user.setNickName("计划员");var login=new LoginUser();login.setUser(user);security.when(SecurityUtils::getLoginUser).thenReturn(login);
   service.create(input);
  }
  return Objects.requireNonNull(eligible.get());
 }
}
