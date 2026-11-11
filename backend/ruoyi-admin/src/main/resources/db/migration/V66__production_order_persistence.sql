CREATE TABLE metal_production_order_sequence (
 business_date DATE PRIMARY KEY,
 sequence_value INT NOT NULL DEFAULT 0,
 CHECK(sequence_value BETWEEN 0 AND 999999)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE metal_production_order (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 order_no VARCHAR(32) NOT NULL UNIQUE,
 factory_id BIGINT NOT NULL,
 customer_id BIGINT NULL,
 production_line_id BIGINT NOT NULL,
 owner_id BIGINT NOT NULL,
 processing_type VARCHAR(24) NOT NULL,
 incoming_weight DECIMAL(20,6) NULL,
 order_date DATE NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'CREATED',
 factory_name VARCHAR(100) NOT NULL,
 customer_name VARCHAR(100) NULL,
 line_name VARCHAR(100) NOT NULL,
 owner_name VARCHAR(100) NOT NULL,
 customer_snapshot JSON NULL,
 request_id CHAR(36) NOT NULL,
 request_actor BIGINT NOT NULL,
 request_hash CHAR(64) NOT NULL,
 create_by VARCHAR(64) NOT NULL,
 creator_name VARCHAR(100) NOT NULL,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_production_request(factory_id,request_actor,request_id),
 KEY idx_production_factory(factory_id,create_time,id),
 FOREIGN KEY(factory_id) REFERENCES metal_factory(factory_id),
 FOREIGN KEY(customer_id) REFERENCES metal_customer(customer_id),
 FOREIGN KEY(production_line_id) REFERENCES metal_production_line(production_line_id),
 FOREIGN KEY(owner_id) REFERENCES sys_user(user_id),
 FOREIGN KEY(request_actor) REFERENCES sys_user(user_id),
 CHECK(status='CREATED'),
 CHECK(processing_type IN ('MATERIAL_CUSTOM','DIRECT_CUSTOM','SHORTAGE_CUSTOM')),
 CHECK(incoming_weight IS NULL OR (processing_type='MATERIAL_CUSTOM' AND incoming_weight>=0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE metal_production_order_item (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 order_id BIGINT NOT NULL,
 sequence_no INT NOT NULL,
 product_id BIGINT NOT NULL,
 quantity INT NOT NULL,
 product_snapshot JSON NOT NULL,
 UNIQUE KEY uk_production_item(order_id,sequence_no),
 FOREIGN KEY(order_id) REFERENCES metal_production_order(id),
 FOREIGN KEY(product_id) REFERENCES metal_product(product_id),
 CHECK(quantity>0), CHECK(sequence_no>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE metal_production_order_step (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 item_id BIGINT NOT NULL,
 sequence_no INT NOT NULL,
 process_id BIGINT NOT NULL,
 process_name VARCHAR(100) NOT NULL,
 process_snapshot JSON NOT NULL,
 start_time DATETIME NULL,
 end_time DATETIME NULL,
 UNIQUE KEY uk_production_step(item_id,sequence_no),
 FOREIGN KEY(item_id) REFERENCES metal_production_order_item(id),
 FOREIGN KEY(process_id) REFERENCES metal_process(process_id),
 CHECK(sequence_no>0),
 CHECK(start_time IS NULL OR end_time IS NULL OR end_time>=start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
