package com.ruoyi.metal.service;

import com.ruoyi.common.exception.ServiceException;

/** 生产产出类型以工单工序快照为准，保存与导入共用。 */
final class OutputStageRules {
    private OutputStageRules() {}

    static void require(String type, long sequence, long lastSequence) {
        if (sequence < 1 || lastSequence < sequence) throw new ServiceException("工单工序快照无效，请核对工艺路线");
        if ("SEMI_IN".equals(type) && sequence == lastSequence)
            throw new ServiceException("当前工序为最后一道工序，请选择成品入库。");
        if ("FINISHED_IN".equals(type) && sequence != lastSequence)
            throw new ServiceException("当前工序不是最后一道工序，请选择半成品入库。");
    }
}
