package com.ruoyi.metal.service;
import java.util.*;import java.time.*;import java.lang.reflect.*;
import com.ruoyi.metal.catalog.WorkOrderRequest;
import com.ruoyi.metal.mapper.WorkOrderMapper;
import com.ruoyi.metal.security.FactoryScope;
import com.ruoyi.common.exception.ServiceException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class WorkOrderCustomerSnapshotTest {
 private WorkOrderRequest request(Long customer,Integer version){return new WorkOrderRequest(4L,0,null,"客户端伪造",1L,2L,3L,LocalDate.of(2026,11,2),List.of(),customer,version,"MATERIAL");}
 private Map<String,Object> choose(JdbcTemplate jdbc,WorkOrderRequest input,Map<String,Object> old)throws Exception{var service=new WorkOrderService(jdbc,mock(WorkOrderMapper.class),mock(FactoryScope.class),mock(ProductService.class));var method=WorkOrderService.class.getDeclaredMethod("customer",WorkOrderRequest.class,Map.class);method.setAccessible(true);try{return (Map<String,Object>)method.invoke(service,input,old);}catch(InvocationTargetException e){throw (Exception)e.getCause();}}
 @Test void legacyFingerprintIsByteCompatible(){var r=new WorkOrderRequest(null,null,"uuid","客户",1L,2L,3L,LocalDate.of(2026,11,2),List.of());assertEquals("WorkOrderRequest[id=null, version=null, requestId=uuid, customer=客户, factoryId=1, productionLineId=2, ownerId=3, orderDate=2026-11-02, items=[]]",r.fingerprintInput());assertNotEquals(r.fingerprintInput(),request(5L,1).fingerprintInput());}
 @Test void keepsLegacyTextWithoutReadingCurrentDirectory()throws Exception{var jdbc=mock(JdbcTemplate.class);var old=new HashMap<String,Object>();old.put("factoryId","1");old.put("customer","历史客户");assertEquals(Map.of("name","历史客户"),choose(jdbc,request(null,null),old));verifyNoInteractions(jdbc);}
 @Test void sameCustomerKeepsSnapshotButChecksCurrentSelectionVersion()throws Exception{var jdbc=mock(JdbcTemplate.class);when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(Map.of("factory_id",1L,"version",2,"name","新名字")));var old=Map.<String,Object>of("customerId","5","customerSnapshot","{\"name\":\"旧名字\",\"version\":0}");assertEquals("旧名字",choose(jdbc,request(5L,2),old).get("name"));assertThrows(ServiceException.class,()->choose(jdbc,request(5L,1),old));}
 @Test void deletedOrForeignCustomerCannotBeChosen(){var jdbc=mock(JdbcTemplate.class);when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of());assertThrows(ServiceException.class,()->choose(jdbc,request(5L,0),null));when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(Map.of("factory_id",9L,"version",0)));assertThrows(ServiceException.class,()->choose(jdbc,request(5L,0),null));}
}
