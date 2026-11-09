package com.ruoyi.metal.service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.metal.catalog.ListQuery;
import com.ruoyi.metal.mapper.MaterialMapper;
import com.ruoyi.metal.security.FactoryScope;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class MaterialFactoryListTest {
 @Test void missingOrForeignFactoryIsRejectedBeforeQuery(){
  var scope=mock(FactoryScope.class);var mapper=mock(MaterialMapper.class);var service=new MaterialService(mapper,scope,null);
  when(scope.resolveFactory(null)).thenThrow(new ServiceException("请先选择所属工厂",400));
  assertThrows(ServiceException.class,service::list);verifyNoInteractions(mapper);
  var q=new ListQuery();q.setFactoryId(8L);when(scope.resolveFactory(8L)).thenThrow(new ServiceException("不能查询其他工厂",403));
  assertEquals(403,assertThrows(ServiceException.class,()->service.list(q)).getCode());verifyNoInteractions(mapper);
 }
 @Test void ownFactoryIsAssignedAndMetalBelongsToThatFactory(){
  var scope=mock(FactoryScope.class);var mapper=mock(MaterialMapper.class);var service=new MaterialService(mapper,scope,null);
  when(scope.resolveFactory(null)).thenReturn(7L);when(scope.currentFactory()).thenReturn(7L);var q=new ListQuery();q.setMetalTypeId(2L);service.list(q);
  assertEquals(7L,q.getFactoryId());verify(scope).requireCatalog("metal",2L,7L);verify(mapper).listFiltered(7L,q);
 }
}
