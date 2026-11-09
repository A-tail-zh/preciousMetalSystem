ALTER TABLE metal_type
 ADD version INT NOT NULL DEFAULT 0,
 ADD update_by VARCHAR(64) NOT NULL DEFAULT '',
 ADD update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;
UPDATE metal_type SET update_time=create_time;

CREATE TABLE metal_product_type (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 factory_id BIGINT NOT NULL,
 name VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
 version INT NOT NULL DEFAULT 0,
 create_by VARCHAR(64) NOT NULL DEFAULT '',
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_by VARCHAR(64) NOT NULL DEFAULT '',
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uq_factory_product_type(factory_id,name),
 CONSTRAINT fk_product_type_factory FOREIGN KEY(factory_id) REFERENCES metal_factory(factory_id),
 CONSTRAINT chk_product_type_formula CHECK(BINARY name IN ('平底坩埚','环','杆','梯形','弧底','靶','方形坩埚','方形盖')),
 CONSTRAINT chk_product_type_version CHECK(version>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE metal_material_type (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 factory_id BIGINT NOT NULL,
 name VARCHAR(100) NOT NULL,
 version INT NOT NULL DEFAULT 0,
 create_by VARCHAR(64) NOT NULL DEFAULT '',
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_by VARCHAR(64) NOT NULL DEFAULT '',
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uq_factory_material_type(factory_id,name),
 CONSTRAINT fk_material_type_factory FOREIGN KEY(factory_id) REFERENCES metal_factory(factory_id),
 CONSTRAINT chk_material_type_name CHECK(CHAR_LENGTH(TRIM(name))>0),
 CONSTRAINT chk_material_type_version CHECK(version>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
VALUES('类型管理',3000,2,'type-management','metal/type-management/index',1,1,'C','0','0','metal:type-management:list','list','system',NOW());
SET @type_menu=LAST_INSERT_ID();
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT CONCAT(k.label,a.label),@type_menu,k.seq*10+a.seq,'#','',1,1,'F','0','0',CONCAT('metal:',k.kind,'-type:',a.action),'#','system',NOW()
FROM (SELECT 'metal' kind,'金属类型' label,1 seq UNION ALL SELECT 'product','产品类型',2 UNION ALL SELECT 'material','物料类型',3) k
CROSS JOIN (SELECT 'list' action,'查看' label,1 seq UNION ALL SELECT 'add','新增',2 UNION ALL SELECT 'edit','编辑',3 UNION ALL SELECT 'remove','删除',4) a;
INSERT INTO sys_role_menu(role_id,menu_id) SELECT 1,menu_id FROM sys_menu WHERE menu_id=@type_menu OR parent_id=@type_menu;
