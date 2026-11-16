package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.util.UUID;
import com.ruoyi.common.exception.ServiceException;

/** Pure invariants used by reservations, individual receipts and final batch settlement. */
public final class WarehouseFlowRules {
    public static final String POLICY = "DUAL_WEIGH_HANDOVER";
    private WarehouseFlowRules() {}

    public static void requestId(String value) {
        try {
            if (!UUID.fromString(value).toString().equals(value)) throw new IllegalArgumentException();
        } catch (Exception e) { throw new ServiceException("请求编号须为UUID"); }
    }

    public static BigDecimal weight(BigDecimal value) {
        if (value == null || value.signum() < 0) throw new ServiceException("请填写合法净重");
        BigDecimal result = BusinessDecimal.round(value);
        if (result.signum() <= 0) throw new ServiceException("净重保留两位小数后须大于0");
        return result;
    }

    public static void difference(BigDecimal declared, BigDecimal actual, String reason) {
        if (reason != null && reason.length() > 500) throw new ServiceException("误差备注最多500字");
        if (declared.compareTo(actual) != 0 && (reason == null || reason.isBlank()))
            throw new ServiceException("每条重量差异都须填写误差备注，不能用其他行抵消");
    }

    public static void available(BigDecimal book, long pieces, BigDecimal held, long heldPieces,
                                 BigDecimal requested, long requestedPieces, boolean byWeight) {
        if (held.signum() < 0 || heldPieces < 0 || requested.signum() < 0 || requestedPieces < 0
                || requested.add(held).compareTo(book) > 0 || !byWeight && requestedPieces + heldPieces > pieces)
            throw new ServiceException("库存可用量不足，不能占用或扣减其他待出库单的余额");
    }

    public record Mass(BigDecimal issued, BigDecimal netInput, BigDecimal processorOutput,
                       BigDecimal processorReturns, BigDecimal loss, BigDecimal received,
                       BigDecimal handoverDifference) {}

    public static Mass mass(BigDecimal issued, BigDecimal processorOutput,
                            BigDecimal processorReturns, BigDecimal keeperReceived) {
        if (issued == null || processorOutput == null || processorReturns == null || keeperReceived == null
                || issued.signum() <= 0 || processorOutput.signum() <= 0
                || processorReturns.signum() < 0 || keeperReceived.signum() <= 0)
            throw new ServiceException("加工交接重量资料不完整或无实际产出");
        BigDecimal input = issued.subtract(processorReturns), loss = input.subtract(processorOutput);
        if (input.signum() <= 0 || loss.signum() < 0)
            throw new ServiceException("实际加工投入须大于0，产出和余料不能超过实领；不能用备注绕过负损耗");
        BigDecimal difference = processorOutput.add(processorReturns).subtract(keeperReceived);
        if (keeperReceived.add(loss).add(difference).compareTo(issued) != 0)
            throw new ServiceException("加工及交接重量不闭合");
        return new Mass(issued,input,processorOutput,processorReturns,loss,keeperReceived,difference);
    }

    public static String newOwner(String owner, Long customer) {
        if (owner == null || owner.isBlank()) {
            if (customer != null) return "CUSTOMER";
            throw new ServiceException("请选择客户料或工厂自有料，未关联客户的历史库存不能自动作为厂料");
        }
        if (!"CUSTOMER".equals(owner) && !"FACTORY".equals(owner))
            throw new ServiceException("新增原料必须明确客户料或工厂自有料");
        if ("FACTORY".equals(owner) && customer != null)
            throw new ServiceException("工厂自有料不能同时选择客户");
        if ("CUSTOMER".equals(owner) && customer == null)
            throw new ServiceException("客户料必须选择有效同厂客户");
        return owner;
    }
}
