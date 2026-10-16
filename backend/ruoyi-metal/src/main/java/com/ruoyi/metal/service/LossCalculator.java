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
  BigDecimal loss=issued.subtract(returned),threshold=theoretical.add(tolerance);return new Result(loss,loss.divide(BigDecimal.valueOf(quantity),6,RoundingMode.HALF_UP),threshold,loss.compareTo(threshold.multiply(BigDecimal.valueOf(quantity)))>0);
 }
 /**
  * 按重量比例分摊损耗，最后一项吸收舍入差额以保持总额一致。
  */
 public static List<BigDecimal> allocate(BigDecimal loss,List<BigDecimal> weights){BigDecimal total=weights.stream().reduce(BigDecimal.ZERO,BigDecimal::add);if(total.signum()<=0||weights.stream().anyMatch(v->v.signum()<=0))throw new ServiceException("分摊领料重量必须大于0");var result=new ArrayList<BigDecimal>();BigDecimal left=loss;for(int i=0;i<weights.size();i++){BigDecimal value=i==weights.size()-1?left:loss.multiply(weights.get(i)).divide(total,6,RoundingMode.DOWN);result.add(value);left=left.subtract(value);}return result;}
}
