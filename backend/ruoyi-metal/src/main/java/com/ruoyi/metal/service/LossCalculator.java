package com.ruoyi.metal.service;
import java.math.*;
import java.util.*;
import com.ruoyi.common.exception.ServiceException;
/**
 * 使用十进制精度计算加工损耗、单位损耗和预警阈值，并按领料重量分摊损耗。
 */
public final class LossCalculator {
 private LossCalculator(){}
 public record Result(BigDecimal loss,BigDecimal average,BigDecimal threshold,boolean abnormal){}
 /**
  * 校验领料、回料及完成数量，计算总损耗、单位损耗和是否超过理论损耗加容差的阈值。
  */
 public static Result calculate(BigDecimal issued,BigDecimal returned,int quantity,BigDecimal theoretical,BigDecimal tolerance){
  if(issued.signum()<=0||returned.signum()<0||returned.compareTo(issued)>0||quantity<=0||theoretical.signum()<0||tolerance.signum()<0)throw new ServiceException("领料须大于0，回料不能超过领料，完成数量须大于0");
  BigDecimal loss=issued.subtract(returned),threshold=theoretical.add(tolerance);return new Result(loss,loss.divide(BigDecimal.valueOf(quantity),2,RoundingMode.HALF_UP),threshold,loss.compareTo(threshold.multiply(BigDecimal.valueOf(quantity)))>0);
 }
 /**
  * 以0.01g采用最大余数法分摊；调用方按类别、资料ID排序以稳定处理相同余数。
  */
 public record PercentageResult(BigDecimal loss,BigDecimal rate,BigDecimal threshold,boolean abnormal){}
 public static BigDecimal denominator(String basis,BigDecimal issued,BigDecimal returns){
  return switch(basis){case "OUTBOUND_TOTAL"->issued;case "NET_INPUT"->issued.subtract(returns);default->throw new ServiceException("损耗率口径无效");};
 }
 public static PercentageResult percentage(BigDecimal issued,BigDecimal returns,BigDecimal output,BigDecimal theoretical,String basis){
  BigDecimal net=issued.subtract(returns),loss=net.subtract(output),denominator=denominator(basis,issued,returns);
  if(issued.signum()<=0||returns.signum()<0||output.signum()<0||net.signum()<=0||loss.signum()<0||theoretical.signum()<0||theoretical.compareTo(BigDecimal.valueOf(100))>0)throw new ServiceException("百分比结算须有正数实际投入，产出及还料不能超过领料，理论损耗率须为0至100%");
  return new PercentageResult(loss,loss.multiply(BigDecimal.valueOf(100)).divide(denominator,2,RoundingMode.HALF_UP),theoretical,loss.multiply(BigDecimal.valueOf(100)).compareTo(denominator.multiply(theoretical))>0);
 }
 public static PercentageResult percentage(BigDecimal issued,BigDecimal returns,BigDecimal output,BigDecimal theoretical){
  return percentage(issued,returns,output,theoretical,"NET_INPUT");
 }
 public static List<BigDecimal> allocate(BigDecimal loss,List<BigDecimal> weights){
  BigDecimal total=weights.stream().reduce(BigDecimal.ZERO,BigDecimal::add);
  if(total.signum()<=0||loss.signum()<0||weights.stream().anyMatch(v->v.signum()<=0))throw new ServiceException("分摊领料重量必须大于0，损耗不能为负");
  long cents=BusinessDecimal.round(loss).movePointRight(2).longValueExact(),assigned=0;
  var units=new ArrayList<Long>();var remainders=new ArrayList<BigDecimal>();
  for(var weight:weights){var numerator=BigDecimal.valueOf(cents).multiply(weight);long floor=numerator.divide(total,0,RoundingMode.DOWN).longValueExact();units.add(floor);assigned+=floor;remainders.add(numerator.subtract(total.multiply(BigDecimal.valueOf(floor))));}
  var indices=new ArrayList<Integer>();for(int i=0;i<weights.size();i++)indices.add(i);
  indices.sort((a,b)->{int c=remainders.get(b).compareTo(remainders.get(a));return c==0?Integer.compare(a,b):c;});
  for(int i=0;i<cents-assigned;i++){int index=indices.get(i);units.set(index,units.get(index)+1);}
  return units.stream().map(n->BigDecimal.valueOf(n,2)).toList();
 }
}
