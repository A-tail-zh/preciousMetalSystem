ALTER TABLE sys_oper_log MODIFY oper_param TEXT,MODIFY json_result TEXT,
 ADD event_id VARCHAR(36) NULL,ADD trace_id VARCHAR(32) NULL,ADD actor_user_id BIGINT NULL,ADD actor_factory_id BIGINT NULL,ADD business_factory_id BIGINT NULL,ADD factory_name VARCHAR(100) NULL,ADD actor_name VARCHAR(100) NULL,ADD module_code VARCHAR(80) NULL,ADD action_code VARCHAR(80) NULL,ADD event_key VARCHAR(160) NULL,ADD result_code VARCHAR(32) NULL,ADD business_code VARCHAR(160) NULL,ADD object_type VARCHAR(80) NULL,ADD objects_json JSON NULL,ADD changes_json JSON NULL,ADD error_category VARCHAR(100) NULL,ADD replayed BOOLEAN NOT NULL DEFAULT FALSE,ADD truncated BOOLEAN NOT NULL DEFAULT FALSE,ADD schema_version INT NOT NULL DEFAULT 1,ADD UNIQUE KEY uk_audit_event(event_id),ADD KEY ix_audit_scope(business_factory_id,oper_time),ADD KEY ix_audit_trace(trace_id);
ALTER TABLE sys_logininfor ADD trace_id VARCHAR(32) NULL,ADD event_id VARCHAR(36) NULL,ADD reason_category VARCHAR(80) NULL,ADD UNIQUE KEY uk_login_event(event_id);
CREATE TABLE audit_scope(oper_id BIGINT NOT NULL,factory_id BIGINT NOT NULL,PRIMARY KEY(oper_id,factory_id));
CREATE TABLE audit_outbox(event_id VARCHAR(36) PRIMARY KEY,event_json JSON NOT NULL,status VARCHAR(16) NOT NULL DEFAULT 'PENDING',attempts INT NOT NULL DEFAULT 0,create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,next_retry DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,KEY ix_delivery(status,next_retry));
CREATE TABLE sys_runtime_log(id BIGINT AUTO_INCREMENT PRIMARY KEY,event_id VARCHAR(36) NOT NULL UNIQUE,event_time DATETIME NOT NULL,level VARCHAR(8) NOT NULL,service VARCHAR(80),component VARCHAR(160),event_type VARCHAR(80),summary TEXT,trace_id VARCHAR(32),exception_class VARCHAR(255),detail_json JSON,cost_ms BIGINT,create_time DATETIME DEFAULT CURRENT_TIMESTAMP,KEY ix_runtime_time(event_time),KEY ix_runtime_trace(trace_id));
CREATE TABLE audit_file_cursor(file_name VARCHAR(255) PRIMARY KEY,file_offset BIGINT NOT NULL DEFAULT 0);
CREATE TABLE audit_log_config(id INT PRIMARY KEY,version INT NOT NULL DEFAULT 0,operation_days INT NOT NULL DEFAULT 180,login_days INT NOT NULL DEFAULT 180,runtime_days INT NOT NULL DEFAULT 30,file_days INT NOT NULL DEFAULT 60,archive_days INT NOT NULL DEFAULT 365,slow_request_ms INT NOT NULL DEFAULT 2000,slow_sql_ms INT NOT NULL DEFAULT 1000,cleanup_enabled BOOLEAN NOT NULL DEFAULT FALSE);
INSERT INTO audit_log_config(id) VALUES(1);
CREATE TABLE audit_cleanup_history(id BIGINT AUTO_INCREMENT PRIMARY KEY,business_date DATE NOT NULL UNIQUE,status VARCHAR(20),summary TEXT,create_time DATETIME DEFAULT CURRENT_TIMESTAMP);
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES
(3400,'系统管理',0,8,'audit',NULL,1,1,'M','0','0','','system','admin',NOW()),
(3401,'操作日志',3400,1,'operations','audit/operations',1,1,'C','0','0','monitor:operlog:list','list','admin',NOW()),
(3402,'操作详情',3401,1,'#',NULL,1,1,'F','0','0','monitor:operlog:query','#','admin',NOW()),
(3403,'操作导出',3401,2,'#',NULL,1,1,'F','0','0','monitor:operlog:export','#','admin',NOW()),
(3410,'登录日志',3400,2,'logins','audit/logins',1,1,'C','0','0','monitor:logininfor:list','logininfor','admin',NOW()),
(3420,'系统运行日志',3400,3,'runtime','audit/runtime',1,1,'C','0','0','monitor:runtime-log:list','monitor','admin',NOW()),
(3421,'运行详情',3420,1,'#',NULL,1,1,'F','0','0','monitor:runtime-log:query','#','admin',NOW()),
(3422,'运行导出',3420,2,'#',NULL,1,1,'F','0','0','monitor:runtime-log:export','#','admin',NOW()),
(3430,'日志配置',3400,4,'config','audit/config',1,1,'C','0','0','monitor:log-config:query','edit','admin',NOW()),
(3431,'配置修改',3430,1,'#',NULL,1,1,'F','0','0','monitor:log-config:edit','#','admin',NOW());
