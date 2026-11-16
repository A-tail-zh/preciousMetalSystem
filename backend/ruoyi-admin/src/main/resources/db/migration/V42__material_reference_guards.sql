-- Bound current-read reference checks to this material instead of scanning other stocks.
CREATE INDEX idx_inventory_material_reference ON metal_inventory(reference_id,category);
CREATE INDEX idx_warehouse_line_material_reference ON metal_warehouse_order_line(reference_id,category);

-- A shared metal name may contain 100 characters, followed by the material classification.
ALTER TABLE metal_inventory MODIFY name VARCHAR(160) NOT NULL COMMENT '品名快照';
ALTER TABLE metal_warehouse_order_line MODIFY name VARCHAR(160) NOT NULL COMMENT '品名快照';
ALTER TABLE metal_report_material MODIFY name VARCHAR(160) NOT NULL COMMENT '物料品名快照';
