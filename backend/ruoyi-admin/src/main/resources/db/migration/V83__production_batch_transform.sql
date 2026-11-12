-- Identity transfers do not change the physical stock, ownership or debt ledgers.
ALTER TABLE metal_production_batch ADD COLUMN completed_step_ids JSON NULL;
ALTER TABLE metal_storage_issue_allocation ADD UNIQUE KEY uk_issue_allocation_factory_id(factory_id,id);
ALTER TABLE metal_production_execution
 MODIFY COLUMN status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
 DROP CHECK chk_production_execution_status,
 ADD CONSTRAINT chk_production_execution_status CHECK(status IN ('OPEN','CLOSED','CANCELLED','SPLIT_TRANSFERRED')),
 ADD UNIQUE KEY uk_execution_factory_id(factory_id,id),
 ADD COLUMN carry_in_weight DECIMAL(20,2) NOT NULL DEFAULT 0,
 ADD COLUMN carry_out_weight DECIMAL(20,2) NOT NULL DEFAULT 0,
 ADD COLUMN carry_in_quantity BIGINT NOT NULL DEFAULT 0,
 ADD COLUMN carry_out_quantity BIGINT NOT NULL DEFAULT 0,
 ADD CONSTRAINT chk_execution_carry CHECK(carry_in_weight>=0 AND carry_out_weight>=0 AND carry_in_quantity>=0 AND carry_out_quantity>=0);

CREATE TABLE metal_production_batch_edge (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, factory_id BIGINT NOT NULL, operation_id BIGINT NOT NULL,
 source_batch_id BIGINT NOT NULL,target_batch_id BIGINT NOT NULL,
 planned_quantity_part BIGINT NULL,weight DECIMAL(20,2) NOT NULL DEFAULT 0,quantity BIGINT NULL,
 measurement_mode VARCHAR(8) NOT NULL,allocation_basis VARCHAR(32) NOT NULL,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY idx_batch_edge_source(factory_id,source_batch_id,id), KEY idx_batch_edge_target(factory_id,target_batch_id,id),
 FOREIGN KEY(factory_id,operation_id) REFERENCES metal_production_execution_operation(factory_id,id),
 FOREIGN KEY(factory_id,source_batch_id) REFERENCES metal_production_batch(factory_id,id),
 FOREIGN KEY(factory_id,target_batch_id) REFERENCES metal_production_batch(factory_id,id),
 CHECK(source_batch_id<>target_batch_id AND weight>=0 AND (quantity IS NULL OR quantity>=0)),
 CHECK(measurement_mode IN ('PIECE','WEIGHT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Initial provenance is immutable; only the remaining responsibility is consumed.
CREATE TABLE metal_production_material_source (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, factory_id BIGINT NOT NULL,
 stock_slice_id BIGINT NULL, execution_id BIGINT NULL,
 source_allocation_id BIGINT NULL,root_issue_allocation_id BIGINT NULL,
 current_owner_id BIGINT NOT NULL,source_owner_id BIGINT NOT NULL,source_kind VARCHAR(16) NOT NULL,
 initial_weight DECIMAL(20,2) NOT NULL,initial_quantity BIGINT NULL,
 weight DECIMAL(20,2) NOT NULL,quantity BIGINT NULL,
 returnable_weight DECIMAL(20,2) NOT NULL DEFAULT 0,returnable_quantity BIGINT NOT NULL DEFAULT 0,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_material_source_scope(factory_id,id),
 KEY idx_material_source_slice(factory_id,stock_slice_id,id), KEY idx_material_source_execution(factory_id,execution_id,id),
 FOREIGN KEY(factory_id,stock_slice_id) REFERENCES metal_storage_batch_slice(factory_id,id),
 FOREIGN KEY(factory_id,execution_id) REFERENCES metal_production_execution(factory_id,id),
 FOREIGN KEY(factory_id,source_allocation_id) REFERENCES metal_production_material_source(factory_id,id),
 FOREIGN KEY(factory_id,root_issue_allocation_id) REFERENCES metal_storage_issue_allocation(factory_id,id),
 CHECK((stock_slice_id IS NULL)<>(execution_id IS NULL)),
 CHECK(source_kind IN ('CUSTOMER','FACTORY')),
 CHECK(initial_weight>=0 AND weight>=0 AND (initial_quantity IS NULL OR initial_quantity>=0) AND (quantity IS NULL OR quantity>=0)),
 CHECK(returnable_weight>=0 AND returnable_weight<=weight AND returnable_quantity>=0),
 CHECK(current_owner_id>=0 AND source_owner_id>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE metal_production_wip_transfer (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,factory_id BIGINT NOT NULL,operation_id BIGINT NOT NULL,
 source_execution_id BIGINT NOT NULL,target_execution_id BIGINT NOT NULL,order_step_id BIGINT NOT NULL,
 weight DECIMAL(20,2) NOT NULL,quantity BIGINT NOT NULL DEFAULT 0,
 allocation_basis VARCHAR(32) NOT NULL,create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_wip_transfer_factory_id(factory_id,id),
 FOREIGN KEY(factory_id,operation_id) REFERENCES metal_production_execution_operation(factory_id,id),
 FOREIGN KEY(factory_id,source_execution_id) REFERENCES metal_production_execution(factory_id,id),
 FOREIGN KEY(factory_id,target_execution_id) REFERENCES metal_production_execution(factory_id,id),
 FOREIGN KEY(order_step_id) REFERENCES metal_production_order_step(id),
 CHECK(source_execution_id<>target_execution_id AND weight>0 AND quantity>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE metal_production_material_allocation (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,factory_id BIGINT NOT NULL,operation_id BIGINT NULL,storage_event_id BIGINT NULL,
 from_source_id BIGINT NULL,to_source_id BIGINT NULL,wip_transfer_id BIGINT NULL,
 purpose VARCHAR(32) NOT NULL,weight DECIMAL(20,2) NOT NULL,quantity BIGINT NULL,
 allocation_basis VARCHAR(32) NOT NULL,create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY idx_material_allocation_event(factory_id,storage_event_id,id),
 FOREIGN KEY(factory_id,operation_id) REFERENCES metal_production_execution_operation(factory_id,id),
 FOREIGN KEY(factory_id,storage_event_id) REFERENCES metal_storage_event(factory_id,id),
 FOREIGN KEY(factory_id,from_source_id) REFERENCES metal_production_material_source(factory_id,id),
 FOREIGN KEY(factory_id,to_source_id) REFERENCES metal_production_material_source(factory_id,id),
 FOREIGN KEY(factory_id,wip_transfer_id) REFERENCES metal_production_wip_transfer(factory_id,id),
 CHECK(weight>=0 AND (quantity IS NULL OR quantity>=0)),
 CHECK(purpose IN ('IDENTITY_TRANSFER','WIP_TRANSFER','ISSUE','OUTPUT','RETURN','RECOVERY','LOSS','DISPATCH','ADJUSTMENT','REVERSAL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
SELECT '生产批次拆分合并',m.menu_id,90,'#',NULL,1,0,'F','0','0','metal:production-batch:transform','#','admin',NOW(),'统一生产批次身份及在制责任转移'
FROM sys_menu m WHERE m.perms='metal:work-order:list'
 AND NOT EXISTS(SELECT 1 FROM sys_menu existing WHERE existing.perms='metal:production-batch:transform') LIMIT 1;
