package com.ruoyi.metal.service;
import java.math.*;
import java.util.*;
import com.ruoyi.common.exception.ServiceException;

/**
 * 根据支持的产品类别及尺寸计算体积，并统一校验存储精度。
 */
public final class VolumeCalculator {
 private VolumeCalculator(){}
 /**
  * 判断产品类别是否已有体积计算公式。
  */
 public static boolean supported(String category){return category!=null&&Set.of("铱坩埚平底","铱环","铱杆实心","铱杆空心").contains(category);}
 /**
  * 校验产品类别、尺寸及内外径关系，按对应公式计算体积。
  */
 public static BigDecimal calculate(String category,Map<String,String> dimensions,BigDecimal pi){
  if(!supported(category))throw new ServiceException("该类别尚未提供计算公式");
  var fields=ProductService.DIMENSIONS.get(category);
  if(dimensions==null||!dimensions.keySet().equals(new HashSet<>(fields)))throw new ServiceException("请填写类别对应的全部尺寸");
  Map<String,BigDecimal> d=new HashMap<>();
  for(String field:fields){try{BigDecimal v=new BigDecimal(dimensions.get(field));if(v.signum()<=0||v.scale()>6||v.precision()-v.scale()>14)throw new IllegalArgumentException();d.put(field,v);}catch(Exception e){throw new ServiceException("尺寸必须为正数，最多6位小数："+field);}}
  if(d.containsKey("outerDiameter")&&d.get("outerDiameter").compareTo(d.get("innerDiameter"))<=0)throw new ServiceException("外径必须大于内径");
  BigDecimal two=BigDecimal.valueOf(2),value;
  switch(category){
   case "铱杆实心":value=pi.multiply(d.get("diameter").divide(two).pow(2)).multiply(d.get("length"));break;
   case "铱杆空心":value=pi.multiply(d.get("outerDiameter").subtract(d.get("innerDiameter")).pow(2)).multiply(d.get("length"));break;
   case "铱环":value=pi.multiply(d.get("outerDiameter").divide(two).pow(2).subtract(d.get("innerDiameter").divide(two).pow(2))).multiply(d.get("height"));break;
   default:BigDecimal r=d.get("innerDiameter").divide(two);value=pi.multiply(r.pow(2)).multiply(d.get("bottomThickness")).add(two.multiply(pi).multiply(r).multiply(d.get("innerHeight")).multiply(d.get("wallThickness")));
  }
  return value;
 }
 /**
  * 将计算结果四舍五入保留六位小数，并校验整数位上限。
  */
 public static BigDecimal stored(BigDecimal value){BigDecimal rounded=value.setScale(6,RoundingMode.HALF_UP);if(rounded.precision()-rounded.scale()>14)throw new ServiceException("计算体积超过支持范围");return rounded;}
}
