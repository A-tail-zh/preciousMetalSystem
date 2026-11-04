package com.ruoyi.metal.service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.metal.catalog.ListQuery;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ProcessTypeServiceTest {
 @Test void namesAreTrimmedAndBlankRejected(){assertEquals("打磨",ProcessTypeService.validName("  打磨  "));assertThrows(ServiceException.class,()->ProcessTypeService.validName("　 "));assertThrows(ServiceException.class,()->ProcessTypeService.validName(null));}
 @Test void namesRespectLength(){assertEquals(100,ProcessTypeService.validName("磨".repeat(100)).length());assertThrows(ServiceException.class,()->ProcessTypeService.validName("磨".repeat(101)));}
 @Test void customTypeAboveNineCanFilter(){var q=new ListQuery();q.setProcessType(10);assertDoesNotThrow(q::validate);q.setProcessType(0);assertThrows(ServiceException.class,q::validate);}
 @Test void existingCustomIdIsAccepted(){var j=mock(JdbcTemplate.class);when(j.queryForObject(anyString(),eq(Integer.class),eq(10))).thenReturn(1);assertDoesNotThrow(()->ProcessTypeService.requireType(j,10));}
 @Test void nonexistentIdIsRejected(){var j=mock(JdbcTemplate.class);assertThrows(ServiceException.class,()->ProcessTypeService.requireType(j,123));assertThrows(ServiceException.class,()->ProcessTypeService.requireType(j,null));}
 @Test void duplicateNameHasChineseError(){var j=mock(JdbcTemplate.class);when(j.update(any(org.springframework.jdbc.core.PreparedStatementCreator.class),any(org.springframework.jdbc.support.KeyHolder.class))).thenThrow(new DuplicateKeyException("internal SQL"));assertEquals("工序类型已存在，请勿重复添加",assertThrows(ServiceException.class,()->new ProcessTypeService(j,FactoryCatalogTestFixtures.scope()).create("打磨","tester")).getMessage());}
}
