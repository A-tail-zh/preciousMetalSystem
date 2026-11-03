CREATE TABLE metal_outbound_submission (
 submission_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 request_id VARCHAR(36) NOT NULL,
 request_actor BIGINT NOT NULL,
 request_hash CHAR(64) NOT NULL,
 factory_id BIGINT NOT NULL,
 type VARCHAR(32) NOT NULL,
 order_date DATE NOT NULL,
 work_order_id BIGINT NOT NULL,
 item_id BIGINT NOT NULL,
 process_id BIGINT NOT NULL,
 batch_id BIGINT NOT NULL,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_outbound_request(request_id),
 KEY idx_outbound_factory(factory_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='多仓生产领料提交';
ALTER TABLE metal_warehouse_order ADD COLUMN outbound_submission_id BIGINT NULL COMMENT '出库提交编号',
 ADD UNIQUE KEY uk_outbound_submission_warehouse(outbound_submission_id,warehouse_id);
