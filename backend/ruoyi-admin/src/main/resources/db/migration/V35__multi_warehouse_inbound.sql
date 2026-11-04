CREATE TABLE metal_inbound_submission (
 submission_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 request_id VARCHAR(36) NOT NULL,
 request_actor BIGINT NOT NULL,
 request_hash CHAR(64) NOT NULL,
 factory_id BIGINT NOT NULL,
 type VARCHAR(32) NOT NULL,
 order_date DATE NOT NULL,
 work_order_id BIGINT NULL,
 item_id BIGINT NULL,
 process_id BIGINT NULL,
 batch_id BIGINT NULL,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_inbound_request(request_id),
 KEY idx_inbound_factory(factory_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='多仓入库原始提交关联';
ALTER TABLE metal_warehouse_order ADD COLUMN inbound_submission_id BIGINT NULL COMMENT '入库提交编号',
 ADD UNIQUE KEY uk_inbound_submission_warehouse(inbound_submission_id,warehouse_id);
