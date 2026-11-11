ALTER TABLE metal_material ADD COLUMN measurement_mode VARCHAR(8) NULL,
 ADD COLUMN material_form VARCHAR(16) NULL,
 ADD CONSTRAINT chk_material_storage_measurement CHECK(measurement_mode IS NULL OR measurement_mode IN ('PIECE','WEIGHT')),
 ADD CONSTRAINT chk_material_storage_form CHECK(material_form IS NULL OR material_form IN ('POWDER','UNTREATED','METAL'));
ALTER TABLE metal_process ADD COLUMN semantic_code VARCHAR(16) NULL,
 ADD CONSTRAINT chk_process_semantic CHECK(semantic_code IS NULL OR semantic_code IN ('NORMAL','ACID_WASH','SMELTING'));
ALTER TABLE metal_product_process ADD COLUMN output_category VARCHAR(12) NULL,
 ADD COLUMN output_form VARCHAR(16) NULL,
 ADD COLUMN output_material_id BIGINT NULL,
 ADD CONSTRAINT fk_product_process_output_material FOREIGN KEY(output_material_id) REFERENCES metal_material(material_id),
 ADD CONSTRAINT chk_product_process_output CHECK(output_category IS NULL OR output_category IN ('RAW','SEMI','PRODUCT'));
SET @production_status_constraint=(SELECT tc.CONSTRAINT_NAME FROM information_schema.TABLE_CONSTRAINTS tc JOIN information_schema.CHECK_CONSTRAINTS cc ON cc.CONSTRAINT_SCHEMA=tc.CONSTRAINT_SCHEMA AND cc.CONSTRAINT_NAME=tc.CONSTRAINT_NAME WHERE tc.TABLE_SCHEMA=DATABASE() AND tc.TABLE_NAME='metal_production_order' AND tc.CONSTRAINT_TYPE='CHECK' AND cc.CHECK_CLAUSE LIKE '%status%' LIMIT 1);
SET @production_status_sql=IF(@production_status_constraint IS NULL,'SELECT 1',CONCAT('ALTER TABLE metal_production_order DROP CHECK `',REPLACE(@production_status_constraint,'`','``'),'`'));
PREPARE production_status_stmt FROM @production_status_sql;
EXECUTE production_status_stmt;
DEALLOCATE PREPARE production_status_stmt;
ALTER TABLE metal_production_order ADD COLUMN flow_version INT NOT NULL DEFAULT 0,
 ADD COLUMN first_issue_confirmed_at DATETIME NULL,
 ADD COLUMN completed_at DATETIME NULL,
 ADD COLUMN arrivals_snapshot JSON NULL,
 ADD CONSTRAINT chk_production_flow_status CHECK(status IN ('CREATED','IN_PROGRESS','COMPLETED','EXCEPTION_COMPLETED'));
ALTER TABLE metal_production_order_step ADD COLUMN semantic_code VARCHAR(16) NULL,
 ADD COLUMN output_category VARCHAR(12) NULL, ADD COLUMN output_form VARCHAR(16) NULL,
 ADD COLUMN output_material_id BIGINT NULL, ADD COLUMN measurement_mode VARCHAR(8) NULL,
 ADD COLUMN execution_closed BOOLEAN NOT NULL DEFAULT FALSE;
