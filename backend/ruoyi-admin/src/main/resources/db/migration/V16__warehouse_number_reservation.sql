CREATE TABLE metal_warehouse_number (
 request_id VARCHAR(36) NOT NULL PRIMARY KEY,
 actor_id BIGINT NOT NULL,
 kind VARCHAR(8) NOT NULL,
 order_no VARCHAR(32) NULL,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_warehouse_reserved_number (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
