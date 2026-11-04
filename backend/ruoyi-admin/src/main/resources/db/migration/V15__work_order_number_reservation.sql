CREATE TABLE metal_work_order_number (
 request_id VARCHAR(36) NOT NULL PRIMARY KEY,
 actor_id BIGINT NOT NULL,
 order_no VARCHAR(32) NULL,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_work_order_reserved_number (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
