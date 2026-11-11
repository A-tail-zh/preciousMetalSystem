CREATE TABLE metal_storage_document (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 factory_id BIGINT NOT NULL,
 number VARCHAR(48) NOT NULL UNIQUE,
 kind VARCHAR(24) NOT NULL,
 type VARCHAR(32) NOT NULL,
 status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
 version INT NOT NULL DEFAULT 0,
 review_round INT NOT NULL DEFAULT 0,
 order_id BIGINT NULL,
 execution_id BIGINT NULL,
 creator_id BIGINT NOT NULL,
 reviewer_id BIGINT NOT NULL,
 business_date DATE NOT NULL,
 data JSON NOT NULL,
 deleted TINYINT NOT NULL DEFAULT 0,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_storage_document_factory(factory_id,id),
 KEY idx_storage_document_list(factory_id,kind,status,create_time,id),
 KEY idx_storage_document_order(factory_id,order_id,type,status),
 FOREIGN KEY(factory_id) REFERENCES metal_factory(factory_id),
 FOREIGN KEY(order_id) REFERENCES metal_production_order(id),
 FOREIGN KEY(creator_id) REFERENCES sys_user(user_id),
 FOREIGN KEY(reviewer_id) REFERENCES sys_user(user_id),
 CHECK(status IN ('DRAFT','PENDING_REVIEW','RETURNED','POSTED','CANCELLED','REVERSED')),
 CHECK(version>=0 AND review_round>=0),CHECK(deleted IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE metal_storage_document_line (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 factory_id BIGINT NOT NULL,
 document_id BIGINT NOT NULL,
 sequence_no INT NOT NULL,
 data JSON NOT NULL,
 UNIQUE KEY uk_storage_line_seq(document_id,sequence_no),
 UNIQUE KEY uk_storage_line_factory(factory_id,id),
 FOREIGN KEY(factory_id,document_id) REFERENCES metal_storage_document(factory_id,id),
 CHECK(sequence_no>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE metal_storage_review_task (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 factory_id BIGINT NOT NULL,
 document_id BIGINT NOT NULL,
 review_round INT NOT NULL,
 assignee_id BIGINT NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
 version INT NOT NULL DEFAULT 0,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 completed_time DATETIME NULL,
 UNIQUE KEY uk_storage_review_round(document_id,review_round),
 KEY idx_storage_task_person(factory_id,assignee_id,status,id),
 FOREIGN KEY(factory_id,document_id) REFERENCES metal_storage_document(factory_id,id),
 FOREIGN KEY(assignee_id) REFERENCES sys_user(user_id),
 CHECK(status IN ('OPEN','DONE','CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE metal_storage_operation (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 factory_id BIGINT NOT NULL,
 actor_id BIGINT NOT NULL,
 request_id CHAR(36) NOT NULL,
 action VARCHAR(48) NOT NULL,
 request_hash CHAR(64) NOT NULL,
 document_id BIGINT NULL,
 result JSON NOT NULL,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_storage_request(factory_id,actor_id,request_id),
 KEY idx_storage_operation_document(factory_id,document_id,id),
 FOREIGN KEY(factory_id) REFERENCES metal_factory(factory_id),
 FOREIGN KEY(actor_id) REFERENCES sys_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE metal_storage_sequence (
 business_date DATE NOT NULL,
 prefix VARCHAR(8) NOT NULL,
 value INT NOT NULL,
 PRIMARY KEY(business_date,prefix),CHECK(value BETWEEN 1 AND 999999)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Permissions are available for explicit role grants; no ordinary role is expanded.
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT p.label,COALESCE((SELECT MIN(menu_id) FROM sys_menu WHERE perms=IF(p.perm LIKE 'metal:work-order:%','metal:work-order:list','metal:warehouse-order:list')),0),p.seq,'#','',1,1,'F','0','0',p.perm,'#','system',NOW()
FROM (SELECT '仓储冲销' label,'metal:warehouse-order:reverse' perm,101 seq
 UNION ALL SELECT '复验任务管理','metal:storage-review:manage',102
 UNION ALL SELECT '材料借还查看','metal:material-debt:list',103
 UNION ALL SELECT '材料归还办理','metal:material-debt:return',104
 UNION ALL SELECT '工单加工执行','metal:work-order:execute',105
 UNION ALL SELECT '异常完工申请','metal:work-order:exception-close',106
 UNION ALL SELECT '异常完工审批','metal:work-order:exception-review',107
 UNION ALL SELECT '仓储配置查看','metal:storage-config:query',108
 UNION ALL SELECT '仓储配置修改','metal:storage-config:edit',109
 UNION ALL SELECT '编辑仓储单据','metal:warehouse-order:edit',110
 UNION ALL SELECT '删除撤销仓储单据','metal:warehouse-order:remove',111) p
WHERE NOT EXISTS(SELECT 1 FROM sys_menu m WHERE m.perms=p.perm);
INSERT INTO sys_role_menu(role_id,menu_id)
SELECT 1,m.menu_id FROM sys_menu m WHERE m.perms IN ('metal:warehouse-order:edit','metal:warehouse-order:remove','metal:warehouse-order:reverse','metal:storage-review:manage','metal:material-debt:list','metal:material-debt:return','metal:work-order:execute','metal:work-order:exception-close','metal:work-order:exception-review','metal:storage-config:query','metal:storage-config:edit') AND NOT EXISTS(SELECT 1 FROM sys_role_menu r WHERE r.role_id=1 AND r.menu_id=m.menu_id);
