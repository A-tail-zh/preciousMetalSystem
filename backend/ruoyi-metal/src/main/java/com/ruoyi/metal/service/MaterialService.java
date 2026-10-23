package com.ruoyi.metal.service;

import java.util.Set;

import org.springframework.stereotype.Service;
import com.ruoyi.metal.catalog.*;
import com.ruoyi.metal.mapper.MaterialMapper;
import com.ruoyi.metal.security.FactoryScope;
import com.ruoyi.common.exception.ServiceException;

/**
 * 物料业务服务，负责业务校验、数据处理和相关持久化操作。
 */
@Service
public class MaterialService extends MasterService<MaterialRecord> {
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public MaterialService(MaterialMapper mapper, FactoryScope scope, org.springframework.jdbc.core.JdbcTemplate jdbc) {
        super(mapper, scope);
        this.jdbc = jdbc;
    }

    /**
     * 检查业务引用和保护规则，不满足删除条件时抛出异常。
     */
    @Override
    protected void deletable(MaterialRecord row) {
        WarehouseReferences.material(jdbc, row.getId());
    }

    /**
     * 保存前校验业务数据，并准备需要持久化的字段。
     */
    @Override
    protected void prepare(MaterialRecord row, MaterialRecord old) {
        if (!Set.of("旧铱坩埚", "旧铱杆", "铱块", "铱粉").contains(row.getMaterialType()))
            throw new ServiceException("物料类型无效");
    }
}
