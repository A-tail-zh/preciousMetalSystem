CREATE TABLE metal_factory (
  factory_id BIGINT NOT NULL AUTO_INCREMENT,
  factory_name VARCHAR(100) NOT NULL,
  area DECIMAL(14,4) NOT NULL COMMENT '占地面积，万平方米',
  address VARCHAR(500) NOT NULL,
  introduction TEXT NULL,
  create_by VARCHAR(64) NOT NULL DEFAULT '',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64) NOT NULL DEFAULT '',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  del_flag CHAR(1) NOT NULL DEFAULT '0',
  version INT NOT NULL DEFAULT 0,
  PRIMARY KEY(factory_id),
  KEY idx_factory_live(del_flag, factory_id),
  CONSTRAINT chk_factory_area CHECK (area > 0),
  CONSTRAINT chk_factory_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工厂基础信息';

-- 产线基础关联表；后续产线模块在此表扩展，不创建平行关系表。
CREATE TABLE metal_production_line (
  production_line_id BIGINT NOT NULL AUTO_INCREMENT,
  production_line_code VARCHAR(64) NOT NULL,
  production_line_name VARCHAR(100) NOT NULL,
  factory_id BIGINT NULL,
  PRIMARY KEY(production_line_id),
  UNIQUE KEY uk_line_code(production_line_code),
  KEY idx_line_factory(factory_id),
  CONSTRAINT fk_line_factory FOREIGN KEY(factory_id) REFERENCES metal_factory(factory_id)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产线资料及工厂归属';

INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
VALUES
(3000,'基础信息管理',0,0,'metal',NULL,1,1,'M','0','0','','tree','system',NOW()),
(3001,'工厂管理',3000,1,'factory','metal/factory/index',1,1,'C','0','0','metal:factory:list','build','system',NOW()),
(3002,'工厂查询',3001,1,'#','',1,1,'F','0','0','metal:factory:query','#','system',NOW()),
(3003,'工厂新增',3001,2,'#','',1,1,'F','0','0','metal:factory:add','#','system',NOW()),
(3004,'工厂修改',3001,3,'#','',1,1,'F','0','0','metal:factory:edit','#','system',NOW()),
(3005,'工厂删除',3001,4,'#','',1,1,'F','0','0','metal:factory:remove','#','system',NOW());

-- 只授予初始超级管理员，普通角色在若依角色管理中显式授权。
INSERT INTO sys_role_menu(role_id,menu_id) VALUES (1,3000),(1,3001),(1,3002),(1,3003),(1,3004),(1,3005);
