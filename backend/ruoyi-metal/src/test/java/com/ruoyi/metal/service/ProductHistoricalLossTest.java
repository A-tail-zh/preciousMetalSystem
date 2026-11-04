package com.ruoyi.metal.service;
import java.util.*;
import java.math.BigDecimal;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.*;
import com.ruoyi.metal.catalog.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ProductHistoricalLossTest {
 @Test void lockedProductWithoutHydratedRoutePreservesUnchangedLoss()throws Exception {
  var jdbc=mock(JdbcTemplate.class);var rules=mock(QuoteRuleService.class);when(rules.metalName(1L)).thenReturn("铱");when(rules.pi(1L)).thenReturn(new BigDecimal("3.14"));when(rules.rule(1L)).thenReturn(Map.of("density","22.65"));
  when(jdbc.queryForList(eq("SELECT process_id FROM metal_process WHERE process_id=? AND del_flag='0' FOR UPDATE"),any(Object[].class))).thenReturn(List.of(Map.of("process_id",9L)));
  when(jdbc.queryForList(startsWith("SELECT p.process_id,t.type_name"),any(Object[].class))).thenReturn(List.of(Map.of("process_id",9L,"type_name","融定")));
  doAnswer(call->{var row=mock(ResultSet.class);when(row.getLong("process_id")).thenReturn(9L);when(row.getBigDecimal("theoretical_loss")).thenReturn(new BigDecimal("0.123456"));call.<RowCallbackHandler>getArgument(1).processRow(row);return null;}).when(jdbc).query(anyString(),any(RowCallbackHandler.class),any(Object[].class));
  var old=new ProductRecord();old.setFactoryId(7L);old.setId(42L);old.setCategory("铱杆实心");old.setMetalTypeId(1L);old.setProductType("杆实心");old.setDimensions(Map.of("diameter","2.123456","length","1.234567"));old.setVolume(BigDecimal.ONE);
  var form=new ProductRecord();form.setFactoryId(7L);form.setCategory(old.getCategory());form.setMetalTypeId(1L);form.setProductType(old.getProductType());form.setDimensions(old.getDimensions());var step=new ProductStep();step.setLossUnit("G_PER_PIECE");step.setProcessId(9L);step.setLoss(new BigDecimal("0.123456"));form.setProcesses(List.of(step));
  new ProductService(null,FactoryCatalogTestFixtures.scope(),jdbc,rules).prepare(form,old);
  assertEquals(new BigDecimal("0.123456"),form.getProcesses().get(0).getLoss());assertEquals(old.getDimensions(),form.getDimensions());
 }
}
