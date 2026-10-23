package com.ruoyi.metal.service;
import java.math.*;import java.util.*;import com.ruoyi.common.exception.ServiceException;
/** Shared immutable rules for semi-finished stock: provenance is never removed. */
final class SemiFlowRules {
 private SemiFlowRules(){}
 static boolean compatible(Long sourceProduct,String sourceSpec,List<Long> sourceRoute,Long targetProduct,String targetSpec,List<Long> requiredRoute){return sourceProduct!=null&&sourceProduct.equals(targetProduct)&&sourceSpec!=null&&!sourceSpec.trim().isEmpty()&&targetSpec!=null&&sourceSpec.trim().equals(targetSpec.trim())&&!sourceRoute.isEmpty()&&sourceRoute.equals(requiredRoute);}
 static boolean sameCustomer(String a,String b){return a!=null&&b!=null&&!a.trim().isEmpty()&&a.trim().equals(b.trim());}
 static long netPieces(long issued,long returned){if(issued<returned)throw new ServiceException("历史半成品还库件数超过领用件数，请人工核对");return issued-returned;}
 static BigDecimal loss(BigDecimal issued,BigDecimal rawReturn,BigDecimal semiReturn,BigDecimal output){BigDecimal result=issued.subtract(rawReturn).subtract(semiReturn).subtract(output);if(result.signum()<0)throw new ServiceException("产出与还库合计超过领料");return result.setScale(6,RoundingMode.UNNECESSARY);}
 static void release(long available,BigDecimal weight,long pieces,BigDecimal requested){if(pieces<1||pieces>available||requested==null||requested.signum()<=0||requested.scale()>6||requested.compareTo(weight)>0||(pieces==available)!=(requested.compareTo(weight)==0))throw new ServiceException("释放件数、净重超过可用余额或未保留一致的剩余件重");}
 static long cancelled(long planned,long completed){if(completed<0||completed>planned)throw new ServiceException("历史完成件数异常，请人工核对");return planned-completed;}
}
