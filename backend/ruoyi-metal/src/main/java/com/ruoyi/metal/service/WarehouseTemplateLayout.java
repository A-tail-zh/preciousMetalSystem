package com.ruoyi.metal.service;
import java.util.*;
import com.ruoyi.common.exception.ServiceException;
/** Shared named columns for generation and parsing; legacy workbooks remain readable. */
public final class WarehouseTemplateLayout {
 private WarehouseTemplateLayout(){}
 public record Column(String key,String label){}
 private static Column c(String key,String label){return new Column(key,label);}
 public static List<Column> legacy(){return List.of(c("inventoryId","库存ID"),c("referenceId","资料ID"),c("sourceLineId","原出库明细ID"),c("category","分类"),c("batchNo","批次号"),c("netWeight","净重(g)"),c("quantity","数量"),c("grossWeight","毛重(g)"),c("impurity","杂质(g)"),c("purity","纯度(%)"),c("organization","单位名称"),c("purpose","用途"),c("remark","备注"));}
 public static List<Column> production(){var columns=new ArrayList<>(legacy());columns.add(c("releaseId","半成品释放记录ID"));columns.add(c("skipPermitId","工艺跳步许可ID"));columns.add(c("skipPermitVersion","工艺跳步许可版本"));return List.copyOf(columns);}
 public static List<Column> inbound(String type,boolean batchBound){
  if(!Set.of("RAW_IN","FINISHED_IN","SEMI_IN","PROCESS_RAW_RETURN").contains(type))throw new ServiceException("新模板仅支持入库类型");
  var columns=new ArrayList<Column>();columns.add(c("referenceId","物料／产品ID"));
  if(Set.of("RAW_IN","PROCESS_RAW_RETURN").contains(type)){columns.add(c("grossWeight","毛重(g)"));columns.add(c("impurity","杂质(g)"));}
  else if(!batchBound)columns.add(c("batchNo","批次号"));
  columns.add(c("netWeight","实测净重(g)"));
  if(!Set.of("RAW_IN","PROCESS_RAW_RETURN").contains(type)){columns.add(c("quantity","数量(件)"));columns.add(c("purity","纯度(%)"));}
  columns.add(c("organization","单位名称"));if(!Set.of("RAW_IN","PROCESS_RAW_RETURN").contains(type))columns.add(c("receivingOrganization","收货单位"));columns.addAll(List.of(c("purpose","用途"),c("remark","备注")));return List.copyOf(columns);
 }
 public static Map<String,Integer> identify(List<String> headers,String type){
  if(headers.isEmpty()||new HashSet<>(headers).size()!=headers.size())throw new ServiceException("模板表头为空或重复，请重新下载模板");
  var candidates=new ArrayList<List<Column>>();candidates.add(legacy());candidates.add(production());candidates.add(production().stream().filter(c->!c.key().startsWith("skipPermit")).toList());if(Set.of("RAW_IN","FINISHED_IN","SEMI_IN","PROCESS_RAW_RETURN").contains(type)){for(boolean bound:List.of(false,true)){var current=inbound(type,bound);candidates.add(current);candidates.add(current.stream().filter(c->!c.key().equals("receivingOrganization")).toList());}}
  for(var columns:candidates){if(headers.size()==columns.size()&&new HashSet<>(headers).equals(new HashSet<>(columns.stream().map(Column::label).toList()))){var positions=new LinkedHashMap<String,Integer>();for(var column:columns)positions.put(column.key(),headers.indexOf(column.label()));return positions;}}
  throw new ServiceException("模板表头不匹配当前单据类型，请重新下载模板");
 }
 public static String category(String type){return switch(type){case "RAW_IN","PROCESS_RAW_RETURN"->"RAW";case "SEMI_IN"->"SEMI";case "FINISHED_IN"->"FINISHED";default->null;};}
}
