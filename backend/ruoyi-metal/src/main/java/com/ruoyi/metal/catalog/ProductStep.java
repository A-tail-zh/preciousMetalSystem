package com.ruoyi.metal.catalog;
import java.math.BigDecimal;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonFormat;
/**
 * 产品工序及理论损耗数据模型，承载业务字段及适用的参数校验规则。
 */
public class ProductStep {
    @NotNull @Positive @JsonFormat(shape=JsonFormat.Shape.STRING) private Long processId;
    @NotNull @DecimalMin("0") @Digits(integer=14,fraction=6) @JsonFormat(shape=JsonFormat.Shape.STRING) private BigDecimal loss;
    private String processName;
    public Long getProcessId(){return processId;} public void setProcessId(Long v){processId=v;}
    public BigDecimal getLoss(){return loss;} public void setLoss(BigDecimal v){loss=v;}
    public String getProcessName(){return processName;} public void setProcessName(String v){processName=v;}
}
