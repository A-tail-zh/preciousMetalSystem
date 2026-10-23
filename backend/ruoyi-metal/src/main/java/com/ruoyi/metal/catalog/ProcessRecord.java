package com.ruoyi.metal.catalog;

import java.util.*;
import java.math.BigDecimal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 工序数据模型，承载业务字段及适用的参数校验规则。
 */
public class ProcessRecord extends MasterRecord {
    @NotBlank
    @Size(max = 100)
    private String name;
    @Size(max = 2000)
    private String description;
    @NotNull
    @Min(1)
    @Max(9)
    private Integer processType;

    public String getName() {
        return name;
    }

    public void setName(String v) {
        name = v == null ? null : v.strip();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String v) {
        description = v == null ? null : v.strip();
    }

    public Integer getProcessType() {
        return processType;
    }

    public void setProcessType(Integer v) {
        processType = v;
    }
}
