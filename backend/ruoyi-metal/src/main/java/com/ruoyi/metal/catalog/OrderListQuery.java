package com.ruoyi.metal.catalog;
import com.ruoyi.common.exception.ServiceException;
public class OrderListQuery extends ListQuery {
private String orderNo;public String getOrderNo(){return orderNo;}public void setOrderNo(String v){orderNo=v==null||v.isBlank()?null:v.strip();}
private String workOrderNo;public String getWorkOrderNo(){return workOrderNo;}public void setWorkOrderNo(String v){workOrderNo=v==null||v.isBlank()?null:v.strip();}
private String type;public String getType(){return type;}public void setType(String v){type=v==null||v.isBlank()?null:v.strip();}
private String filter;public String getFilter(){return filter;}public void setFilter(String v){filter=v==null||v.isBlank()?null:v.strip();}
private Long warehouseId;public Long getWarehouseId(){return warehouseId;}public void setWarehouseId(Long v){warehouseId=v;}
private Long operatorId;public Long getOperatorId(){return operatorId;}public void setOperatorId(Long v){operatorId=v;}
private Long recipientId;public Long getRecipientId(){return recipientId;}public void setRecipientId(Long v){recipientId=v;}
private Long keeperId;public Long getKeeperId(){return keeperId;}public void setKeeperId(Long v){keeperId=v;}
@Override public void validate(){super.validate();for(Long id:new Long[]{warehouseId,operatorId,recipientId,keeperId})if(id!=null&&id<=0)throw new ServiceException("筛选编号无效");if(filter!=null&&!java.util.Set.of("IN","OUT","RET","ADJ").contains(filter))throw new ServiceException("单据分类无效");if(type!=null&&!java.util.Set.of("RAW_IN","SEMI_IN","FINISHED_IN","PRODUCTION_OUT","PRODUCT_OUT","EXCHANGE_OUT","SEMI_OUT","RAW_RETURN","PRODUCT_RETURN","OPENING","ADJUST").contains(type))throw new ServiceException("单据类型无效");for(String s:new String[]{orderNo,workOrderNo})if(s!=null&&s.length()>64)throw new ServiceException("编号筛选过长");}
}