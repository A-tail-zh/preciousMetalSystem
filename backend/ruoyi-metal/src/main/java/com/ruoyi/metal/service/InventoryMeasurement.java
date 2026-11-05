package com.ruoyi.metal.service;

import java.util.*;
import com.alibaba.fastjson2.JSON;
import org.springframework.jdbc.core.JdbcTemplate;

/** RAW's historical PIECE column is meaningful only with a saved configurable product origin. */
public final class InventoryMeasurement {
    private InventoryMeasurement() {}

    public static String mode(Map<String, ?> row) {
        if ("RAW".equals(row.get("category")))
            return version(row.get("measurement_policy_version")) >= 3
                    && "PIECE".equals(row.get("effective_measurement_mode")) ? "PIECE" : "WEIGHT";
        return SemiMeasurementRules.mode(row.get("measurement_mode"));
    }

    public static boolean weight(Map<String, ?> row) {
        return "WEIGHT".equals(mode(row));
    }

    public static List<Map<String,Object>> enrich(JdbcTemplate jdbc, List<Map<String,Object>> rows) {
        HandoverSource.enrich(jdbc,rows);
        var stockIds = new TreeSet<Long>();
        var raw = new ArrayList<Map<String,Object>>();
        for (var row : rows) {
            if (row.get("category") == null) continue;
            if (!"RAW".equals(row.get("category"))) {
                row.put("effective_measurement_mode", mode(row));
                continue;
            }
            raw.add(row);
            row.put("effective_measurement_mode", "WEIGHT");
            row.put("measurement_policy_version", 0);
            Long stock = stockId(row);
            if (stock != null) stockIds.add(stock);
        }
        if (raw.isEmpty()) return rows;
        var origins = new HashMap<Long,Long>();
        if (!stockIds.isEmpty())
            for (var stock : jdbc.queryForList("SELECT inventory_id,origin_batch_id FROM metal_inventory WHERE inventory_id IN (" + placeholders(stockIds.size()) + ")", stockIds.toArray())) {
                Long origin = number(stock.get("origin_batch_id"));
                if (origin != null) origins.put(number(stock.get("inventory_id")), origin);
            }
        var batchIds = new TreeSet<Long>();
        for (var row : raw) {
            Long origin = origin(row, origins);
            if (origin != null) batchIds.add(origin);
        }
        var batches = new HashMap<Long,Map<String,Object>>();
        if (!batchIds.isEmpty())
            for (var batch : jdbc.queryForList("SELECT b.batch_id,b.measurement_mode,CAST(i.product_snapshot AS CHAR) product_snapshot FROM metal_process_batch b JOIN metal_work_order_item i ON i.item_id=b.item_id WHERE b.batch_id IN (" + placeholders(batchIds.size()) + ")", batchIds.toArray()))
                batches.put(number(batch.get("batch_id")), batch);
        for (var row : raw) {
            var batch = batches.get(origin(row, origins));
            if (batch == null) continue;
            int marker = snapshotVersion(batch.get("product_snapshot"));
            row.put("measurement_policy_version", marker);
            if (marker >= 3 && "PIECE".equals(batch.get("measurement_mode")))
                row.put("effective_measurement_mode", "PIECE");
        }
        return rows;
    }

    public static String outputMode(JdbcTemplate jdbc, Map<String, ?> batch, String category) {
        if (!"RAW".equals(category)) return SemiMeasurementRules.mode(batch.get("measurement_mode"));
        Object saved = null;
        if (batch.get("item_id") != null)
            saved = jdbc.queryForObject("SELECT CAST(product_snapshot AS CHAR) FROM metal_work_order_item WHERE item_id=?", String.class, batch.get("item_id"));
        else if (batch.get("batch_id") != null)
            saved = jdbc.queryForObject("SELECT CAST(i.product_snapshot AS CHAR) FROM metal_process_batch b JOIN metal_work_order_item i ON i.item_id=b.item_id WHERE b.batch_id=?", String.class, batch.get("batch_id"));
        return snapshotVersion(saved) >= 3 && "PIECE".equals(batch.get("measurement_mode")) ? "PIECE" : "WEIGHT";
    }

    private static String placeholders(int size) { return String.join(",", Collections.nCopies(size, "?")); }
    private static Long number(Object value) { return value == null ? null : Long.valueOf(value.toString()); }
    private static int version(Object value) { return value == null ? 0 : Integer.parseInt(value.toString()); }
    private static int snapshotVersion(Object value) {
        if (value == null) return 0;
        Map<?,?> snapshot = value instanceof Map<?,?> map ? map : JSON.parseObject(value.toString());
        return snapshot == null ? 0 : version(snapshot.get("measurementPolicyVersion"));
    }
    private static Long stockId(Map<String,?> row) {
        Object id = row.get("inventory_id");
        if (id == null) id = row.get("inventoryId");
        if (id != null) return number(id);
        if (row.get("origin_batch_id") != null || row.get("originBatchId") != null) return null;
        for (String key : List.of("line_id", "source_line_id", "source_type", "lineId", "sourceLineId", "sourceType"))
            if (row.containsKey(key)) return null;
        return number(row.get("id"));
    }
    private static Long origin(Map<String,?> row, Map<Long,Long> origins) {
        Long stock = stockId(row);
        return stock != null ? origins.get(stock) : number(row.get("origin_batch_id") != null ? row.get("origin_batch_id") : row.get("originBatchId"));
    }
}
