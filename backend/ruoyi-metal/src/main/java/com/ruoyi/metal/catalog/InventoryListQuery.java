package com.ruoyi.metal.catalog;
import com.ruoyi.common.exception.ServiceException;
public class InventoryListQuery extends ListQuery {
 private String metalTypeName,sourceProcessName;public String getMetalTypeName(){return metalTypeName;}public void setMetalTypeName(String value){metalTypeName=value==null||value.isBlank()?null:value.trim();}public String getSourceProcessName(){return sourceProcessName;}public void setSourceProcessName(String value){sourceProcessName=value==null||value.isBlank()?null:value.trim();}
 private String filter;private Long warehouseId,sourceProcessId;
 public String getFilter(){return filter;}public void setFilter(String value){filter=value==null||value.isBlank()?null:value.trim();}
 public Long getWarehouseId(){return warehouseId;}public void setWarehouseId(Long value){warehouseId=value;}
 public Long getSourceProcessId(){return sourceProcessId;}public void setSourceProcessId(Long value){sourceProcessId=value;}
 @Override public void validate(){super.validate();for(String text:new String[]{metalTypeName,sourceProcessName})if(text!=null&&text.length()>500)throw new ServiceException("筛选文本过长");if(filter!=null&&!java.util.Set.of("RAW","SEMI","FINISHED").contains(filter))throw new ServiceException("库存分类无效");for(Long id:new Long[]{warehouseId,sourceProcessId})if(id!=null&&id<=0)throw new ServiceException("筛选编号无效");}
}
