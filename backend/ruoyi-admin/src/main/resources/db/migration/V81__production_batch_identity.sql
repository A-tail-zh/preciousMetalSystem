-- P1 adds identity only. Existing executions remain unmapped; no historical ledger rows are rewritten.
ALTER TABLE metal_production_order
 ADD UNIQUE KEY uk_production_order_factory_id(factory_id,id);
ALTER TABLE metal_production_order_item
 ADD UNIQUE KEY uk_production_item_order_id(order_id,id);

ALTER TABLE metal_production_execution_operation
 ADD COLUMN reason VARCHAR(500) NULL,
 ADD COLUMN reversal_of BIGINT NULL,
 ADD UNIQUE KEY uk_execution_operation_scope(factory_id,id),
 ADD UNIQUE KEY uk_execution_operation_reversal(factory_id,reversal_of);
-- Materialize the referenced key before adding the self-reference (MySQL 8.4).
ALTER TABLE metal_production_execution_operation
 ADD CONSTRAINT fk_execution_operation_reversal FOREIGN KEY(factory_id,reversal_of)
  REFERENCES metal_production_execution_operation(factory_id,id);

CREATE TABLE metal_production_batch (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 factory_id BIGINT NOT NULL,
 number VARCHAR(64) NOT NULL,
 order_id BIGINT NOT NULL,
 order_item_id BIGINT NOT NULL,
 route_snapshot JSON NOT NULL,
 planned_quantity BIGINT NULL,
 lifecycle VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
 origin_action VARCHAR(24) NOT NULL DEFAULT 'INITIAL',
 origin_operation_id BIGINT NULL,
 version INT NOT NULL DEFAULT 0,
 create_by BIGINT NOT NULL,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_production_batch_number(factory_id,number),
 UNIQUE KEY uk_production_batch_scope(factory_id,id),
 UNIQUE KEY uk_production_batch_order_item(factory_id,order_id,order_item_id,id),
 KEY idx_production_batch_list(factory_id,order_id,order_item_id,lifecycle,id),
 KEY idx_production_batch_origin(factory_id,origin_operation_id),
 CONSTRAINT fk_production_batch_order FOREIGN KEY(factory_id,order_id)
  REFERENCES metal_production_order(factory_id,id),
 CONSTRAINT fk_production_batch_item FOREIGN KEY(order_id,order_item_id)
  REFERENCES metal_production_order_item(order_id,id),
 CONSTRAINT fk_production_batch_creator FOREIGN KEY(create_by) REFERENCES sys_user(user_id),
 CONSTRAINT fk_production_batch_origin FOREIGN KEY(factory_id,origin_operation_id)
  REFERENCES metal_production_execution_operation(factory_id,id),
 CONSTRAINT chk_production_batch_plan CHECK(planned_quantity IS NULL OR planned_quantity BETWEEN 1 AND 2147483647),
 CONSTRAINT chk_production_batch_version CHECK(version>=0),
 CONSTRAINT chk_production_batch_number CHECK(CHAR_LENGTH(TRIM(number))>0),
 CONSTRAINT chk_production_batch_lifecycle CHECK(lifecycle IN ('ACTIVE','SPLIT','MERGED','COMPLETED','CANCELLED','EXCEPTION_TERMINATED')),
 CONSTRAINT chk_production_batch_origin CHECK(origin_action IN ('INITIAL','SPLIT','MERGE','RETURN_RESIDUAL','MIGRATION'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE metal_production_execution
 ADD COLUMN batch_id BIGINT NULL,
 ADD KEY idx_execution_batch(factory_id,batch_id,order_step_id,id),
 ADD CONSTRAINT fk_execution_production_batch FOREIGN KEY(factory_id,order_id,order_item_id,batch_id)
  REFERENCES metal_production_batch(factory_id,order_id,order_item_id,id);
