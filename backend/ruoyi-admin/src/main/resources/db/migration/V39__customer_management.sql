CREATE TABLE metal_customer (
 customer_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 factory_id BIGINT NOT NULL,
 customer_name VARCHAR(100) COLLATE utf8mb4_bin NOT NULL,
 company_name VARCHAR(100) COLLATE utf8mb4_bin NOT NULL,
 address VARCHAR(500) NOT NULL,
 contact_name VARCHAR(100) NOT NULL,
 contact_phone VARCHAR(11) NOT NULL,
 department VARCHAR(100) NULL,
 create_by VARCHAR(64) NOT NULL DEFAULT '',
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_by VARCHAR(64) NOT NULL DEFAULT '',
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 version INT NOT NULL DEFAULT 0,
 del_flag CHAR(1) NOT NULL DEFAULT '0',
 active_record TINYINT GENERATED ALWAYS AS (CASE WHEN del_flag='0' THEN 1 ELSE NULL END) STORED,
 UNIQUE KEY uk_customer_active(factory_id,customer_name,company_name,active_record),
 FOREIGN KEY(factory_id) REFERENCES metal_factory(factory_id),
 CHECK(CHAR_LENGTH(TRIM(customer_name))>0 AND CHAR_LENGTH(TRIM(company_name))>0 AND CHAR_LENGTH(TRIM(address))>0 AND CHAR_LENGTH(TRIM(contact_name))>0),
 CHECK(contact_phone REGEXP '^1[3-9][0-9]{9}$'),
 CHECK(del_flag IN ('0','2'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工厂客户档案，独立于历史工单客户文本';

INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES
(3140,'客户管理',3000,6,'customer','metal/customer/index',1,1,'C','0','0','metal:customer:list','peoples','system',NOW()),
(3141,'客户查询',3140,1,'#','',1,1,'F','0','0','metal:customer:query','#','system',NOW()),
(3142,'客户添加',3140,2,'#','',1,1,'F','0','0','metal:customer:add','#','system',NOW()),
(3143,'客户编辑',3140,3,'#','',1,1,'F','0','0','metal:customer:edit','#','system',NOW()),
(3144,'客户删除',3140,4,'#','',1,1,'F','0','0','metal:customer:remove','#','system',NOW());
INSERT INTO sys_role_menu(role_id,menu_id) VALUES(1,3140),(1,3141),(1,3142),(1,3143),(1,3144);
