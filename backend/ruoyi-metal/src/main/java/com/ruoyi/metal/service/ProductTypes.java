package com.ruoyi.metal.service;

import java.util.List;
import com.ruoyi.common.exception.ServiceException;

/** Stable shape identity is independent of the metal name. */
public final class ProductTypes {
    public static final List<String> LEGACY = List.of("坩埚弧底", "坩埚平底", "坩埚梯形", "环", "杆实心", "杆空心");
    public static final List<String> ALL = List.of("平底坩埚", "环", "杆", "梯形", "弧底", "靶", "方形坩埚", "方形盖");
    private ProductTypes() {}
    public static String legacy(String category) {
        if (category != null && category.startsWith("铱") && LEGACY.contains(category.substring(1))) return category.substring(1);
        return null;
    }
    public static String require(String type) {
        if (!ALL.contains(type == null ? "" : type)) throw new ServiceException("请选择有效产品类型");
        return type;
    }
    public static String geometry(String type) {
        if (!LEGACY.contains(type == null ? "" : type)) throw new ServiceException("请选择有效旧版产品类型");
        return "铱" + type;
    }
}