CREATE TABLE metal_production_execution (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, factory_id BIGINT NOT NULL, order_id BIGINT NOT NULL,
 order_item_id BIGINT NOT NULL, order_step_id BIGINT NOT NULL, processor_id BIGINT NOT NULL,
 status VARCHAR(12) NOT NULL DEFAULT 'OPEN', version INT NOT NULL DEFAULT 0,
 planned_quantity BIGINT NULL, input_weight DECIMAL(20,2) NOT NULL DEFAULT 0,
 output_weight DECIMAL(20,2) NOT NULL DEFAULT 0, returned_weight DECIMAL(20,2) NOT NULL DEFAULT 0,
 recovered_weight DECIMAL(20,2) NOT NULL DEFAULT 0, loss_weight DECIMAL(20,2) NULL,
 output_quantity BIGINT NOT NULL DEFAULT 0, input_quantity BIGINT NOT NULL DEFAULT 0,
 inherited_snapshot JSON NULL, close_reason VARCHAR(500) NULL,
 create_by BIGINT NOT NULL, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, closed_at DATETIME NULL,
 KEY idx_execution_order(factory_id,order_id,order_item_id,order_step_id),
 FOREIGN KEY(factory_id) REFERENCES metal_factory(factory_id), FOREIGN KEY(order_id) REFERENCES metal_production_order(id),
 FOREIGN KEY(order_item_id) REFERENCES metal_production_order_item(id), FOREIGN KEY(order_step_id) REFERENCES metal_production_order_step(id),
 FOREIGN KEY(processor_id) REFERENCES sys_user(user_id), FOREIGN KEY(create_by) REFERENCES sys_user(user_id),
 CHECK(status IN ('OPEN','CLOSED')), CHECK(input_weight>=0 AND output_weight>=0 AND returned_weight>=0 AND recovered_weight>=0),
 CHECK(loss_weight IS NULL OR loss_weight>=0), CHECK(output_quantity>=0 AND input_quantity>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE metal_production_execution_event (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,factory_id BIGINT NOT NULL,execution_id BIGINT NOT NULL,
 document_id BIGINT NOT NULL,line_id BIGINT NOT NULL,event_type VARCHAR(12) NOT NULL,
 stock_id BIGINT NULL,source_execution_id BIGINT NULL,weight DECIMAL(20,2) NOT NULL,quantity BIGINT NOT NULL DEFAULT 0,
 customer_weight DECIMAL(20,2) NOT NULL DEFAULT 0,factory_weight DECIMAL(20,2) NOT NULL DEFAULT 0,
 reversed BOOLEAN NOT NULL DEFAULT FALSE,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_execution_post(document_id,line_id,event_type),KEY idx_execution_event(execution_id,event_type),
 FOREIGN KEY(execution_id) REFERENCES metal_production_execution(id),FOREIGN KEY(document_id) REFERENCES metal_storage_document(id),
 FOREIGN KEY(line_id) REFERENCES metal_storage_document_line(id),FOREIGN KEY(factory_id) REFERENCES metal_factory(factory_id),
 CHECK(event_type IN ('INPUT','OUTPUT','RETURN','RECOVERY')),CHECK(weight>=0 AND quantity>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE metal_production_execution_operation (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,factory_id BIGINT NOT NULL,actor_id BIGINT NOT NULL,request_id CHAR(36) NOT NULL,
 action VARCHAR(64) NOT NULL,request_hash CHAR(64) NOT NULL,result JSON NOT NULL,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_execution_request(factory_id,actor_id,request_id),FOREIGN KEY(factory_id) REFERENCES metal_factory(factory_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE metal_production_exception_close (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,factory_id BIGINT NOT NULL,order_id BIGINT NOT NULL,applicant_id BIGINT NOT NULL,
 reviewer_id BIGINT NOT NULL,reason VARCHAR(500) NOT NULL,status VARCHAR(12) NOT NULL DEFAULT 'PENDING',version INT NOT NULL DEFAULT 0,
 summary_snapshot JSON NOT NULL,review_remark VARCHAR(500) NULL,create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,review_time DATETIME NULL,
 KEY idx_production_close_task(factory_id,reviewer_id,status),
 FOREIGN KEY(order_id) REFERENCES metal_production_order(id),FOREIGN KEY(applicant_id) REFERENCES sys_user(user_id),FOREIGN KEY(reviewer_id) REFERENCES sys_user(user_id),
 CHECK(applicant_id<>reviewer_id),CHECK(status IN ('PENDING','APPROVED','REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
