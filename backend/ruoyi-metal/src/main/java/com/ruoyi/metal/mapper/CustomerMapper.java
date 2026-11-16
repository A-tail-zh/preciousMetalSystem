package com.ruoyi.metal.mapper;

import com.ruoyi.metal.catalog.CustomerRecord;
import com.ruoyi.metal.catalog.MasterMapper;
import org.apache.ibatis.annotations.Param;

public interface CustomerMapper extends MasterMapper<CustomerRecord> {
    boolean hasActiveReferences(@Param("factoryId") Long factoryId, @Param("id") Long id);
}
