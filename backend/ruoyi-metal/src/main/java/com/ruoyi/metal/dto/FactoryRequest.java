package com.ruoyi.metal.dto;

import java.math.BigDecimal;
import java.util.List;
import jakarta.validation.constraints.*;

/**
 * 工厂数据模型，承载业务字段及适用的参数校验规则。
 */
public class FactoryRequest {
    private Long factoryId;
    @NotBlank(message = "工厂名称不能为空")
    @Size(max = 100, message = "工厂名称不能超过100个字符")
    private String factoryName;
    @NotNull(message = "占地面积不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "占地面积必须大于0")
    @Digits(integer = 10, fraction = 4, message = "占地面积最多10位整数、4位小数")
    private BigDecimal area;
    @NotBlank(message = "详细地址不能为空")
    @Size(max = 500, message = "详细地址不能超过500个字符")
    private String address;
    @Size(max = 5000, message = "工厂简介不能超过5000个字符")
    private String introduction;
    @NotNull(message = "关联产线不能为空，请传入数组")
    @Size(max = 1000, message = "关联产线数量不能超过1000条")
    private List<@NotNull @Positive Long> productionLineIds;
    @Min(0)
    private Integer version;
    public Long getFactoryId() { return factoryId; }
    public void setFactoryId(Long v) { factoryId = v; }
    public String getFactoryName() { return factoryName; }
    public void setFactoryName(String v) { factoryName = v == null ? null : v.strip(); }
    public BigDecimal getArea() { return area; }
    public void setArea(BigDecimal v) { area = v; }
    public String getAddress() { return address; }
    public void setAddress(String v) { address = v == null ? null : v.strip(); }
    public String getIntroduction() { return introduction; }
    public void setIntroduction(String v) { introduction = v; }
    public List<Long> getProductionLineIds() { return productionLineIds; }
    public void setProductionLineIds(List<Long> v) { productionLineIds = v; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer v) { version = v; }
}
