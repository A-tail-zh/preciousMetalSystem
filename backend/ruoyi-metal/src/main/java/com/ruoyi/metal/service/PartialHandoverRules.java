package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import com.ruoyi.common.exception.ServiceException;

final class PartialHandoverRules {
    private PartialHandoverRules() {}
    static BigDecimal consumed(BigDecimal before, BigDecimal remaining, BigDecimal delivered) {
        if (remaining.signum() < 0 || delivered.signum() < 0 || remaining.add(delivered).compareTo(before) > 0)
            throw new ServiceException("本次产出、实际还料及剩余在制合计超过本人在制净重");
        return before.subtract(remaining);
    }
    static List<BigDecimal> proportion(BigDecimal total, List<BigDecimal> weights) {
        BigDecimal basis = weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() < 0 || total.compareTo(basis) > 0) throw new ServiceException("在制分配超过来源余额");
        var result = new ArrayList<BigDecimal>();
        BigDecimal allocated = BigDecimal.ZERO, cumulative = BigDecimal.ZERO;
        for (var weight : weights) {
            cumulative = cumulative.add(weight);
            BigDecimal next = basis.signum() == 0 ? BigDecimal.ZERO.setScale(6)
                : total.multiply(cumulative).divide(basis, 2, RoundingMode.DOWN).setScale(6);
            result.add(next.subtract(allocated));
            allocated = next;
        }
        return result;
    }
}
