ALTER TABLE metal_volume_rule
 ADD curved_bottom_factor DECIMAL(16,2) NOT NULL DEFAULT 1.03 COMMENT '公共弧底体积系数',
 ADD CONSTRAINT chk_volume_curved_bottom_factor CHECK(curved_bottom_factor>0);
