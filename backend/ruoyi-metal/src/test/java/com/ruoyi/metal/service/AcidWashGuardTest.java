package com.ruoyi.metal.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.KeyHolder;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.metal.catalog.AcidWashRequests.*;
import com.ruoyi.metal.catalog.WarehouseRequests;
import com.ruoyi.metal.security.FactoryScope;

class AcidWashGuardTest {
 private WarehouseRequests.Line line(){return new WarehouseRequests.Line(null,null,"RAW",7L,null,new BigDecimal("99"),0L,null,null,null,null,null,null);}
 private void finish(long lockedMetal,String materialType,boolean reject){try(var security=mockStatic(SecurityUtils.class)){
  security.when(SecurityUtils::getUserId).thenReturn(7L);var jdbc=mock(JdbcTemplate.class);var scope=mock(FactoryScope.class);when(scope.isAdmin()).thenReturn(true);var warehouse=mock(WarehouseService.class);
  when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(c->{String q=c.getArgument(0);if(q.contains("finish_request_id"))return List.of();if(q.contains("FROM metal_acid_wash_batch"))return List.of(Map.of("factory_id",2L,"status","OPEN","version",0,"issued_weight",new BigDecimal("100")));if(q.contains("FROM metal_material"))return List.of(Map.of("metal_type_id",q.contains("FOR UPDATE")?lockedMetal:1L,"material_type",q.contains("FOR UPDATE")?materialType:"块料"));throw new AssertionError(q);});
  var service=new AcidWashService(jdbc,scope,warehouse);var request=new Finish(UUID.randomUUID().toString(),0,LocalDate.of(2026,11,3),List.of(7L),7L,true,"酸洗验收",List.of(new Group(3L,List.of(line()))),List.of());
  if(reject){assertThrows(ServiceException.class,()->service.finish(1L,request));verify(warehouse,never()).postAcidChild(any(),anyLong());}else{assertEquals("1",service.finish(1L,request));verify(warehouse).postAcidChild(any(),eq(1L));}
 }}
 @Test void outputMaterialChangedToOtherMetalBeforeLockMustBeRejected(){finish(2L,"块料",true);}
 @Test void outputMaterialChangedToPowderBeforeLockMustBeRejected(){finish(1L,"粉料",true);}
 @Test void validIridiumBlockOutputStillPosts(){finish(1L,"块料",false);}
 @Test void formalAcidInboundAlsoRejectsNonIridiumBeforeWriting()throws Exception{
  var jdbc=mock(JdbcTemplate.class);var scope=mock(FactoryScope.class);
  when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(c->{String q=c.getArgument(0);if(q.contains("metal_acid_wash_batch"))return List.of(Map.of("batch_no","ACID-TEST","material_ownership","FACTORY"));if(q.contains("metal_material"))return List.of(Map.of("code","RU-TEST","name","块料","material_type","块料","metal_type_id",2L,"specification","测试"));return List.of();});
  doAnswer(c->{throw new AssertionError("非铱酸洗产出已进入正式写入路径");}).when(jdbc).update(any(PreparedStatementCreator.class),any(KeyHolder.class));
  var service=new WarehouseService(jdbc,scope);var type=Class.forName("com.ruoyi.metal.service.WarehouseService$Type");var constructor=type.getDeclaredConstructors()[0];constructor.setAccessible(true);var method=Arrays.stream(WarehouseService.class.getDeclaredMethods()).filter(m->m.getName().equals("postLine")).findFirst().orElseThrow();method.setAccessible(true);
  var input=new WarehouseRequests.Order(UUID.randomUUID().toString(),2L,3L,"ACID_IN",LocalDate.of(2026,11,3),7L,List.of(7L),7L,null,null,null,null,null,null,false,null,null,List.of(line()),null,null);
  var error=assertThrows(InvocationTargetException.class,()->method.invoke(service,input,line(),constructor.newInstance("IN","RAW",1),1L,"ACID-TEST",1,null,null));assertInstanceOf(ServiceException.class,error.getCause());verify(jdbc,never()).update(any(PreparedStatementCreator.class),any(KeyHolder.class));
 }
}
