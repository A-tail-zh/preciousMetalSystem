ALTER TABLE metal_production_order
 ADD COLUMN version INT NOT NULL DEFAULT 0,
 ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0,
 ADD COLUMN update_by VARCHAR(64) NULL,
 ADD COLUMN update_time DATETIME NULL,
 ADD CONSTRAINT chk_production_deleted CHECK(deleted IN (0,1));
CREATE TABLE metal_production_order_operation (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 factory_id BIGINT NOT NULL,
 actor_id BIGINT NOT NULL,
 request_id CHAR(36) NOT NULL,
 action VARCHAR(8) NOT NULL,
 request_hash CHAR(64) NOT NULL,
 result JSON NOT NULL,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_production_operation(factory_id,actor_id,request_id),
 FOREIGN KEY(factory_id) REFERENCES metal_factory(factory_id),
 CHECK(action IN ('edit','remove'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
