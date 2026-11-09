package com.ruoyi.framework.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;

class LegacyBusinessRetirementConfigTest {
    @Test void blocksReadsWritesAndExportsBeforeBusinessExecution() throws Exception {
        var guard = new LegacyBusinessRetirementConfig();
        for (String path : new String[]{"/metal/work-order", "/metal/work-order/1/partial-close",
                "/metal/warehouse/list", "/metal/warehouse-order/export", "/metal/inventory/1/adjust",
                "/metal/acid-wash/1/finish", "/metal/process-batch/1/confirm-return",
                "/metal/warehouse-flows/1/groups/1/confirm", "/metal/route-skip-permits"}) {
            for (String method : new String[]{"GET", "POST", "PUT", "DELETE"}) {
                var request = new MockHttpServletRequest(method, path);
                request.setServletPath(path);
                var response = new MockHttpServletResponse();
                assertFalse(guard.preHandle(request, response, new Object()), path);
                assertEquals(503, response.getStatus());
                assertTrue(response.getContentAsString().contains("该模块正在重构，暂不可用"));
            }
        }
    }

    @Test void leavesMasterDataQuotesUsersLogsAndSimilarNamesAccessible() throws Exception {
        var guard = new LegacyBusinessRetirementConfig();
        for (String path : new String[]{"/metal/factory/list", "/metal/product/list", "/metal/quotation/orders",
                "/system/user/list", "/audit/operations", "/metal/work-order-new", "/metal/work-report/list"}) {
            var request = new MockHttpServletRequest("GET", path);
            request.setServletPath(path);
            assertTrue(guard.preHandle(request, new MockHttpServletResponse(), new Object()), path);
        }
    }
}
