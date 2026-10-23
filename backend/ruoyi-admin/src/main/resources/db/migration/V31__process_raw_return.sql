-- Additive provenance only. No historical rows are rewritten or inferred.
CREATE TABLE metal_process_raw_return_line (
 line_id BIGINT NOT NULL PRIMARY KEY,
 batch_id BIGINT NOT NULL,
 inventory_id BIGINT NOT NULL,
 source_snapshot JSON NOT NULL,
 FOREIGN KEY(line_id) REFERENCES metal_warehouse_order_line(line_id),
 FOREIGN KEY(batch_id) REFERENCES metal_process_batch(batch_id),
 FOREIGN KEY(inventory_id) REFERENCES metal_inventory(inventory_id),
 UNIQUE KEY uq_process_raw_inventory(inventory_id),
 KEY idx_process_raw_batch(batch_id,line_id)
);
