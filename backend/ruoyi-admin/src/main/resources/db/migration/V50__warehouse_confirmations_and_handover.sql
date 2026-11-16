-- New workflow records are separate from posted facts; no historical balances or reports are recalculated.
ALTER TABLE metal_inventory
 ADD material_ownership VARCHAR(16) NOT NULL DEFAULT 'UNVERIFIED',
 ADD customer_name_snapshot VARCHAR(100) NULL,
 ADD customer_company_snapshot VARCHAR(200) NULL,
 ADD product_type_snapshot VARCHAR(40) NULL,
 ADD metal_type_name_snapshot VARCHAR(100) NULL,
 ADD metal_type_id_snapshot BIGINT NULL,
 ADD source_process_id BIGINT NULL,
 ADD source_process_name VARCHAR(100) NULL,
 ADD CONSTRAINT chk_inventory_material_owner CHECK(material_ownership IN ('CUSTOMER','FACTORY','UNVERIFIED'));
UPDATE metal_inventory SET material_ownership='CUSTOMER' WHERE customer_id IS NOT NULL;
ALTER TABLE metal_inventory ADD INDEX idx_inventory_factory_owner(factory_id);
ALTER TABLE metal_inventory DROP INDEX uq_inventory_raw_kind;
ALTER TABLE metal_inventory MODIFY raw_customer_key VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin
 GENERATED ALWAYS AS (CASE WHEN category='RAW' THEN CASE WHEN material_ownership='FACTORY' THEN CONCAT('F:',factory_id) WHEN customer_id IS NOT NULL THEN CONCAT('C:',customer_id) ELSE CONCAT('L:',COALESCE(TRIM(organization),'')) END ELSE '' END) STORED;
ALTER TABLE metal_inventory ADD UNIQUE KEY uq_inventory_raw_kind(factory_id,warehouse_id,category,reference_id,batch_no,raw_customer_key,ownership_status,raw_kind_key);

ALTER TABLE metal_warehouse_order_line
 ADD material_ownership VARCHAR(16) NOT NULL DEFAULT 'UNVERIFIED',
 ADD planned_net_weight DECIMAL(20,6) NULL,
 ADD processor_net_weight DECIMAL(20,6) NULL,
 ADD processor_actor_id BIGINT NULL,
 ADD production_supply_kind VARCHAR(24) NULL,
 ADD weighing_difference_reason VARCHAR(500) NULL,
 ADD confirmed_by BIGINT NULL,
 ADD confirmed_at DATETIME NULL;
UPDATE metal_warehouse_order_line SET material_ownership='CUSTOMER' WHERE customer_id IS NOT NULL;
ALTER TABLE metal_work_report
 ADD processor_output_weight DECIMAL(20,6) NULL,
 ADD processor_returned_weight DECIMAL(20,6) NULL,
 ADD keeper_received_weight DECIMAL(20,6) NULL,
 ADD handover_difference DECIMAL(20,6) NULL;

ALTER TABLE metal_acid_wash_batch MODIFY customer_id BIGINT NULL, ADD material_ownership VARCHAR(16) NOT NULL DEFAULT 'CUSTOMER';

