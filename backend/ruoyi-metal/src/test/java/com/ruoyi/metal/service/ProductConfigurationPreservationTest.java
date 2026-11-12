package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.metal.catalog.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ProductConfigurationPreservationTest {
 @Test void reorderAndNewStepKeepConfigurationByProcessIdentity(){
  var jdbc=mock(JdbcTemplate.class);
  when(jdbc.queryForList(startsWith("SELECT process_id,measurement_mode"),any(Object[].class))).thenReturn(List.of(
   Map.of("process_id",1L,"measurement_mode","WEIGHT","output_category","RAW","output_form","METAL","output_material_id",9L),
   Map.of("process_id",2L,"measurement_mode","PIECE","output_category","PRODUCT")));
  var product=new ProductRecord();product.setId(42L);var steps=new ArrayList<ProductStep>();
  for(long id:List.of(2L,1L,3L)){var step=new ProductStep();step.setProcessId(id);step.setLoss(new BigDecimal("2.00"));step.setMeasurementMode("WEIGHT");steps.add(step);}product.setProcesses(steps);
  new ProductService(null,FactoryCatalogTestFixtures.scope(),jdbc,mock(QuoteRuleService.class)).relations(product);
  verify(jdbc).update(startsWith("INSERT INTO metal_product_process"),eq(42L),eq(2L),eq(1),eq(new BigDecimal("2.00")),eq("PIECE"),eq("PERCENT"),eq("PRODUCT"),isNull(),isNull());
  verify(jdbc).update(startsWith("INSERT INTO metal_product_process"),eq(42L),eq(1L),eq(2),eq(new BigDecimal("2.00")),eq("WEIGHT"),eq("PERCENT"),eq("RAW"),eq("METAL"),eq(9L));
  verify(jdbc).update(startsWith("INSERT INTO metal_product_process"),eq(42L),eq(3L),eq(3),eq(new BigDecimal("2.00")),eq("PIECE"),eq("PERCENT"),isNull(),isNull(),isNull());
 }
 @Test void productJsonMeasurementIsReadOnly()throws Exception{
  var mapper=new ObjectMapper();var step=mapper.readValue("{\"processId\":1,\"loss\":0,\"measurementMode\":\"WEIGHT\"}",ProductStep.class);
  assertEquals("PIECE",step.getMeasurementMode());step.setMeasurementMode("WEIGHT");assertEquals("WEIGHT",mapper.readTree(mapper.writeValueAsString(step)).get("measurementMode").asText());
 }
}
