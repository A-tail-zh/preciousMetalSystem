package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import com.ruoyi.common.exception.ServiceException;

/** Decimal policy for new business values. Never normalize history while reading it. */
public final class BusinessDecimal {
    private BusinessDecimal() {}
    public static BigDecimal round(BigDecimal value) {
        if (value == null) return null;
        BigDecimal result = value.setScale(2, RoundingMode.HALF_UP);
        if (result.precision() - result.scale() > 14) throw new ServiceException("数值四舍五入后超过14位整数范围");
        return result;
    }
    public static BigDecimal positive(BigDecimal value, String label) {
        BigDecimal result = round(value);
        if (result == null || result.signum() <= 0) throw new ServiceException(label + "保留两位小数后须大于0");
        return result;
    }
    public static BigDecimal changed(BigDecimal value, BigDecimal previous) {
        return value != null && previous != null && value.compareTo(previous) == 0 ? previous : round(value);
    }
    public static String display(Object value) {
        return value == null ? "" : new BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
    public static void requireCompatibleBalance(BigDecimal value, String label) {
        if (value != null && value.compareTo(value.setScale(2, RoundingMode.HALF_UP)) != 0)
            throw new ServiceException(label + "存在历史高精度余额，请先人工核对；本次未修改历史账");
    }
}
