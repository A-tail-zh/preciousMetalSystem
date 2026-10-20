CREATE TABLE metal_process (
 process_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 process_name VARCHAR(100) NOT NULL,
 description VARCHAR(2000),
 process_type TINYINT NOT NULL,
 create_by VARCHAR(64) NOT NULL DEFAULT '',
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_by VARCHAR(64) NOT NULL DEFAULT '',
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 del_flag CHAR(1) NOT NULL DEFAULT '0',
 version INT NOT NULL DEFAULT 0,
 CONSTRAINT chk_process_type CHECK(process_type BETWEEN 1 AND 9)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE metal_material (
 material_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 material_code VARCHAR(64) NOT NULL UNIQUE,
 material_name VARCHAR(100) NOT NULL,
 material_type VARCHAR(30) NOT NULL,
 specification VARCHAR(200),
 create_by VARCHAR(64) NOT NULL DEFAULT '',
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_by VARCHAR(64) NOT NULL DEFAULT '',
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 del_flag CHAR(1) NOT NULL DEFAULT '0',
 version INT NOT NULL DEFAULT 0,
 CONSTRAINT chk_material_type CHECK(material_type IN ('旧铱坩埚','旧铱杆','铱块','铱粉'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE metal_product (
 product_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 product_code VARCHAR(64) NOT NULL UNIQUE,
 product_name VARCHAR(100) NOT NULL,
 product_category VARCHAR(30) NOT NULL,
 specification VARCHAR(200),
 dimensions JSON NOT NULL,
 volume DECIMAL(20,6),
 purity DECIMAL(20,6),
 unit VARCHAR(20) NOT NULL,
 theoretical_weight DECIMAL(20,6),
 create_by VARCHAR(64) NOT NULL DEFAULT '',
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_by VARCHAR(64) NOT NULL DEFAULT '',
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 del_flag CHAR(1) NOT NULL DEFAULT '0',
 version INT NOT NULL DEFAULT 0,
 CONSTRAINT chk_product_category CHECK(product_category IN ('铱坩埚弧底','铱坩埚平底','铱坩埚梯形','铱环','铱杆实心','铱杆空心')),
 CONSTRAINT chk_product_volume CHECK(volume IS NULL OR volume >= 0),
 CONSTRAINT chk_product_purity CHECK(purity IS NULL OR purity BETWEEN 0 AND 100),
 CONSTRAINT chk_product_weight CHECK(theoretical_weight IS NULL OR theoretical_weight >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE metal_product_process (
 product_id BIGINT NOT NULL,
 process_id BIGINT NOT NULL,
 sequence_no INT NOT NULL,
 theoretical_loss DECIMAL(20,6) NOT NULL,
 PRIMARY KEY(product_id,sequence_no),
 UNIQUE KEY uk_product_process(product_id,process_id),
 CONSTRAINT fk_product_process_product FOREIGN KEY(product_id) REFERENCES metal_product(product_id),
 CONSTRAINT fk_product_process_process FOREIGN KEY(process_id) REFERENCES metal_process(process_id),
 CONSTRAINT chk_loss_amount CHECK(theoretical_loss >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE metal_production_line
 ADD COLUMN create_by VARCHAR(64) NOT NULL DEFAULT '',
 ADD COLUMN create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 ADD COLUMN update_by VARCHAR(64) NOT NULL DEFAULT '',
 ADD COLUMN update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 ADD COLUMN version INT NOT NULL DEFAULT 0;

CREATE TABLE metal_line_product (
 production_line_id BIGINT NOT NULL,
 product_id BIGINT NOT NULL,
 PRIMARY KEY(production_line_id,product_id),
 CONSTRAINT fk_line_product_line FOREIGN KEY(production_line_id) REFERENCES metal_production_line(production_line_id),
 CONSTRAINT fk_line_product_product FOREIGN KEY(product_id) REFERENCES metal_product(product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE sys_user ADD COLUMN factory_id BIGINT NULL, ADD COLUMN version INT NOT NULL DEFAULT 0,
 ADD KEY idx_staff_factory(factory_id),
 ADD CONSTRAINT fk_staff_factory FOREIGN KEY(factory_id) REFERENCES metal_factory(factory_id);
ALTER TABLE sys_role ADD COLUMN factory_id BIGINT NULL, ADD COLUMN version INT NOT NULL DEFAULT 0,
 ADD KEY idx_role_factory(factory_id),
 ADD CONSTRAINT fk_role_factory FOREIGN KEY(factory_id) REFERENCES metal_factory(factory_id);

ALTER TABLE sys_user MODIFY COLUMN nick_name VARCHAR(100) NOT NULL,
 ADD COLUMN unique_phone VARCHAR(11) GENERATED ALWAYS AS (NULLIF(phonenumber,'')) STORED,
 ADD UNIQUE KEY uk_user_phone(unique_phone), ADD UNIQUE KEY uk_user_name(user_name);
ALTER TABLE sys_role MODIFY COLUMN role_name VARCHAR(100) NOT NULL,
 ADD COLUMN active_role_name VARCHAR(100) GENERATED ALWAYS AS (CASE WHEN del_flag='0' THEN role_name ELSE NULL END) STORED,
 ADD UNIQUE KEY uk_factory_role_name(factory_id,active_role_name);

INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(3100,'产线管理',3000,2,'production-line','metal/production-line/index',1,1,'C','0','0','metal:production-line:list','list','system',NOW());
INSERT INTO sys_role_menu(role_id,menu_id) VALUES(1,3100);
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(3101,'产线管理query',3100,1,'#','',1,1,'F','0','0','metal:production-line:query','#','system',NOW());
INSERT INTO sys_role_menu(role_id,menu_id) VALUES(1,3101);
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(3102,'产线管理add',3100,2,'#','',1,1,'F','0','0','metal:production-line:add','#','system',NOW());
INSERT INTO sys_role_menu(role_id,menu_id) VALUES(1,3102);
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(3103,'产线管理edit',3100,3,'#','',1,1,'F','0','0','metal:production-line:edit','#','system',NOW());
INSERT INTO sys_role_menu(role_id,menu_id) VALUES(1,3103);
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(3104,'产线管理remove',3100,4,'#','',1,1,'F','0','0','metal:production-line:remove','#','system',NOW());
INSERT INTO sys_role_menu(role_id,menu_id) VALUES(1,3104);
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(3110,'产品管理',3000,3,'product','metal/product/index',1,1,'C','0','0','metal:product:list','list','system',NOW());
INSERT INTO sys_role_menu(role_id,menu_id) VALUES(1,3110);
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(3111,'产品管理query',3110,1,'#','',1,1,'F','0','0','metal:product:query','#','system',NOW());
INSERT INTO sys_role_menu(role_id,menu_id) VALUES(1,3111);
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(3112,'产品管理add',3110,2,'#','',1,1,'F','0','0','metal:product:add','#','system',NOW());
INSERT INTO sys_role_menu(role_id,menu_id) VALUES(1,3112);
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(3113,'产品管理edit',3110,3,'#','',1,1,'F','0','0','metal:product:edit','#','system',NOW());
INSERT INTO sys_role_menu(role_id,menu_id) VALUES(1,3113);
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(3114,'产品管理remove',3110,4,'#','',1,1,'F','0','0','metal:product:remove','#','system',NOW());
INSERT INTO sys_role_menu(role_id,menu_id) VALUES(1,3114);
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(3120,'物料管理',3000,4,'material','metal/material/index',1,1,'C','0','0','metal:material:list','list','system',NOW());
INSERT INTO sys_role_menu(role_id,menu_id) VALUES(1,3120);
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(3121,'物料管理query',3120,1,'#','',1,1,'F','0','0','metal:material:query','#','system',NOW());
INSERT INTO sys_role_menu(role_id,menu_id) VALUES(1,3121);
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(3122,'物料管理add',3120,2,'#','',1,1,'F','0','0','metal:material:add','#','system',NOW());
INSERT INTO sys_role_menu(role_id,menu_id) VALUES(1,3122);
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(3123,'物料管理edit',3120,3,'#','',1,1,'F','0','0','metal:material:edit','#','system',NOW());
INSERT INTO sys_role_menu(role_id,menu_id) VALUES(1,3123);
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(3124,'物料管理remove',3120,4,'#','',1,1,'F','0','0','metal:material:remove','#','system',NOW());
INSERT INTO sys_role_menu(role_id,menu_id) VALUES(1,3124);
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(3130,'工序管理',3000,5,'process','metal/process/index',1,1,'C','0','0','metal:process:list','list','system',NOW());
INSERT INTO sys_role_menu(role_id,menu_id) VALUES(1,3130);
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(3131,'工序管理query',3130,1,'#','',1,1,'F','0','0','metal:process:query','#','system',NOW());
INSERT INTO sys_role_menu(role_id,menu_id) VALUES(1,3131);
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(3132,'工序管理add',3130,2,'#','',1,1,'F','0','0','metal:process:add','#','system',NOW());
INSERT INTO sys_role_menu(role_id,menu_id) VALUES(1,3132);
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(3133,'工序管理edit',3130,3,'#','',1,1,'F','0','0','metal:process:edit','#','system',NOW());
INSERT INTO sys_role_menu(role_id,menu_id) VALUES(1,3133);
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(3134,'工序管理remove',3130,4,'#','',1,1,'F','0','0','metal:process:remove','#','system',NOW());
INSERT INTO sys_role_menu(role_id,menu_id) VALUES(1,3134);
UPDATE sys_menu SET menu_name='人员管理',component='metal/staff/index' WHERE menu_id=100;
UPDATE sys_menu SET component='metal/role/index' WHERE menu_id=101;
