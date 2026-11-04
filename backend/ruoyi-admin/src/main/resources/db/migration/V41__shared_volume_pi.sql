-- Preserve old per-metal values and historical snapshots; all new geometry uses this singleton.
CREATE TABLE metal_volume_rule (
 rule_id BIGINT NOT NULL PRIMARY KEY,
 pi_value DECIMAL(7,2) NOT NULL,
 version INT NOT NULL DEFAULT 0,
 update_by VARCHAR(64) NOT NULL DEFAULT '',
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT chk_volume_rule_singleton CHECK(rule_id=1),
 CONSTRAINT chk_volume_rule_pi CHECK(pi_value>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO metal_volume_rule(rule_id,pi_value,update_by)
 SELECT 1,COALESCE((SELECT pi_value FROM metal_type_quote_rule WHERE metal_type_id=1),
                  (SELECT ROUND(pi_value,2) FROM metal_quote_rule WHERE rule_id=1),3.14),'migration';
