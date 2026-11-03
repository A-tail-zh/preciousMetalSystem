package com.ruoyi.metal.service;

import com.ruoyi.common.exception.ServiceException;

final class RawMaterialKinds {
 private RawMaterialKinds(){}
 static String initial(Object type){return "粉料".equals(type)?"POWDER":"UNPROCESSED";}
 static String kind(Object saved,Object type){return saved==null?initial(type):saved.toString();}
 static void unprocessed(String kind){if(!"UNPROCESSED".equals(kind))throw new ServiceException("酸洗仅可领用未处理料，不能领用粉料或已酸洗金属料");}
 static void mass(java.math.BigDecimal issued,java.math.BigDecimal output,java.math.BigDecimal returned){
  if(issued.signum()<=0||output.signum()<=0||returned.signum()<0||output.add(returned).compareTo(issued)>0)throw new ServiceException("酸洗产出须大于0，产出与余料合计不能超过领料净重");
 }
 static String labelSql(String stock,String material){return "CASE WHEN "+stock+".category<>'RAW' THEN NULL WHEN "+stock+".raw_material_kind='METAL' THEN '金属料' WHEN "+material+".material_type='粉料' THEN '粉料' ELSE '未处理料' END";}
}
