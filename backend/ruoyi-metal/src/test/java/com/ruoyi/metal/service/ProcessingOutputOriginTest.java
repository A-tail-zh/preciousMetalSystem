package com.ruoyi.metal.service;
import java.util.*;import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ruoyi.metal.catalog.WarehouseRequests.Line;

class ProcessingOutputOriginTest {
 Line line(String origin){return new Line(null,null,"RAW",1L,null,BigDecimal.ONE,0L,null,null,null,null,null,null,null,null,null,null,null,null,null,"FACTORY",origin);}
 Map<String,Object> material(String type){return Map.of("material_type",type,"metal_type_id",71L,"metal_type_name","铱");}
 Map<String,Object> batch(String role){return Map.of("processing_policy_version",2,"process_role",role,"batch_id",4L);}
 @Test void nativeReceiptMustBeExplicitAndDoesNotMergeWithRecycled(){
  var j=mock(JdbcTemplate.class);
  assertThrows(RuntimeException.class,()->ProcessingOutputOrigin.determine(j,"RAW_IN","RAW",line(null),material("粉料"),null,null));
  var nativePowder=ProcessingOutputOrigin.determine(j,"RAW_IN","RAW",line("NATIVE"),material("粉料"),null,null);
  var recycled=ProcessingOutputOrigin.determine(j,"RAW_IN","RAW",line("RECYCLED"),material("粉料"),null,null);
  assertEquals("EXEMPT",nativePowder.washing());assertEquals("REQUIRED",recycled.washing());assertNotEquals(nativePowder.rawKey(null,null),recycled.rawKey(null,null));
 }
 @Test void acidPowderTargetRemainsWashedMetalAndCannotBeForged(){
  var j=mock(JdbcTemplate.class);var origin=ProcessingOutputOrigin.determine(j,"RAW_PROCESS_IN","RAW",line(null),material("粉料"),null,batch("ACID_WASH"));
  assertEquals("METAL",origin.rawKind());assertEquals("PASSED",origin.washing());assertEquals("RECYCLED",origin.powder());
  assertThrows(RuntimeException.class,()->ProcessingOutputOrigin.determine(j,"RAW_PROCESS_IN","RAW",line("NATIVE"),material("粉料"),null,batch("ACID_WASH")));
 }
 @Test void oldMaterialProcessedIntoPowderNeverAcquiresNativeExemption(){
  var j=mock(JdbcTemplate.class);when(j.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(Map.of("powder_origin","RECYCLED","washing_status","REQUIRED")));
  var origin=ProcessingOutputOrigin.determine(j,"RAW_PROCESS_IN","RAW",line(null),material("粉料"),null,batch("PREPARATION"));
  assertEquals("RECYCLED",origin.powder());assertEquals("REQUIRED",origin.washing());
 }
 @Test void preparationKeepsActualWashProofAndUnusedReturnsKeepOriginalEvidence(){
  var j=mock(JdbcTemplate.class);when(j.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(Map.of("powder_origin","RECYCLED","washing_status","PASSED")));
  var result=ProcessingOutputOrigin.determine(j,"RAW_PROCESS_IN","RAW",line(null),material("粉料"),null,batch("PREPARATION"));assertEquals("METAL",result.rawKind());assertEquals("PASSED",result.washing());
  var saved=Map.<String,Object>of("powder_origin","NATIVE","washing_status","EXEMPT","origin_policy_version",2,"origin_verification_id",7L,"raw_material_kind","POWDER");
  var returned=ProcessingOutputOrigin.determine(j,"RAW_RETURN","RAW",line(null),material("粉料"),saved,batch("PREPARATION"));assertEquals("NATIVE",returned.powder());assertEquals(7L,returned.verification());
 }
}
