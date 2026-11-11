package com.ruoyi.metal.service;

import org.springframework.jdbc.core.JdbcTemplate;

/** Run within a transaction; preserve existing rules and frozen production snapshots. */
public final class ProcessRuleMigration {
    private ProcessRuleMigration() {}

    public static void migrate(JdbcTemplate jdbc) {
        String expected = "CASE t.type_name WHEN '酸洗' THEN 'ACID_WASH' WHEN '融定' THEN 'SMELTING' ELSE COALESCE(t.builtin_code,'NORMAL') END";
        int conflicts = jdbc.queryForObject("SELECT COUNT(*) FROM metal_process_type t JOIN metal_process_type b ON b.factory_id=t.factory_id AND b.type_id<>t.type_id AND b.builtin_code=CASE t.type_name WHEN '酸洗' THEN 'ACID_WASH' WHEN '融定' THEN 'SMELTING' END WHERE t.type_name IN ('酸洗','融定') AND t.builtin_code IS NULL", Integer.class);
        int invalid = jdbc.queryForObject("SELECT COUNT(*) FROM metal_process p LEFT JOIN metal_process_type t ON t.type_id=p.process_type AND t.factory_id=p.factory_id WHERE p.del_flag='0' AND p.factory_id IS NOT NULL AND (t.type_id IS NULL OR (p.semantic_code IS NOT NULL AND p.semantic_code<>" + expected + "))", Integer.class);
        if (conflicts != 0 || invalid != 0) throw new IllegalStateException("工序规则补齐已阻断：内置类型冲突" + conflicts + "条，工序类型或已有规则冲突" + invalid + "条；请先核对，不能覆盖已有规则");

        jdbc.update("UPDATE metal_process_type SET builtin_code=CASE type_name WHEN '酸洗' THEN 'ACID_WASH' ELSE 'SMELTING' END,version=version+1,update_by='process-rule-backfill',update_time=NOW() WHERE type_name IN ('酸洗','融定') AND builtin_code IS NULL AND factory_id IS NOT NULL");
        jdbc.update("UPDATE metal_process p JOIN metal_process_type t ON t.type_id=p.process_type AND t.factory_id=p.factory_id SET p.semantic_code=COALESCE(t.builtin_code,'NORMAL'),p.version=p.version+1,p.update_by='process-rule-backfill',p.update_time=NOW() WHERE p.del_flag='0' AND p.semantic_code IS NULL");
    }
}
