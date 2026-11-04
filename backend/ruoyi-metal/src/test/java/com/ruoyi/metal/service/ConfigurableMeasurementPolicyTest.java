package com.ruoyi.metal.service;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ConfigurableMeasurementPolicyTest {
    private boolean configurable(Map<String,?> snapshot) {
        return MeasurementPolicy.configurable(snapshot);
    }

    private boolean configurable(JdbcTemplate jdbc,Map<String,?> target) {
        return MeasurementPolicy.configurable(jdbc,target);
    }

    @Test void versionTwoRetainsCurrentRulesWithoutOptingIntoConfigurableRoutes() throws Exception {
        assertTrue(MeasurementPolicy.current(Map.of("measurementPolicyVersion",2)));
        assertFalse(configurable(Map.of("measurementPolicyVersion",2)));
        assertFalse(configurable(Map.of("measurementPolicyVersion",1)));
        assertFalse(configurable(Map.of()));
        assertTrue(configurable(Map.of("measurementPolicyVersion",3)));
        assertTrue(configurable(Map.of("measurementPolicyVersion",4)));
    }

    @Test void configurableReadsSavedMapOrJsonBeforeLookingAtTheDatabase() throws Exception {
        var jdbc=mock(JdbcTemplate.class);
        assertTrue(configurable(jdbc,Map.of("product_snapshot",Map.of("measurementPolicyVersion",3))));
        assertFalse(configurable(jdbc,Map.of("product_snapshot","{\"measurementPolicyVersion\":2}")));
        verifyNoInteractions(jdbc);
    }

    @Test void missingInlineSnapshotUsesTheSavedItemRatherThanCurrentProductConfiguration() throws Exception {
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(),eq(String.class),eq(11L))).thenReturn("{\"measurementPolicyVersion\":3}");
        assertTrue(configurable(jdbc,Map.of("item_id",11L)));
        assertFalse(configurable(jdbc,Map.of()));
    }
}
