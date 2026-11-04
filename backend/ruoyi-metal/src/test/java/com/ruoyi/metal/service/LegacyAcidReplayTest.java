package com.ruoyi.metal.service;
import java.util.*;import java.time.LocalDate;import java.security.MessageDigest;import java.nio.charset.StandardCharsets;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.core.domain.model.LoginUser;import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.exception.ServiceException;import com.ruoyi.metal.catalog.AcidWashRequests.*;import com.ruoyi.metal.security.FactoryScope;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.security.core.context.SecurityContextHolder;import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.junit.jupiter.api.*;import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;
class LegacyAcidReplayTest {
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 @Test void historicalSuccessfulStartReplaysBeforeProcessAndNewBusinessValidation()throws Exception{
  var j=mock(JdbcTemplate.class);var scope=mock(FactoryScope.class);when(scope.isAdmin()).thenReturn(true);
  var login=new LoginUser(1L,null,new SysUser(1L),Set.of());SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(login,null,List.of()));
  var input=new Start(UUID.randomUUID().toString(),2L,99L,0,LocalDate.of(2026,11,3),List.of(3L),3L,null,List.of());
  String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JSON.toJSONString(input).getBytes(StandardCharsets.UTF_8)));
  when(j.queryForList(contains("WHERE request_id=?"),eq(input.requestId()))).thenReturn(List.of(Map.of("request_actor",1L,"request_hash",hash,"acid_wash_id",7L,"status","VOID")));
  var service=new AcidWashService(j,scope,new WarehouseService(j,scope));assertEquals("7",service.start(input));
  verify(j,times(1)).queryForList(anyString(),any(Object[].class));verify(j,never()).update(anyString(),any(Object[].class));
 }
 @Test void newStandaloneAcidRequestIsRetiredBeforeWriting(){
  var j=mock(JdbcTemplate.class);var scope=mock(FactoryScope.class);when(scope.isAdmin()).thenReturn(true);
  var input=new Start(UUID.randomUUID().toString(),2L,99L,0,LocalDate.of(2026,11,3),List.of(3L),3L,null,List.of());
  var error=assertThrows(ServiceException.class,()->new AcidWashService(j,scope,new WarehouseService(j,scope)).start(input));assertEquals(410,error.getCode());verify(j,never()).update(anyString(),any(Object[].class));
 }
}
