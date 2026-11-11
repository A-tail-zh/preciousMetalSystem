CREATE TABLE metal_storage_event (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, factory_id BIGINT NOT NULL, document_id BIGINT NOT NULL, line_id BIGINT NOT NULL,
 action VARCHAR(32) NOT NULL, request_hash CHAR(64) NOT NULL, result JSON NULL, reversed_by BIGINT NULL,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uq_storage_event(factory_id,document_id,line_id,action),
 FOREIGN KEY(factory_id) REFERENCES metal_factory(factory_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE metal_storage_stock (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, factory_id BIGINT NOT NULL, warehouse_id BIGINT NOT NULL,
 category VARCHAR(16) NOT NULL, material_id BIGINT NULL, product_id BIGINT NULL,
 identity_key CHAR(64) NOT NULL, identity_snapshot JSON NOT NULL, measurement_mode VARCHAR(8) NOT NULL,
 batch_no VARCHAR(100) NOT NULL, origin_order_id BIGINT NULL, origin_step_id BIGINT NULL, origin_document_id BIGINT NOT NULL, origin_line_id BIGINT NOT NULL,
 net_weight DECIMAL(20,2) NOT NULL DEFAULT 0, quantity BIGINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uq_storage_origin(factory_id,origin_document_id,origin_line_id), UNIQUE KEY uq_storage_stock_scope(factory_id,id),
 KEY ix_storage_stock_list(factory_id,category,warehouse_id,id),
 FOREIGN KEY(factory_id) REFERENCES metal_factory(factory_id), FOREIGN KEY(warehouse_id) REFERENCES metal_warehouse(warehouse_id),
 CHECK(category IN ('RAW','SEMI','PRODUCT')), CHECK(measurement_mode IN ('WEIGHT','PIECE')), CHECK(net_weight>=0), CHECK(quantity>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE metal_storage_ownership (
 factory_id BIGINT NOT NULL, stock_id BIGINT NOT NULL, owner_id BIGINT NOT NULL,
 weight DECIMAL(20,2) NOT NULL DEFAULT 0, quantity BIGINT NOT NULL DEFAULT 0,
 PRIMARY KEY(stock_id,owner_id), FOREIGN KEY(factory_id,stock_id) REFERENCES metal_storage_stock(factory_id,id),
 CHECK(owner_id>=0), CHECK(weight>=0), CHECK(quantity>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE metal_storage_entry (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, event_id BIGINT NOT NULL, factory_id BIGINT NOT NULL, stock_id BIGINT NOT NULL,
 owner_id BIGINT NULL, ledger VARCHAR(16) NOT NULL, weight_delta DECIMAL(20,2) NOT NULL, quantity_delta BIGINT NOT NULL,
 reason VARCHAR(64) NOT NULL, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(event_id) REFERENCES metal_storage_event(id), FOREIGN KEY(factory_id,stock_id) REFERENCES metal_storage_stock(factory_id,id),
 KEY ix_storage_entry(factory_id,stock_id,id), CHECK(ledger IN ('PHYSICAL','OWNERSHIP','LOSS'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE metal_storage_debt (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, factory_id BIGINT NOT NULL, event_id BIGINT NOT NULL, stock_id BIGINT NOT NULL,
 work_order_id BIGINT NULL, creditor_id BIGINT NOT NULL, debtor_id BIGINT NOT NULL, debt_kind VARCHAR(24) NOT NULL,
 identity_key CHAR(64) NOT NULL, identity_snapshot JSON NOT NULL, category VARCHAR(16) NOT NULL, measurement_mode VARCHAR(8) NOT NULL,
 original_weight DECIMAL(20,2) NOT NULL, original_quantity BIGINT NOT NULL DEFAULT 0,
 remaining_weight DECIMAL(20,2) NOT NULL, remaining_quantity BIGINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(factory_id,stock_id) REFERENCES metal_storage_stock(factory_id,id), FOREIGN KEY(event_id) REFERENCES metal_storage_event(id),
 KEY ix_storage_debt(factory_id,creditor_id,debtor_id,identity_key,id),
 CHECK(creditor_id<>debtor_id), CHECK(creditor_id=0 OR debtor_id=0),
 CHECK(original_weight>=0), CHECK(remaining_weight>=0), CHECK(original_quantity>=0), CHECK(remaining_quantity>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE metal_storage_debt_settlement (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, event_id BIGINT NOT NULL, factory_id BIGINT NOT NULL, debt_id BIGINT NOT NULL,
 weight DECIMAL(20,2) NOT NULL, quantity BIGINT NOT NULL DEFAULT 0, reason VARCHAR(24) NOT NULL,
 FOREIGN KEY(event_id) REFERENCES metal_storage_event(id), FOREIGN KEY(debt_id) REFERENCES metal_storage_debt(id),
 KEY ix_storage_settlement(factory_id,debt_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE metal_storage_issue_allocation (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, event_id BIGINT NOT NULL, factory_id BIGINT NOT NULL, document_id BIGINT NOT NULL, line_id BIGINT NOT NULL,
 stock_id BIGINT NOT NULL, work_order_id BIGINT NOT NULL, customer_id BIGINT NOT NULL, source_owner_id BIGINT NOT NULL,
 issued_weight DECIMAL(20,2) NOT NULL, issued_quantity BIGINT NOT NULL DEFAULT 0,
 received_weight DECIMAL(20,2) NOT NULL, received_quantity BIGINT NOT NULL DEFAULT 0,
 returned_weight DECIMAL(20,2) NOT NULL DEFAULT 0, returned_quantity BIGINT NOT NULL DEFAULT 0,
 advance_debt_id BIGINT NULL, borrow_debt_id BIGINT NULL, loss_bearer VARCHAR(16) NOT NULL,
 FOREIGN KEY(event_id) REFERENCES metal_storage_event(id), FOREIGN KEY(factory_id,stock_id) REFERENCES metal_storage_stock(factory_id,id),
 FOREIGN KEY(advance_debt_id) REFERENCES metal_storage_debt(id), FOREIGN KEY(borrow_debt_id) REFERENCES metal_storage_debt(id),
 KEY ix_storage_issue(factory_id,line_id,stock_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE metal_storage_warehouse_request (
 factory_id BIGINT NOT NULL, actor_id BIGINT NOT NULL, request_id CHAR(36) NOT NULL, request_hash CHAR(64) NOT NULL, result JSON NOT NULL,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(factory_id,actor_id,request_id), FOREIGN KEY(factory_id) REFERENCES metal_factory(factory_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE metal_storage_return_allocation (
 event_id BIGINT NOT NULL, issue_allocation_id BIGINT NOT NULL, weight DECIMAL(20,2) NOT NULL, quantity BIGINT NOT NULL,
 PRIMARY KEY(event_id,issue_allocation_id), FOREIGN KEY(event_id) REFERENCES metal_storage_event(id), FOREIGN KEY(issue_allocation_id) REFERENCES metal_storage_issue_allocation(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
