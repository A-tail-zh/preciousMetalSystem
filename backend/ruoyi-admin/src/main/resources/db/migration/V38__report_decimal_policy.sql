-- Preserve the original validation precision of historical reports.
ALTER TABLE metal_work_report ADD numeric_policy_version TINYINT NOT NULL DEFAULT 1 COMMENT '1=历史六位口径，2=新业务两位口径';
