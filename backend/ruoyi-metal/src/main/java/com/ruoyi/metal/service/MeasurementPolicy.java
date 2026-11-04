package com.ruoyi.metal.service;

import java.util.Map;
import com.alibaba.fastjson2.JSON;
import org.springframework.jdbc.core.JdbcTemplate;

/** Missing markers deliberately retain the saved order's legacy measurement rules. */
final class MeasurementPolicy {
    static final int VERSION = 2;
    private MeasurementPolicy() {}

    static boolean current(Map<String, ?> snapshot) {
        Object value = snapshot.get("measurementPolicyVersion");
        return value != null && Integer.parseInt(value.toString()) >= VERSION;
    }

    static boolean current(JdbcTemplate jdbc, Map<String, ?> target) {
        Object saved = target.get("product_snapshot");
        if (saved == null && target.get("item_id") != null)
            saved = jdbc.queryForObject("SELECT CAST(COALESCE(material_snapshot,product_snapshot) AS CHAR) FROM metal_work_order_item WHERE item_id=?", String.class, target.get("item_id"));
        if (saved instanceof Map<?, ?> snapshot)
            return current((Map<String, ?>) snapshot);
        return saved != null && current(JSON.parseObject(saved.toString()));
    }
}
