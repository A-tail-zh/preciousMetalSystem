package com.ruoyi.metal.service;

import com.ruoyi.common.exception.ServiceException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OutputStageRulesTest {
    @Test void singleStepAcceptsFinished() { assertDoesNotThrow(() -> OutputStageRules.require("FINISHED_IN", 1, 1)); }
    @Test void singleStepRejectsSemi() { assertEquals("当前工序为最后一道工序，请选择成品入库。", assertThrows(ServiceException.class, () -> OutputStageRules.require("SEMI_IN", 1, 1)).getMessage()); }
    @Test void middleStepsAcceptSemi() { for (long step : new long[]{1, 2}) assertDoesNotThrow(() -> OutputStageRules.require("SEMI_IN", step, 3)); }
    @Test void middleStepRejectsFinished() { assertThrows(ServiceException.class, () -> OutputStageRules.require("FINISHED_IN", 2, 3)); }
    @Test void finalStepAcceptsFinishedButRejectsSemi() { assertDoesNotThrow(() -> OutputStageRules.require("FINISHED_IN", 3, 3)); assertThrows(ServiceException.class, () -> OutputStageRules.require("SEMI_IN", 3, 3)); }
    @Test void invalidSnapshotFailsClosed() { assertThrows(ServiceException.class, () -> OutputStageRules.require("SEMI_IN", 0, 0)); assertThrows(ServiceException.class, () -> OutputStageRules.require("FINISHED_IN", 3, 2)); }
}
