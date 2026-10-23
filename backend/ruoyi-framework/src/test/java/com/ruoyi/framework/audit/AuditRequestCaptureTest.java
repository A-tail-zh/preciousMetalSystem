package com.ruoyi.framework.audit;
import java.util.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;import org.springframework.web.bind.annotation.*;import com.alibaba.fastjson2.JSON;
class AuditRequestCaptureTest {
 static class Controller {public void update(@PathVariable("id") Long id,@RequestBody Map<String,Object> body){} public void upload(@RequestParam("factoryId") Long factory){} }
 @Test void controllerBodyWinsOverDecomposedServiceParameters()throws Exception{
  var c=new AuditContext();var body=Map.<String,Object>of("reason","precise reason","version",3,"requestId","request-uuid","nested",Map.of("password","secret-sentinel"));
  AuditRequestCapture.controller(c,Controller.class.getMethod("update",Long.class,Map.class),new Object[]{11L,body},List.of());
  String before=c.request;assertSame(body,AuditRequestCapture.service(c,new Object[]{11L,"request-uuid",3,"precise reason"}));assertEquals(before,c.request);assertTrue(c.request.contains("precise reason"));assertFalse(c.request.contains("secret-sentinel"));assertEquals("11",JSON.parseObject(c.request).getJSONObject("parameters").getString("id"));
 }
 @Test void boundedCaptureIsValidRedactedJsonAndRetainsOperationSummary(){
  var c=new AuditContext();AuditRequestCapture.service(c,new Object[]{11L,Map.of("reason","needed reason","requestId","needed-uuid","items",Collections.nCopies(100,"x".repeat(400)),"password","sentinel")});
  assertTrue(c.request.length()<=4096);assertTrue(JSON.parseObject(c.request).getBooleanValue("truncated"));assertTrue(c.request.contains("needed reason"));assertFalse(c.request.contains("sentinel"));
 }
 @Test void uploadMetadataAndParametersRemainSeparate()throws Exception{var c=new AuditContext();AuditRequestCapture.controller(c,Controller.class.getMethod("upload",Long.class),new Object[]{7L},List.of(Map.of("fileName","x.xlsx","bytes",20,"sha256","abc")));assertTrue(c.request.contains("sha256"));assertTrue(c.request.contains("factoryId"));assertFalse(c.request.contains("content"));}
 @Test void oldSingleDtoRemainsSnapshotInputAndNullAndScalarArgsAreSupported(){var c=new AuditContext();var body=Map.of("requestId","uuid");assertSame(body,AuditRequestCapture.service(c,new Object[]{body}));assertDoesNotThrow(()->AuditRequestCapture.service(new AuditContext(),new Object[]{1L,null,true}));assertNull(AuditRequestCapture.service(new AuditContext(),new Object[]{}));}
 public record SecretDto(String password,String phonenumber){}
 @Test void nestedDtoSecretsAreRedacted(){var c=new AuditContext();AuditRequestCapture.service(c,new Object[]{new SecretDto("privacy-sentinel","13912345678")});assertFalse(c.request.contains("privacy-sentinel"));assertFalse(c.request.contains("13912345678"));assertTrue(c.request.contains("[REDACTED]"));}
 @Test void multipartDisplayKeepsServiceDtoForAttribution()throws Exception{var c=new AuditContext();AuditRequestCapture.controller(c,Controller.class.getMethod("upload",Long.class),new Object[]{7L},List.of(Map.of("fileName","x.xlsx")));String display=c.request;var input=Map.of("factoryId",7L);assertSame(input,AuditRequestCapture.service(c,new Object[]{input}));assertEquals(display,c.request);}
 @Test void collectionAboveOneHundredIsCompleteOrExplicitlyMarked(){var c=new AuditContext();AuditRequestCapture.service(c,new Object[]{Map.of("items",Collections.nCopies(101,"x"))});var parsed=JSON.parseObject(c.request);assertTrue(parsed.getBooleanValue("truncated")||parsed.getJSONObject("body").getJSONArray("items").size()==101);}
 @Test void realAdjustmentRecordKeepsBody(){var c=new AuditContext();var dto=new com.ruoyi.metal.catalog.WarehouseRequests.Adjustment("real-uuid",2,11L,12L,new java.math.BigDecimal("2"),0L,"real adjustment reason");AuditRequestCapture.service(c,new Object[]{7L,dto});assertTrue(c.request.contains("real adjustment reason"),c.request);assertTrue(c.request.contains("real-uuid"));}
}
