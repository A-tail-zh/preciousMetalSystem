ALTER TABLE metal_process_batch ADD COLUMN completion_policy VARCHAR(32) NOT NULL DEFAULT 'LEGACY' COMMENT '批次执行策略：历史/一次产出还料';
CREATE TABLE metal_batch_return_confirmation (
 confirmation_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 batch_id BIGINT NOT NULL,
 factory_id BIGINT NOT NULL,
 request_id VARCHAR(64) NOT NULL,
 request_hash VARCHAR(64) NOT NULL,
 mode VARCHAR(16) NOT NULL,
 physical_checked BOOLEAN NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
 actor_id BIGINT NOT NULL,
 reason VARCHAR(500),
 version INT NOT NULL DEFAULT 0,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_batch_return_batch(batch_id),
 UNIQUE KEY uk_batch_return_request(request_id),
 KEY idx_batch_return_factory(factory_id,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='一次全部还料或无余料确认';
CREATE TABLE metal_batch_return_document (
 confirmation_id BIGINT NOT NULL,
 order_id BIGINT NOT NULL,
 PRIMARY KEY(confirmation_id,order_id),
 UNIQUE KEY uk_batch_return_document(order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='一次还料确认生成的仓储单据';
