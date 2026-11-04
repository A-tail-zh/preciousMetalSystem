package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.ruoyi.metal.catalog.WarehouseFlowRequests.Closeout;
import static org.junit.jupiter.api.Assertions.*;

class CloseoutJsonCompatibilityTest {
 @Test void oldConstructorKeepsTheExactOldJsonShape(){assertEquals("{\"requestId\":\"r\",\"version\":0,\"mode\":\"RETURN\",\"keeperId\":1,\"offsetWorkOrderId\":null,\"weight\":10,\"reason\":\"test\"}",WarehouseWorkflowService.json(new Closeout("r",0,"RETURN",1L,null,BigDecimal.TEN,"test")));}
 @Test void pieceMapOrderDoesNotChangeARequestFingerprint(){var a=new LinkedHashMap<Long,Long>();a.put(19L,1L);a.put(9L,2L);var b=new LinkedHashMap<Long,Long>();b.put(9L,2L);b.put(19L,1L);assertEquals(WarehouseWorkflowService.json(new Closeout("r",0,"RETURN",1L,null,BigDecimal.TEN,"test",a)),WarehouseWorkflowService.json(new Closeout("r",0,"RETURN",1L,null,BigDecimal.TEN,"test",b)));}
}
