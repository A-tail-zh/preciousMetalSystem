package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.List;

import com.ruoyi.common.exception.ServiceException;

/**
 * Snapshot-driven unit rules. Optional reference counts never grant production capacity.
 */
public final class SemiMeasurementRules {
    private SemiMeasurementRules() {
    }

    public static String mode(Object v) {
        String m = v == null ? "PIECE" : v.toString();
        if (!m.equals("PIECE") && !m.equals("WEIGHT")) throw new ServiceException("请选择按件或按重量管理");
        return m;
    }

    public static boolean weight(Map<String, ?> row) {
        return row != null && "WEIGHT".equals(row.get("measurement_mode"));
    }

    public static Long reference(Long v) {
        if (v != null && (v < 1 || v > 1000000)) throw new ServiceException("参考件数须为1至1000000的整数，可不填写");
        return v;
    }

    public static void requireWeight(BigDecimal available, BigDecimal requested) {
        if (requested == null || requested.signum() <= 0 || requested.scale() > 6 || requested.compareTo(available) > 0)
            throw new ServiceException("净重须大于0、最多6位小数且不能超过可用净重");
    }

    public static void requireIntermediateInput(long sequence, BigDecimal semi) {
        if (sequence > 1 && (semi == null || semi.signum() <= 0))
            throw new ServiceException("后续工序须实际领入符合路线的上游半成品，不能只领原材料加工");
    }

    public static boolean stockConsistent(Map<String, ?> stock) {
        BigDecimal mass = new BigDecimal(String.valueOf(stock.get("net_weight")));
        long q = Long.parseLong(String.valueOf(stock.get("quantity")));
        return mass.signum() >= 0 && q >= 0 && (weight(stock) || ((q == 0) == (mass.signum() == 0)));
    }

    public static boolean pieceCreditApplies(List<String> modes, long sequence, long target) {
        if (sequence < 1 || target <= sequence || target > modes.size()) return false;
        for (int i = (int) sequence - 1; i < target - 1; i++) if ("WEIGHT".equals(modes.get(i))) return false;
        return true;
    }

    public static Long referenceBalance(Long old, Long change, boolean subtract, boolean empty) {
        if (empty || old == null || change == null) return null;
        long n = subtract ? old - change : old + change;
        return n < 1 || n > 1000000 ? null : n;
    }
}
