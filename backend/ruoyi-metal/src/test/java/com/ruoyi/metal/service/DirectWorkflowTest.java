package com.ruoyi.metal.service;
import java.util.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class DirectWorkflowTest{
 @Test void absentReceivingCompanyPreservesLegacyIdempotencyText(){
  var line=new com.ruoyi.metal.catalog.WarehouseRequests.Line(null,null,"SEMI",7L,null,java.math.BigDecimal.ONE,2L,null,null,null,"客户",null,null);
  assertEquals("Line[inventoryId=null, sourceLineId=null, category=SEMI, referenceId=7, batchNo=null, netWeight=1, quantity=2, grossWeight=null, impurity=null, purity=null, organization=客户, purpose=null, remark=null]",line.toString());
  var receiver=new com.ruoyi.metal.catalog.WarehouseRequests.Line(null,null,"SEMI",7L,null,java.math.BigDecimal.ONE,2L,null,null,null,"客户",null,null,"收货企业");assertNotEquals(line.toString(),receiver.toString());
 }
 @Test void onlyUnreferencedWaitingOrdersAreEditable(){for(String status:List.of("WAITING_MATERIAL","PENDING","APPROVED","REJECTED","IN_PROGRESS","COMPLETED")){for(boolean references:List.of(false,true)){var row=new HashMap<String,Object>();row.put("status",status);row.put("hasBusinessReferences",references?1L:0L);WorkOrderService.capabilities(row);boolean allowed=status.equals("WAITING_MATERIAL")&&!references;assertEquals(allowed,row.get("canEdit"));assertEquals(allowed,row.get("canDelete"));assertEquals(allowed,Objects.toString(row.get("operationDisabledReason")).isEmpty());}}}
 @Test void templatesMatchInboundFieldsAndHandleReorderedLegacyHeaders(){
  assertEquals(List.of("物料／产品ID","毛重(g)","杂质(g)","实测净重(g)","单位名称","用途","备注"),WarehouseTemplateLayout.inbound("RAW_IN",false).stream().map(WarehouseTemplateLayout.Column::label).toList());
  for(String type:List.of("SEMI_IN","FINISHED_IN")){assertEquals(List.of("物料／产品ID","批次号","实测净重(g)","数量(件)","纯度(%)","单位名称","收货单位","用途","备注"),WarehouseTemplateLayout.inbound(type,false).stream().map(WarehouseTemplateLayout.Column::label).toList());var old=WarehouseTemplateLayout.inbound(type,false).stream().filter(c->!c.key().equals("receivingOrganization")).map(WarehouseTemplateLayout.Column::label).toList();assertFalse(WarehouseTemplateLayout.identify(old,type).containsKey("receivingOrganization"));assertFalse(WarehouseTemplateLayout.inbound(type,true).stream().anyMatch(c->c.key().equals("batchNo")));}
  var labels=new ArrayList<>(WarehouseTemplateLayout.legacy().stream().map(WarehouseTemplateLayout.Column::label).toList());Collections.reverse(labels);assertEquals(labels.indexOf("资料ID"),WarehouseTemplateLayout.identify(labels,"RAW_IN").get("referenceId"));
  assertThrows(RuntimeException.class,()->WarehouseTemplateLayout.identify(List.of("资料ID","资料ID"),"RAW_IN"));assertThrows(RuntimeException.class,()->WarehouseTemplateLayout.identify(List.of("未知列"),"RAW_IN"));
 }
}
