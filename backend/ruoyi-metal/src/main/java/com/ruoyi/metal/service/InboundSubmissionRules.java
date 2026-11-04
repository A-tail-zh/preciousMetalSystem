package com.ruoyi.metal.service;
import java.util.*;
import java.nio.charset.StandardCharsets;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.metal.catalog.InboundSubmissionRequests.*;
public final class InboundSubmissionRules {
 private InboundSubmissionRules(){}
 public static void validate(Request r){
  if(r==null)throw new ServiceException("请填写入库信息");
  try{if(!UUID.fromString(r.requestId()).toString().equals(r.requestId()))throw new IllegalArgumentException();}catch(Exception e){throw new ServiceException("提交请求编号无效");}
  if(!Set.of("RAW_IN","SEMI_IN","FINISHED_IN").contains(r.type()==null?"":r.type()))throw new ServiceException("多仓提交仅支持原材料、半成品及成品入库");
  if(r.factoryId()==null||r.factoryId()<=0||r.orderDate()==null)throw new ServiceException("请选择工厂和入库日期");
  if(r.groups()==null||r.groups().isEmpty())throw new ServiceException("请至少选择一个仓库");
  Set<Long> ids=new HashSet<>();long total=0;
  for(Group g:r.groups()){
   if(g==null||g.warehouseId()==null||g.warehouseId()<=0||!ids.add(g.warehouseId()))throw new ServiceException("仓库无效或重复");
   people(g.peopleOverride()==null?new People(r.operatorIds(),r.keeperId(),r.inspectorId(),r.reviewerId()):g.peopleOverride());
   if(g.lines()==null||g.lines().isEmpty()||g.lines().stream().anyMatch(Objects::isNull))throw new ServiceException("每个仓库都须添加有效物料明细");
   total+=g.lines().size();
  }
  if(total>1000)throw new ServiceException("所有仓库合计最多1000条物料明细");
  if("RAW_IN".equals(r.type())&&(r.workOrderId()!=null||r.itemId()!=null||r.processId()!=null||r.batchId()!=null||Boolean.TRUE.equals(r.settle())))throw new ServiceException("原材料入库不能关联生产批次或结算");
 }
 private static void people(People p){if(p.operatorIds()==null||p.operatorIds().isEmpty()||p.operatorIds().size()>20||p.operatorIds().stream().anyMatch(Objects::isNull)||new HashSet<>(p.operatorIds()).size()!=p.operatorIds().size()||p.operatorIds().stream().anyMatch(x->x<=0))throw new ServiceException("请选择1至20位不重复经办人");if(p.keeperId()==null||p.keeperId()<=0)throw new ServiceException("请选择仓管员");}
 public static String childRequest(String id,Long warehouse){return UUID.nameUUIDFromBytes((id+":"+warehouse).getBytes(StandardCharsets.UTF_8)).toString();}
}
