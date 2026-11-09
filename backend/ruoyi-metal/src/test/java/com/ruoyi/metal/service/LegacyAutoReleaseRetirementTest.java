package com.ruoyi.metal.service;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import static org.junit.jupiter.api.Assertions.*;

class LegacyAutoReleaseRetirementTest {
    @Test void retainedRecoveryImplementationIsNotScheduled() throws Exception {
        assertNull(SemiAutoReleaseService.class.getMethod("recover").getAnnotation(Scheduled.class));
    }
}
