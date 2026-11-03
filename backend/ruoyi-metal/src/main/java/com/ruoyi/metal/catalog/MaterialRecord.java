package com.ruoyi.metal.catalog;
import java.util.*;
import java.math.BigDecimal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonFormat;
/**
 * 物料数据模型，承载业务字段及适用的参数校验规则。
 */
public class MaterialRecord extends MasterRecord {
    @NotBlank @Size(max=64)  private String code;
    @NotBlank @Size(max=100) private String materialType;
    @Size(max=200)  private String specification;
    @JsonFormat(shape=JsonFormat.Shape.STRING)
    @Positive private Long metalTypeId;
    private String metalTypeName;
    private boolean classificationLocked;
    public String getCode(){return code;} public void setCode(String v){code=v==null?null:v.strip();}
    public String getMaterialType(){return materialType;} public void setMaterialType(String v){materialType=v==null?null:v.strip();}
    public String getSpecification(){return specification;} public void setSpecification(String v){specification=v==null?null:v.strip();}
    public Long getMetalTypeId(){return metalTypeId;} public void setMetalTypeId(Long v){metalTypeId=v;}
    public String getMetalTypeName(){return metalTypeName;} public void setMetalTypeName(String v){metalTypeName=v;}
    public boolean isClassificationLocked(){return classificationLocked;} public void setClassificationLocked(boolean v){classificationLocked=v;}
}
