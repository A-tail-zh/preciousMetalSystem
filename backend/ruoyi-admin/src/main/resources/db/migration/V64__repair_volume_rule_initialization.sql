-- Restore the shared initialization parameters without replacing configured values.
INSERT INTO metal_volume_rule(rule_id,pi_value,curved_bottom_factor,update_by)
SELECT 1,3.14,1.03,'migration-v64'
WHERE NOT EXISTS (SELECT 1 FROM metal_volume_rule WHERE rule_id=1);

INSERT INTO metal_factory_volume_rule(factory_id,pi_value,curved_bottom_factor,update_by)
SELECT f.factory_id,r.pi_value,r.curved_bottom_factor,'migration-v64'
FROM metal_factory f CROSS JOIN metal_volume_rule r
WHERE f.del_flag='0' AND r.rule_id=1
AND NOT EXISTS (SELECT 1 FROM metal_factory_volume_rule g WHERE g.factory_id=f.factory_id);
