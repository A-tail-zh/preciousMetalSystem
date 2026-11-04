ALTER TABLE metal_inventory
 ADD origin_recovery_line_id BIGINT NULL,
 ADD CONSTRAINT fk_inventory_recovery_origin FOREIGN KEY(origin_recovery_line_id) REFERENCES metal_warehouse_order_line(line_id);
