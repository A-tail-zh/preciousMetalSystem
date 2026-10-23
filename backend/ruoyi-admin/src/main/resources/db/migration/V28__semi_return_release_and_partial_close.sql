-- Preserve all existing business rows and provenance. New grants are not assigned to roles.
ALTER TABLE metal_work_order DROP CHECK chk_wo_status,
 ADD CONSTRAINT chk_wo_status CHECK(status IN ('PENDING','REJECTED','APPROVED','WAITING_MATERIAL','IN_PROGRESS','COMPLETED','CLOSED_PARTIAL'));
CREATE TABLE metal_work_order_close (
 close_id BIGINT AUTO_INCREMENT PRIMARY KEY,work_order_id BIGINT NOT NULL,factory_id BIGINT NOT NULL,
 request_id VARCHAR(36) NOT NULL UNIQUE,request_hash CHAR(64) NOT NULL,actor_id BIGINT NOT NULL,
 action VARCHAR(12) NOT NULL,reason VARCHAR(500) NOT NULL,snapshot JSON NOT NULL,create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(work_order_id) REFERENCES metal_work_order(work_order_id),KEY(work_order_id,close_id)
);
CREATE TABLE metal_semi_release (
 release_id BIGINT AUTO_INCREMENT PRIMARY KEY,inventory_id BIGINT NOT NULL,factory_id BIGINT NOT NULL,work_order_id BIGINT NOT NULL,
 request_id VARCHAR(36) NOT NULL UNIQUE,request_hash CHAR(64) NOT NULL,actor_id BIGINT NOT NULL,
 quantity BIGINT NOT NULL,net_weight DECIMAL(20,6) NOT NULL,reason VARCHAR(500) NOT NULL,status VARCHAR(12) NOT NULL DEFAULT 'ACTIVE',version INT NOT NULL DEFAULT 0,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,cancel_reason VARCHAR(500),cancel_actor BIGINT,cancel_time DATETIME,
 FOREIGN KEY(inventory_id) REFERENCES metal_inventory(inventory_id),FOREIGN KEY(work_order_id) REFERENCES metal_work_order(work_order_id),
 CHECK(quantity>0 AND net_weight>0),CHECK(status IN ('ACTIVE','CANCELLED')),KEY(inventory_id,status),KEY(work_order_id,status)
);
CREATE TABLE metal_semi_release_use (
 use_id BIGINT AUTO_INCREMENT PRIMARY KEY,release_id BIGINT NOT NULL,line_id BIGINT NOT NULL UNIQUE,
 route_snapshot JSON NOT NULL,FOREIGN KEY(release_id) REFERENCES metal_semi_release(release_id),
 FOREIGN KEY(line_id) REFERENCES metal_warehouse_order_line(line_id),KEY(release_id,line_id)
);
CREATE TABLE metal_semi_flow_event (
 event_id BIGINT AUTO_INCREMENT PRIMARY KEY,request_id VARCHAR(36) UNIQUE,request_hash CHAR(64),release_id BIGINT NOT NULL,action VARCHAR(24) NOT NULL,actor_id BIGINT NOT NULL,reason VARCHAR(500) NOT NULL,create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(release_id) REFERENCES metal_semi_release(release_id)
);
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT 3800+n, label,3300,n,'#',NULL,1,0,'F','0','0',permission,'#','system',NOW() FROM (
 SELECT 1 n,'部分完工关闭' label,'metal:work-order:partial-close' permission UNION ALL
 SELECT 2,'撤销部分关闭','metal:work-order:reopen' UNION ALL
 SELECT 3,'半成品释放','metal:semi-release:add' UNION ALL
 SELECT 4,'撤销半成品释放','metal:semi-release:cancel'
) permission_rows;
