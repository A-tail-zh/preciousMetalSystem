-- Existing orders and balances keep their original policy and evidence.
ALTER TABLE metal_work_order
 ADD task_type VARCHAR(24) NOT NULL DEFAULT 'PRODUCTION',
 ADD processing_policy_version INT NOT NULL DEFAULT 1,
 ADD CONSTRAINT chk_work_task_type CHECK(task_type IN ('PRODUCTION','RAW_PROCESSING'));
ALTER TABLE metal_work_order_item
 MODIFY product_id BIGINT NULL,
 ADD material_id BIGINT NULL,
 ADD material_snapshot JSON NULL,
 ADD planned_input_weight DECIMAL(20,6) NULL,
 DROP CHECK chk_wo_quantity,
 ADD CONSTRAINT fk_work_item_material FOREIGN KEY(material_id) REFERENCES metal_material(material_id),
 ADD CONSTRAINT chk_work_item_target CHECK((product_id IS NOT NULL AND material_id IS NULL AND quantity BETWEEN 1 AND 1000000 AND planned_input_weight IS NULL) OR (product_id IS NULL AND material_id IS NOT NULL AND quantity=0 AND material_snapshot IS NOT NULL AND planned_input_weight>0));
ALTER TABLE metal_work_order_step
 ADD process_role VARCHAR(24) NULL,
 ADD output_stage VARCHAR(16) NULL,
 ADD CONSTRAINT chk_work_step_role CHECK(process_role IS NULL OR process_role IN ('ACID_WASH','SMELTING','PREPARATION','POST_PROCESS')),
 ADD CONSTRAINT chk_work_step_stage CHECK(output_stage IS NULL OR output_stage IN ('RAW','SEMI','FINISHED'));
ALTER TABLE metal_process_batch
 ADD processing_policy_version INT NOT NULL DEFAULT 1,
 ADD process_role VARCHAR(24) NULL,
 ADD output_stage VARCHAR(16) NULL,
 ADD metal_type_id BIGINT NULL,
 DROP CHECK metal_process_batch_chk_1,
 ADD CONSTRAINT chk_processing_batch_quantity CHECK((processing_policy_version>=2 AND output_stage='RAW' AND planned_quantity=0) OR (COALESCE(output_stage,'')<>'RAW' AND planned_quantity BETWEEN 1 AND 1000000));
ALTER TABLE metal_inventory
 ADD powder_origin VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
 ADD washing_status VARCHAR(16) NOT NULL DEFAULT 'UNVERIFIED',
 ADD smelting_status VARCHAR(16) NOT NULL DEFAULT 'UNVERIFIED',
 ADD origin_policy_version INT NOT NULL DEFAULT 1,
 ADD origin_verification_id BIGINT NULL,
 ADD raw_origin_key VARCHAR(128) GENERATED ALWAYS AS (CASE WHEN category='RAW' THEN CONCAT(powder_origin,':',washing_status,':',origin_policy_version,':',COALESCE(origin_batch_id,0),':',COALESCE(origin_acid_wash_id,0),':',COALESCE(origin_verification_id,0)) ELSE '' END) STORED,
 DROP INDEX uq_inventory_raw_kind,
 ADD UNIQUE KEY uq_inventory_raw_origin(factory_id,warehouse_id,category,reference_id,batch_no,raw_customer_key,ownership_status,raw_kind_key,raw_origin_key),
 ADD CONSTRAINT chk_inventory_powder_origin CHECK(powder_origin IN ('NATIVE','RECYCLED','UNKNOWN')),
 ADD CONSTRAINT chk_inventory_washing CHECK(washing_status IN ('EXEMPT','PASSED','REQUIRED','UNVERIFIED')),
 ADD CONSTRAINT chk_inventory_smelting CHECK(smelting_status IN ('PASSED','UNVERIFIED'));
ALTER TABLE metal_warehouse_order_line
 ADD powder_origin VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
 ADD washing_status VARCHAR(16) NOT NULL DEFAULT 'UNVERIFIED',
 ADD smelting_status VARCHAR(16) NOT NULL DEFAULT 'UNVERIFIED',
 ADD origin_policy_version INT NOT NULL DEFAULT 1,
 ADD origin_verification_id BIGINT NULL;
CREATE TABLE metal_inventory_origin_verification (
 verification_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 inventory_id BIGINT NOT NULL,factory_id BIGINT NOT NULL,
 request_id VARCHAR(36) NOT NULL UNIQUE,request_hash CHAR(64) NOT NULL,
 actor_id BIGINT NOT NULL,actor_name VARCHAR(64) NOT NULL,evidence VARCHAR(500) NOT NULL,
 old_powder_origin VARCHAR(16),new_powder_origin VARCHAR(16) NOT NULL,
 old_washing_status VARCHAR(16),new_washing_status VARCHAR(16) NOT NULL,
 old_smelting_status VARCHAR(16),new_smelting_status VARCHAR(16) NOT NULL,
 old_origin_policy_version INT,new_origin_policy_version INT NOT NULL,
 inventory_version INT NOT NULL,create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY ix_origin_verification_inventory(inventory_id,verification_id)
) ENGINE=InnoDB;
CREATE TABLE metal_raw_plan_change (
 change_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 request_id VARCHAR(36) NOT NULL UNIQUE,request_hash CHAR(64) NOT NULL,
 work_order_id BIGINT NOT NULL,item_id BIGINT NOT NULL,
 old_weight DECIMAL(20,6) NOT NULL,new_weight DECIMAL(20,6) NOT NULL,
 reason VARCHAR(500) NOT NULL,actor_id BIGINT NOT NULL,actor_name VARCHAR(64) NOT NULL,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY ix_raw_plan_item(item_id,change_id)
) ENGINE=InnoDB;
CREATE TABLE metal_raw_qualification_use (
 qualification_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 line_id BIGINT NOT NULL UNIQUE,item_id BIGINT NOT NULL,process_id BIGINT NOT NULL,
 inventory_id BIGINT NOT NULL,qualification_snapshot JSON NOT NULL,
 actor_id BIGINT NOT NULL,create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY ix_raw_qualification_item(item_id,process_id)
) ENGINE=InnoDB;
CREATE TABLE metal_semi_qualification_use (
 line_id BIGINT NOT NULL PRIMARY KEY,inventory_id BIGINT NOT NULL,source_batch_id BIGINT NOT NULL,
 item_id BIGINT NOT NULL,batch_id BIGINT NOT NULL,qualification_snapshot JSON NOT NULL,
 actor_id BIGINT NOT NULL,create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY ix_semi_qualification_batch(batch_id)
) ENGINE=InnoDB;
-- Permission is declared only. No warehouse role receives it automatically.
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,remark)
SELECT '原料来源核对',menu_id,99,'','','1','0','F','0','0','metal:inventory:verify-origin','#','admin','独立来源资格核对权限，默认仅超级管理员拥有'
FROM (SELECT MIN(menu_id) menu_id FROM sys_menu WHERE perms='metal:inventory:list') p
WHERE p.menu_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='metal:inventory:verify-origin');
