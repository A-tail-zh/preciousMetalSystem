ALTER TABLE metal_inventory
 ADD customer_id BIGINT NULL COMMENT '原材料客户档案',
 ADD raw_customer_key VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin
 GENERATED ALWAYS AS (CASE WHEN category='RAW' THEN CASE WHEN customer_id IS NULL THEN CONCAT('L:',COALESCE(TRIM(organization),'')) ELSE CONCAT('C:',customer_id) END ELSE '' END) STORED,
 ADD CONSTRAINT fk_inventory_customer FOREIGN KEY(customer_id) REFERENCES metal_customer(customer_id),
 ADD UNIQUE KEY uq_inventory_customer_source(factory_id,warehouse_id,category,reference_id,batch_no,raw_customer_key,ownership_status);
ALTER TABLE metal_inventory DROP INDEX uq_inventory_source;
ALTER TABLE metal_warehouse_order_line
 ADD customer_id BIGINT NULL COMMENT '过账时原材料客户档案，名称保留于organization',
 ADD CONSTRAINT fk_warehouse_line_customer FOREIGN KEY(customer_id) REFERENCES metal_customer(customer_id);