CREATE TABLE metal_warehouse_flow (
 flow_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 request_id VARCHAR(36) NOT NULL UNIQUE,
 request_hash CHAR(64) NOT NULL,
 request_actor BIGINT NOT NULL,
 factory_id BIGINT NOT NULL,
 kind VARCHAR(24) NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'WAITING',
 stage VARCHAR(16) NOT NULL,
 version INT NOT NULL DEFAULT 0,
 revision INT NOT NULL DEFAULT 0,
 work_order_id BIGINT NULL,
 batch_id BIGINT NULL,
 created_batch BOOLEAN NOT NULL DEFAULT FALSE,
 plan_json JSON NOT NULL,
 reason VARCHAR(500) NULL,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 posted_time DATETIME NULL,
 active_handover BIGINT GENERATED ALWAYS AS (CASE WHEN kind='HANDOVER' AND status NOT IN ('VOID','CANCELLED') THEN batch_id ELSE NULL END) STORED,
 FOREIGN KEY(factory_id) REFERENCES metal_factory(factory_id),
 FOREIGN KEY(work_order_id) REFERENCES metal_work_order(work_order_id),
 FOREIGN KEY(batch_id) REFERENCES metal_process_batch(batch_id),
 UNIQUE KEY uq_active_handover(active_handover),
 KEY idx_flow_factory_status(factory_id,status,flow_id),
 KEY idx_flow_work_batch(work_order_id,batch_id,status),
 CHECK(kind IN ('OUTBOUND','RAW_INBOUND','HANDOVER','PRODUCT_RETURN','CUSTOMER_RETURN','OFFSET','STORAGE')),
 CHECK(status IN ('WAITING','COLLECTING','POSTED','RETURNED','CANCELLED','VOID')),
 CHECK(stage IN ('RECIPIENT','PROCESSORS','KEEPER','DONE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE metal_warehouse_flow_group (
 group_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 flow_id BIGINT NOT NULL,
 revision INT NOT NULL DEFAULT 0,
 sequence_no INT NOT NULL,
 order_id BIGINT NULL,
 warehouse_id BIGINT NOT NULL,
 type VARCHAR(32) NOT NULL,
 keeper_id BIGINT NOT NULL,
 recipient_id BIGINT NULL,
 processor_id BIGINT NULL,
 order_json JSON NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'WAITING',
 confirmed_by BIGINT NULL,
 confirmed_at DATETIME NULL,
 FOREIGN KEY(flow_id) REFERENCES metal_warehouse_flow(flow_id),
 FOREIGN KEY(order_id) REFERENCES metal_warehouse_order(order_id),
 FOREIGN KEY(warehouse_id) REFERENCES metal_warehouse(warehouse_id),
 UNIQUE KEY uq_flow_group_order(order_id),
 KEY idx_flow_group_revision(flow_id,revision,sequence_no),
 CHECK(status IN ('WAITING','CONFIRMED','SUPERSEDED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE metal_warehouse_flow_line (
 flow_line_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 group_id BIGINT NOT NULL,
 sequence_no INT NOT NULL,
 line_json JSON NOT NULL,
 inventory_id BIGINT NULL,
 source_line_id BIGINT NULL,
 planned_weight DECIMAL(20,6) NOT NULL,
 actual_weight DECIMAL(20,6) NULL,
 processor_weight DECIMAL(20,6) NULL,
 difference_reason VARCHAR(500) NULL,
 posted_line_id BIGINT NULL,
 FOREIGN KEY(group_id) REFERENCES metal_warehouse_flow_group(group_id),
 FOREIGN KEY(inventory_id) REFERENCES metal_inventory(inventory_id),
 FOREIGN KEY(source_line_id) REFERENCES metal_warehouse_order_line(line_id),
 FOREIGN KEY(posted_line_id) REFERENCES metal_warehouse_order_line(line_id),
 UNIQUE KEY uq_flow_line_sequence(group_id,sequence_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE metal_stock_reservation (
 reservation_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 flow_line_id BIGINT NOT NULL UNIQUE,
 flow_id BIGINT NOT NULL,
 factory_id BIGINT NOT NULL,
 inventory_id BIGINT NULL,
 source_line_id BIGINT NULL,
 release_id BIGINT NULL,
 skip_permit_id BIGINT NULL,
 batch_id BIGINT NULL,
 item_id BIGINT NULL,
 process_id BIGINT NULL,
 category VARCHAR(16) NOT NULL,
 weight DECIMAL(20,6) NOT NULL,
 quantity BIGINT NOT NULL DEFAULT 0,
 route_profile JSON NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
 FOREIGN KEY(flow_line_id) REFERENCES metal_warehouse_flow_line(flow_line_id),
 FOREIGN KEY(flow_id) REFERENCES metal_warehouse_flow(flow_id),
 FOREIGN KEY(inventory_id) REFERENCES metal_inventory(inventory_id),
 FOREIGN KEY(source_line_id) REFERENCES metal_warehouse_order_line(line_id),
 FOREIGN KEY(release_id) REFERENCES metal_semi_release(release_id),
 FOREIGN KEY(batch_id) REFERENCES metal_process_batch(batch_id),
 CHECK(status IN ('ACTIVE','VALIDATING','CONSUMED','RELEASED')),
 CHECK(weight>=0 AND quantity>=0),
 KEY idx_reservation_stock(inventory_id,status),
 KEY idx_reservation_release(release_id,status),
 KEY idx_reservation_stage(item_id,process_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE metal_warehouse_flow_processor (
 flow_id BIGINT NOT NULL,
 actor_id BIGINT NOT NULL,
 submitted BOOLEAN NOT NULL DEFAULT FALSE,
 submitted_time DATETIME NULL,
 PRIMARY KEY(flow_id,actor_id),
 FOREIGN KEY(flow_id) REFERENCES metal_warehouse_flow(flow_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE metal_warehouse_flow_request (
 request_id VARCHAR(36) NOT NULL PRIMARY KEY,
 flow_id BIGINT NOT NULL,
 operation VARCHAR(24) NOT NULL,
 actor_id BIGINT NOT NULL,
 request_hash CHAR(64) NOT NULL,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(flow_id) REFERENCES metal_warehouse_flow(flow_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE metal_warehouse_flow_event (
 event_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 flow_id BIGINT NOT NULL,
 version INT NOT NULL,
 actor_id BIGINT NOT NULL,
 action VARCHAR(24) NOT NULL,
 content JSON NOT NULL,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(flow_id) REFERENCES metal_warehouse_flow(flow_id),
 KEY idx_flow_event(flow_id,event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE metal_warehouse_order
 DROP CHECK metal_warehouse_order_chk_1,
 ADD CONSTRAINT chk_warehouse_order_state CHECK(status IN ('POSTED','VOID','WAITING_OUT','WAITING_IN','RETURNED','CANCELLED')),
 ADD warehouse_flow_id BIGINT NULL,
 ADD workflow_group_id BIGINT NULL,
 ADD confirmed_by BIGINT NULL,
 ADD confirmed_at DATETIME NULL,
 ADD CONSTRAINT fk_order_flow FOREIGN KEY(warehouse_flow_id) REFERENCES metal_warehouse_flow(flow_id),
 ADD CONSTRAINT fk_order_flow_group FOREIGN KEY(workflow_group_id) REFERENCES metal_warehouse_flow_group(group_id);

CREATE TABLE metal_work_order_residual (
 residual_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 work_order_id BIGINT NOT NULL,
 inventory_id BIGINT NOT NULL,
 source_line_id BIGINT NOT NULL UNIQUE,
 material_ownership VARCHAR(16) NOT NULL,
 customer_id BIGINT NULL,
 initial_weight DECIMAL(20,6) NOT NULL,
 remaining_weight DECIMAL(20,6) NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
 version INT NOT NULL DEFAULT 0,
 FOREIGN KEY(work_order_id) REFERENCES metal_work_order(work_order_id),
 FOREIGN KEY(inventory_id) REFERENCES metal_inventory(inventory_id),
 FOREIGN KEY(source_line_id) REFERENCES metal_warehouse_order_line(line_id),
 CHECK(status IN ('OPEN','STORED','RETURNED','OFFSET')),
 CHECK(remaining_weight>=0 AND remaining_weight<=initial_weight),
 KEY idx_residual_work(work_order_id,status),
 KEY idx_residual_stock(inventory_id,residual_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE metal_work_order_residual_use (
 use_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 residual_id BIGINT NOT NULL,
 line_id BIGINT NOT NULL,
 weight DECIMAL(20,6) NOT NULL,
 reversed BOOLEAN NOT NULL DEFAULT FALSE,
 FOREIGN KEY(residual_id) REFERENCES metal_work_order_residual(residual_id),
 FOREIGN KEY(line_id) REFERENCES metal_warehouse_order_line(line_id),
 UNIQUE KEY uq_residual_use(residual_id,line_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE metal_customer_residual_closeout (
 closeout_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 request_id VARCHAR(36) NOT NULL UNIQUE,
 request_hash CHAR(64) NOT NULL,
 actor_id BIGINT NOT NULL,
 factory_id BIGINT NOT NULL,
 work_order_id BIGINT NOT NULL,
 customer_id BIGINT NOT NULL,
 mode VARCHAR(16) NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'WAITING',
 weight DECIMAL(20,6) NOT NULL,
 offset_work_order_id BIGINT NULL,
 version INT NOT NULL DEFAULT 0,
 reason VARCHAR(500) NOT NULL,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 active_work BIGINT GENERATED ALWAYS AS (CASE WHEN status<>'VOID' THEN work_order_id ELSE NULL END) STORED,
 FOREIGN KEY(factory_id) REFERENCES metal_factory(factory_id),
 FOREIGN KEY(work_order_id) REFERENCES metal_work_order(work_order_id),
 FOREIGN KEY(customer_id) REFERENCES metal_customer(customer_id),
 FOREIGN KEY(offset_work_order_id) REFERENCES metal_work_order(work_order_id),
 UNIQUE KEY uq_active_closeout(active_work),
 CHECK(mode IN ('RETURN','STORAGE','OFFSET')),
 CHECK(status IN ('WAITING','PARTIAL','DONE','VOID'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
ALTER TABLE metal_warehouse_flow
 ADD closeout_id BIGINT NULL,
 ADD CONSTRAINT fk_flow_closeout FOREIGN KEY(closeout_id) REFERENCES metal_customer_residual_closeout(closeout_id);
CREATE TABLE metal_customer_residual_closeout_line (
 closeout_id BIGINT NOT NULL,
 residual_id BIGINT NOT NULL,
 weight DECIMAL(20,6) NOT NULL,
 PRIMARY KEY(closeout_id,residual_id),
 FOREIGN KEY(closeout_id) REFERENCES metal_customer_residual_closeout(closeout_id),
 FOREIGN KEY(residual_id) REFERENCES metal_work_order_residual(residual_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO sys_menu(menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT '实领确认',4030,11,'#',1,1,'F','0','0','metal:warehouse-order:receive','#','system',NOW()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='metal:warehouse-order:receive');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT '加工交接',4030,12,'#',1,1,'F','0','0','metal:warehouse-order:handover','#','system',NOW()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='metal:warehouse-order:handover');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT '库管验收',4030,13,'#',1,1,'F','0','0','metal:warehouse-order:accept','#','system',NOW()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='metal:warehouse-order:accept');
