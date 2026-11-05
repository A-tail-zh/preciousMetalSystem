package com.ruoyi.metal.service;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class HandoverSourceTest {
    @Test void openSourceMayAdvanceOnlyWithinItsOwnWorkItemAndKeepsExternalReleaseRules() {
        var jdbc=mock(JdbcTemplate.class);
        var stock=new HashMap<String,Object>(Map.of("origin_handover_flow_id",1,"source_ready",true));
        var batch=new HashMap<String,Object>(Map.of("status","OPEN","handover_policy_version",2,"work_order_id",7,"item_id",8));
        assertTrue(HandoverSource.canEnter(jdbc,stock,batch,Map.of("work_order_id",7,"item_id",8)));
        assertFalse(HandoverSource.canEnter(jdbc,stock,batch,Map.of("work_order_id",9,"item_id",8)));
        assertFalse(HandoverSource.canEnter(jdbc,stock,batch,Map.of("work_order_id",7,"item_id",9)));
        batch.put("status","SETTLED");
        assertTrue(HandoverSource.canEnter(jdbc,stock,batch,Map.of("work_order_id",9,"item_id",10)));
        verifyNoInteractions(jdbc);
    }
    @Test void partialAcceptanceOpensProductionTransferButKeepsOtherOutboundSettlementRules() {
        var jdbc=mock(JdbcTemplate.class);
        var stock=new HashMap<String,Object>(Map.of("origin_handover_flow_id",1,"source_ready",true));
        var batch=new HashMap<String,Object>(Map.of("status","OPEN","handover_policy_version",2));
        assertTrue(HandoverSource.outboundReady(jdbc,stock,batch,"PRODUCTION_OUT"));
        for(String type:List.of("SEMI_OUT","PRODUCT_OUT","EXCHANGE_OUT"))assertFalse(HandoverSource.outboundReady(jdbc,stock,batch,type));
        batch.put("status","SETTLED");assertTrue(HandoverSource.outboundReady(jdbc,stock,batch,"SEMI_OUT"));
        stock.put("source_ready",false);assertFalse(HandoverSource.outboundReady(jdbc,stock,batch,"SEMI_OUT"));
        verifyNoInteractions(jdbc);
    }
    @Test void aThousandSegmentStocksUseOneQualificationReadIncludingNegativeResults() {
        var jdbc=mock(JdbcTemplate.class);
        var stocks=new ArrayList<Map<String,Object>>();
        var accepted=new ArrayList<Map<String,Object>>();
        for(long i=1;i<=1000;i++) {
            stocks.add(new HashMap<>(Map.of("inventory_id",i,"origin_batch_id",9L,"origin_handover_flow_id",i+1000)));
            if(i<1000)accepted.add(Map.of("inventory_id",i));
        }
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(accepted);
        HandoverSource.enrich(jdbc,stocks);
        var batch=new HashMap<String,Object>(Map.of("handover_policy_version",2,"status","OPEN"));
        for(int i=0;i<stocks.size();i++)assertEquals(i<999,HandoverSource.ready(jdbc,stocks.get(i),batch));
        HandoverSource.enrich(jdbc,stocks);
        verify(jdbc,times(1)).queryForList(anyString(),any(Object[].class));
        verifyNoMoreInteractions(jdbc);
    }
    @Test void legacySettlementAndNewSegmentAcceptanceKeepSeparateProofs() {
        var jdbc=mock(JdbcTemplate.class);
        assertTrue(HandoverSource.ready(jdbc,Map.of(),Map.of("status","SETTLED")));
        assertFalse(HandoverSource.ready(jdbc,Map.of(),Map.of("status","OPEN")));
        assertFalse(HandoverSource.ready(jdbc,Map.of(),Map.of("status","SETTLED","handover_policy_version",2)));
        var stock=new HashMap<String,Object>(Map.of("origin_handover_flow_id",1,"source_ready",true));
        assertFalse(HandoverSource.ready(jdbc,stock,Map.of("status","VOID","handover_policy_version",2)));
        assertFalse(HandoverSource.ready(jdbc,stock,Map.of("status","SETTLED")));
        verifyNoInteractions(jdbc);
    }
}
