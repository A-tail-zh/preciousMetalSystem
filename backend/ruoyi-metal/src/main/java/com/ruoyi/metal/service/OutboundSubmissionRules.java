package com.ruoyi.metal.service;
import java.util.*;import java.nio.charset.StandardCharsets;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.metal.catalog.OutboundSubmissionRequests.*;
public final class OutboundSubmissionRules {
 private OutboundSubmissionRules(){}
 public static void validate(Request r,boolean preview){
  if(r==null||!"PRODUCTION_OUT".equals(r.type()))throw new ServiceException("多仓出库仅支持生产出库");
  if(r.factoryId()==null||r.factoryId()<=0||r.workOrderId()==null||r.workOrderId()<=0||r.itemId()==null||r.itemId()<=0||r.processId()==null||r.processId()<=0)throw new ServiceException("请选择工厂、生产工单、产品明细和工序");
  if(!preview){try{if(!UUID.fromString(r.requestId()).toString().equals(r.requestId()))throw new IllegalArgumentException();}catch(Exception e){throw new ServiceException("提交请求编号无效");}if(r.orderDate()==null)throw new ServiceException("请选择出库日期");}
  if(r.groups()==null||r.groups().isEmpty())throw new ServiceException("请至少选择一个仓库");
  Set<Long> ids=new HashSet<>();long total=0;
  for(Group g:r.groups()){if(g==null||g.warehouseId()==null||g.warehouseId()<=0||!ids.add(g.warehouseId()))throw new ServiceException("仓库无效或重复");if(!preview)people(g.peopleOverride()==null?new People(r.operatorIds(),r.recipientId(),r.keeperId(),r.reviewerId()):g.peopleOverride());if(g.lines()==null||g.lines().stream().anyMatch(Objects::isNull)||!preview&&g.lines().isEmpty())throw new ServiceException("每个仓库都须添加有效物料明细");total+=g.lines().size();}
  if(total>1000)throw new ServiceException("所有仓库合计最多1000条物料明细");
 }
 private static void people(People p){var ids=p.operatorIds();if(ids!=null&&(ids.size()>20||ids.stream().anyMatch(x->x==null||x<=0)||new HashSet<>(ids).size()!=ids.size()))throw new ServiceException("经办人最多20位，不能重复");if(p.recipientId()==null||p.recipientId()<=0)throw new ServiceException("请选择领用人");if(p.keeperId()==null||p.keeperId()<=0)throw new ServiceException("请选择仓管员");}
 public static String childRequest(String id,long warehouse){return UUID.nameUUIDFromBytes(("outbound:"+id+":"+warehouse).getBytes(StandardCharsets.UTF_8)).toString();}
}
