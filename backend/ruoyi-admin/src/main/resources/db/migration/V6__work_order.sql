CREATE TABLE metal_work_order_sequence (
 business_date DATE PRIMARY KEY, sequence_value INT NOT NULL DEFAULT 0,
 CONSTRAINT chk_wo_sequence CHECK(sequence_value BETWEEN 0 AND 999999)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE metal_work_order (
 work_order_id BIGINT AUTO_INCREMENT PRIMARY KEY,
 order_no VARCHAR(32) NOT NULL UNIQUE,
 request_id VARCHAR(36) NOT NULL UNIQUE, request_hash CHAR(64) NOT NULL, request_actor BIGINT NOT NULL,
 customer VARCHAR(100) NOT NULL, factory_id BIGINT NOT NULL, production_line_id BIGINT NOT NULL, owner_id BIGINT NOT NULL,
 factory_name VARCHAR(100) NOT NULL, line_name VARCHAR(100) NOT NULL, owner_name VARCHAR(100) NOT NULL,
 order_date DATE NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'PENDING', reject_reason VARCHAR(500),
 create_by VARCHAR(64) NOT NULL, creator_name VARCHAR(100) NOT NULL, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_by VARCHAR(64) NOT NULL, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, del_flag CHAR(1) NOT NULL DEFAULT '0', version INT NOT NULL DEFAULT 0,
 KEY idx_wo_factory(factory_id,del_flag,work_order_id), KEY idx_wo_line(production_line_id,del_flag),
 FOREIGN KEY(factory_id) REFERENCES metal_factory(factory_id),
 FOREIGN KEY(production_line_id) REFERENCES metal_production_line(production_line_id),
 FOREIGN KEY(owner_id) REFERENCES sys_user(user_id),
 CONSTRAINT chk_wo_status CHECK(status IN ('PENDING','REJECTED','APPROVED','IN_PROGRESS'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE metal_work_order_item (
 item_id BIGINT AUTO_INCREMENT PRIMARY KEY, work_order_id BIGINT NOT NULL, sequence_no INT NOT NULL,
 product_id BIGINT NOT NULL, quantity INT NOT NULL, product_snapshot JSON NOT NULL,
 UNIQUE KEY uk_wo_item_sequence(work_order_id,sequence_no), KEY idx_wo_product(product_id),
 FOREIGN KEY(work_order_id) REFERENCES metal_work_order(work_order_id), FOREIGN KEY(product_id) REFERENCES metal_product(product_id),
 CONSTRAINT chk_wo_quantity CHECK(quantity BETWEEN 1 AND 1000000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE metal_work_order_step (
 step_id BIGINT AUTO_INCREMENT PRIMARY KEY, item_id BIGINT NOT NULL, process_id BIGINT NOT NULL, sequence_no INT NOT NULL,
 process_name VARCHAR(100) NOT NULL, theoretical_loss DECIMAL(20,6) NOT NULL,
 start_time DATETIME NOT NULL, end_time DATETIME NOT NULL, actual_start_time DATETIME, actual_end_time DATETIME,
 UNIQUE KEY uk_wo_step_sequence(item_id,sequence_no), UNIQUE KEY uk_wo_step_process(item_id,process_id),
 FOREIGN KEY(item_id) REFERENCES metal_work_order_item(item_id), FOREIGN KEY(process_id) REFERENCES metal_process(process_id),
 CONSTRAINT chk_wo_step_time CHECK(start_time < end_time), CONSTRAINT chk_wo_loss CHECK(theoretical_loss >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE metal_work_order_review (
 review_id BIGINT AUTO_INCREMENT PRIMARY KEY, work_order_id BIGINT NOT NULL, action VARCHAR(20) NOT NULL,
 from_status VARCHAR(20), to_status VARCHAR(20) NOT NULL, reason VARCHAR(500), actor_id BIGINT NOT NULL, actor_name VARCHAR(100) NOT NULL,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(work_order_id) REFERENCES metal_work_order(work_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES
(3300,'工单管理',3000,6,'work-order','metal/work-order/index',1,1,'C','0','0','metal:work-order:list','list','system',NOW()),
(3301,'工单查询',3300,1,'#','',1,1,'F','0','0','metal:work-order:query','#','system',NOW()),
(3302,'工单新增',3300,2,'#','',1,1,'F','0','0','metal:work-order:add','#','system',NOW()),
(3303,'工单修改',3300,3,'#','',1,1,'F','0','0','metal:work-order:edit','#','system',NOW()),
(3304,'工单删除',3300,4,'#','',1,1,'F','0','0','metal:work-order:remove','#','system',NOW()),
(3305,'工单审核通过',3300,5,'#','',1,1,'F','0','0','metal:work-order:approve','#','system',NOW()),
(3306,'工单驳回',3300,6,'#','',1,1,'F','0','0','metal:work-order:reject','#','system',NOW()),
(3307,'工单重新提交',3300,7,'#','',1,1,'F','0','0','metal:work-order:resubmit','#','system',NOW());
INSERT INTO sys_role_menu(role_id,menu_id) VALUES(1,3300),(1,3301),(1,3302),(1,3303),(1,3304),(1,3305),(1,3306),(1,3307);
