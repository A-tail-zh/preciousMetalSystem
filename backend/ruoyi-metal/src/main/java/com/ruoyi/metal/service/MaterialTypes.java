package com.ruoyi.metal.service;

import java.util.Map;
import java.util.Set;
import com.ruoyi.common.exception.ServiceException;

public final class MaterialTypes {
    private MaterialTypes() {}

    public static final Map<String, String> LEGACY = Map.of(
            "铱粉", "粉料", "铱块", "块料", "旧铱杆", "旧料", "旧铱坩埚", "旧料");

    public static String require(String value) {
        if (value == null) throw new ServiceException("请选择物料类型");
        String type = LEGACY.getOrDefault(value, value);
        if (!Set.of("粉料", "块料", "旧料").contains(type)) throw new ServiceException("物料类型无效");
        return type;
    }

    public static void requireProduction(Long metalTypeId) {
        if (!Long.valueOf(1).equals(metalTypeId))
            throw new ServiceException("其他金属物料暂不能用于生产领料或加工余料回收");
    }
}
