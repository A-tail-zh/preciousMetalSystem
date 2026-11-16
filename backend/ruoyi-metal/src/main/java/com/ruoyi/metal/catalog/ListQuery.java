package com.ruoyi.metal.catalog;
import java.time.*;import com.ruoyi.common.exception.ServiceException;
/** Optional, literal-match list filters shared by listing and export. */
public class ListQuery {
 private String companyName;public String getCompanyName(){return companyName;}public void setCompanyName(String v){companyName=v==null||v.isBlank()?null:v.strip();}public String getCompanyNamePattern(){return pattern(companyName);}
 private String contactName;public String getContactName(){return contactName;}public void setContactName(String v){contactName=v==null||v.isBlank()?null:v.strip();}public String getContactNamePattern(){return pattern(contactName);}
 private String contactPhone;public String getContactPhone(){return contactPhone;}public void setContactPhone(String v){contactPhone=v==null||v.isBlank()?null:v.strip();}public String getContactPhonePattern(){return pattern(contactPhone);}
 private String name;public String getName(){return name;}public void setName(String v){name=v==null||v.isBlank()?null:v.strip();}
 private String code;public String getCode(){return code;}public void setCode(String v){code=v==null||v.isBlank()?null:v.strip();}
 private String address;public String getAddress(){return address;}public void setAddress(String v){address=v==null||v.isBlank()?null:v.strip();}
 private Long metalTypeId;public Long getMetalTypeId(){return metalTypeId;}public void setMetalTypeId(Long v){metalTypeId=v;}
 private String productType;public String getProductType(){return productType;}public void setProductType(String v){productType=v==null||v.isBlank()?null:v.strip();}
 private String category;public String getCategory(){return category;}public void setCategory(String v){category=v==null||v.isBlank()?null:v.strip();}
 private String materialType;public String getMaterialType(){return materialType;}public void setMaterialType(String v){materialType=v==null||v.isBlank()?null:v.strip();}
 private String specification;public String getSpecification(){return specification;}public void setSpecification(String v){specification=v==null||v.isBlank()?null:v.strip();}
 private String startDate;public String getStartDate(){return startDate;}public void setStartDate(String v){startDate=v==null||v.isBlank()?null:v.strip();}
 private String endDate;public String getEndDate(){return endDate;}public void setEndDate(String v){endDate=v==null||v.isBlank()?null:v.strip();}
 private Long factoryId;public Long getFactoryId(){return factoryId;}public void setFactoryId(Long v){factoryId=v;}
 private Long roleId;public Long getRoleId(){return roleId;}public void setRoleId(Long v){roleId=v;}
 private Long productId;public Long getProductId(){return productId;}public void setProductId(Long v){productId=v;}
 private Integer processType;public Integer getProcessType(){return processType;}public void setProcessType(Integer v){processType=v;}
 public static String pattern(String value){return value==null||value.isBlank()?null:"%"+value.replace("!","!!").replace("%","!%").replace("_","!_")+"%";}
 public LocalDateTime getStartTime(){return startDate==null||startDate.isBlank()?null:LocalDate.parse(startDate).atStartOfDay();}
 public LocalDateTime getEndExclusive(){return endDate==null||endDate.isBlank()?null:LocalDate.parse(endDate).plusDays(1).atStartOfDay();}
 public void validate(){try{var start=getStartTime();var end=getEndExclusive();if(start!=null&&end!=null&&!start.isBefore(end))throw new IllegalArgumentException();}catch(RuntimeException e){throw new ServiceException("日期区间无效，开始日期不能晚于结束日期");}
 for(Long id:new Long[]{factoryId,roleId,productId,metalTypeId})if(id!=null&&id<=0)throw new ServiceException("筛选编号无效");
 for(String text:new String[]{name,code,address,category,materialType,specification,productType,companyName,contactName,contactPhone})if(text!=null&&text.length()>500)throw new ServiceException("筛选文本过长");if(processType!=null&&processType<1)throw new ServiceException("工序类型无效");}
 public String getNamePattern(){return pattern(name);}
 public String getCodePattern(){return pattern(code);}
 public String getAddressPattern(){return pattern(address);}
 public String getSpecificationPattern(){return pattern(specification);}
}
