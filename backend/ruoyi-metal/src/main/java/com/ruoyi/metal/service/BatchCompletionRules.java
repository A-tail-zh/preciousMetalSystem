package com.ruoyi.metal.service;

import com.ruoyi.common.exception.ServiceException;

/** Invariants shared by posting, final return confirmation and UI context. */
public final class BatchCompletionRules {
 public static final String POLICY="SINGLE_OUTPUT_RETURN";
 private BatchCompletionRules(){}
 public static String phase(String status,long outputs,boolean confirmed){
  if("VOID".equals(status))return "VOID";
  if("SETTLED".equals(status))return "SETTLED";
  return outputs==0?"AWAITING_OUTPUT":"AWAITING_RETURN";
 }
 public static void requireOutput(long outputs){if(outputs!=0)throw new ServiceException("本批次已登记产出入库，每批只允许一次有效入库");}
 public static Long selectBatch(java.util.List<Long> candidates,Long requested){
  if(requested!=null){if(!candidates.contains(requested))throw new ServiceException("指定批次不属于当前工单工序或已结束");return requested;}
  if(candidates.size()>1)throw new ServiceException("存在多个未结算批次，请明确选择当前批次");
  return candidates.isEmpty()?null:candidates.get(0);
 }
 public static void requireConfirmation(long outputs,String mode,boolean checked,int documents){
  if(outputs!=1)throw new ServiceException("请先完成本批次一次产出入库");
  if(!checked)throw new ServiceException("请确认全部实物余料已核对");
  if(!"NONE".equals(mode)&&!"RETURNED".equals(mode))throw new ServiceException("请选择全部还料或无余料");
  if("NONE".equals(mode)&&documents!=0||"RETURNED".equals(mode)&&documents==0)throw new ServiceException("还料确认方式与物料清单不一致");
 }
}
