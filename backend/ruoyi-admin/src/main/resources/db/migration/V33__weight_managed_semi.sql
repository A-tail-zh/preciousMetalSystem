-- All existing rows retain PIECE semantics. No historical quantities are reinterpreted.
ALTER TABLE metal_product_process ADD measurement_mode VARCHAR(8) NOT NULL DEFAULT 'PIECE', ADD CHECK(measurement_mode IN ('PIECE','WEIGHT'));
ALTER TABLE metal_work_order_step ADD measurement_mode VARCHAR(8) NOT NULL DEFAULT 'PIECE', ADD weight_stage_closed BOOLEAN NOT NULL DEFAULT FALSE, ADD CHECK(measurement_mode IN ('PIECE','WEIGHT'));
ALTER TABLE metal_process_batch ADD measurement_mode VARCHAR(8) NOT NULL DEFAULT 'PIECE', ADD CHECK(measurement_mode IN ('PIECE','WEIGHT'));
ALTER TABLE metal_inventory ADD measurement_mode VARCHAR(8) NOT NULL DEFAULT 'PIECE', ADD reference_quantity BIGINT NULL, ADD reference_complete BOOLEAN NOT NULL DEFAULT TRUE, ADD CHECK(measurement_mode IN ('PIECE','WEIGHT')), ADD CHECK(reference_quantity IS NULL OR reference_quantity BETWEEN 1 AND 1000000);
ALTER TABLE metal_warehouse_order_line ADD measurement_mode VARCHAR(8) NOT NULL DEFAULT 'PIECE', ADD reference_quantity BIGINT NULL, ADD CHECK(measurement_mode IN ('PIECE','WEIGHT')), ADD CHECK(reference_quantity IS NULL OR reference_quantity BETWEEN 1 AND 1000000);
ALTER TABLE metal_semi_release DROP CHECK metal_semi_release_chk_1, ADD measurement_mode VARCHAR(8) NOT NULL DEFAULT 'PIECE', ADD reference_quantity BIGINT NULL, ADD CHECK((measurement_mode='PIECE' AND quantity>0 OR measurement_mode='WEIGHT' AND quantity=0) AND net_weight>0), ADD CHECK(measurement_mode IN ('PIECE','WEIGHT'));
ALTER TABLE metal_stock_ledger ADD reference_before BIGINT NULL, ADD reference_after BIGINT NULL, ADD reference_complete_before BOOLEAN NULL, ADD reference_complete_after BOOLEAN NULL;
CREATE TABLE metal_weight_stage_event (
 event_id BIGINT AUTO_INCREMENT PRIMARY KEY,item_id BIGINT NOT NULL,process_id BIGINT NOT NULL,
 request_id VARCHAR(36) NOT NULL UNIQUE,request_hash CHAR(64) NOT NULL,actor_id BIGINT NOT NULL,
 action VARCHAR(12) NOT NULL,reason VARCHAR(500) NOT NULL,create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(item_id) REFERENCES metal_work_order_item(item_id)
);
