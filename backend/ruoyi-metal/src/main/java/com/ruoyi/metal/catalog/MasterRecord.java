package com.ruoyi.metal.catalog;

import com.ruoyi.common.core.domain.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Min;

/**
 * 基础资料公共字段，包含记录 ID 和用于并发修改校验的版本号。
 */
public class MasterRecord extends BaseEntity {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;
    @Min(0)
    private Integer version;

    public Long getId() {
        return id;
    }

    public void setId(Long v) {
        id = v;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer v) {
        version = v;
    }
}
