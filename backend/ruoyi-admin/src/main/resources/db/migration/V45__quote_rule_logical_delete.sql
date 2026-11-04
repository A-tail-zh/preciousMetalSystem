ALTER TABLE metal_type_quote_rule
 ADD del_flag CHAR(1) NOT NULL DEFAULT '0' COMMENT '报价规则状态：0有效，2已删除',
 ADD CONSTRAINT chk_metal_quote_rule_del_flag CHECK(del_flag IN ('0','2'));
