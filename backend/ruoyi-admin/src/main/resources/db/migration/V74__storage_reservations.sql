CREATE TABLE metal_storage_reservation_session (
 session_id CHAR(36) PRIMARY KEY,
 factory_id BIGINT NOT NULL,
 actor_id BIGINT NOT NULL,
 document_id BIGINT NULL,
 version INT NOT NULL DEFAULT 0,
 status VARCHAR(16) NOT NULL DEFAULT 'TEMP',
 expires_at DATETIME NOT NULL,
 context_hash CHAR(64) NOT NULL,
 data JSON NOT NULL,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY idx_storage_reservation_document(factory_id,document_id,status),
 KEY idx_storage_reservation_active(factory_id,status,expires_at),
 FOREIGN KEY(factory_id) REFERENCES metal_factory(factory_id),
 FOREIGN KEY(actor_id) REFERENCES sys_user(user_id),
 FOREIGN KEY(factory_id,document_id) REFERENCES metal_storage_document(factory_id,id),
 CHECK(status IN ('TEMP','BOUND','RELEASED','CONSUMED')),
 CHECK(version>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE metal_storage_reservation (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 session_id CHAR(36) NOT NULL,
 factory_id BIGINT NOT NULL,
 reservation_line_id CHAR(36) NOT NULL,
 document_line_id BIGINT NULL,
 stock_id BIGINT NOT NULL,
 owner_id BIGINT NOT NULL,
 weight DECIMAL(20,2) NOT NULL,
 quantity INT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_storage_reservation_share(session_id,reservation_line_id,owner_id),
 KEY idx_storage_reservation_stock(factory_id,stock_id,owner_id,session_id),
 FOREIGN KEY(session_id) REFERENCES metal_storage_reservation_session(session_id),
 FOREIGN KEY(factory_id,stock_id) REFERENCES metal_storage_stock(factory_id,id),
 -- Draft/returned lines are recreated on save; stable reservation_line_id preserves their identity.
 CHECK(weight>0 AND quantity>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE metal_storage_reservation_operation (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 factory_id BIGINT NOT NULL,
 actor_id BIGINT NOT NULL,
 request_id CHAR(36) NOT NULL,
 session_id CHAR(36) NOT NULL,
 action VARCHAR(16) NOT NULL,
 request_hash CHAR(64) NOT NULL,
 result JSON NOT NULL,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_storage_reservation_request(factory_id,actor_id,request_id),
 FOREIGN KEY(session_id) REFERENCES metal_storage_reservation_session(session_id),
 FOREIGN KEY(factory_id) REFERENCES metal_factory(factory_id),
 FOREIGN KEY(actor_id) REFERENCES sys_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
