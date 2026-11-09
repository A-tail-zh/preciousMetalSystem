package com.ruoyi.metal.service;

import java.util.Objects;

import org.springframework.stereotype.Service;
import com.ruoyi.metal.catalog.*;
import com.ruoyi.metal.mapper.MaterialMapper;
import com.ruoyi.metal.security.FactoryScope;
import com.ruoyi.common.exception.ServiceException;

/**
 * 物料业务服务，负责业务校验、数据处理和相关持久化操作。
 */
@Service
public class MaterialService extends FactoryCatalogService<MaterialRecord> {
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
        super.prepare(row,old);
        String type = TypeManagementService.validName(TypeManagementService.Kind.MATERIAL,row.getMaterialType());
        if (old == null || !Objects.equals(old.getMaterialType(),type)) {
            var names=jdbc.queryForList("SELECT name FROM metal_material_type WHERE factory_id=? AND name=? FOR UPDATE",String.class,row.getFactoryId(),type);
            if(names.isEmpty())throw new ServiceException("所选物料类型不在当前工厂目录中，请重新选择",400);
            type=names.get(0);
        }
        row.setMaterialType(type);
        scope.requireCatalog("metal",row.getMetalTypeId(),row.getFactoryId());
        if (row.getMetalTypeId() == null || row.getMetalTypeId() <= 0
                || jdbc.queryForList("SELECT metal_type_id FROM metal_type WHERE metal_type_id=?", row.getMetalTypeId()).isEmpty())
            throw new ServiceException("请选择有效金属类型");
        if (old != null && (!Objects.equals(old.getMaterialType(), type)
                || !Objects.equals(old.getMetalTypeId(), row.getMetalTypeId()))
                && WarehouseReferences.materialReferenced(jdbc, old.getId()))
            throw new ServiceException("物料已有库存、仓储或原料工单引用，不能修改物料类型或金属类型，请另建物料");
    }
}
