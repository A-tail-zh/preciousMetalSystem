-- Explicit process-skip permissions. No historical data rewriting or role grants.
CREATE TABLE metal_route_skip_permit (
 permit_id BIGINT AUTO_INCREMENT PRIMARY KEY,factory_id BIGINT NOT NULL,product_id BIGINT NOT NULL,
 specification VARCHAR(500) NOT NULL,route_hash CHAR(64) NOT NULL,route_snapshot JSON NOT NULL,
 source_sequence INT NOT NULL,target_sequence INT NOT NULL,enabled BOOLEAN NOT NULL DEFAULT TRUE,
 version INT NOT NULL DEFAULT 0,request_id CHAR(36) NOT NULL UNIQUE,request_hash CHAR(64) NOT NULL,
 actor_id BIGINT NOT NULL,reason VARCHAR(500) NOT NULL,create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CHECK(source_sequence>=1 AND target_sequence>source_sequence+1),
 KEY(factory_id,product_id,route_hash,enabled)
);
CREATE TABLE metal_route_skip_permit_version (
 permit_id BIGINT NOT NULL,version INT NOT NULL,snapshot JSON NOT NULL,actor_id BIGINT NOT NULL,
 request_id CHAR(36) NOT NULL UNIQUE,request_hash CHAR(64) NOT NULL,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(permit_id,version),
 FOREIGN KEY(permit_id) REFERENCES metal_route_skip_permit(permit_id)
);
ALTER TABLE metal_process_batch ADD execution_snapshot JSON NULL;
CREATE TABLE metal_route_skip_use (
 line_id BIGINT PRIMARY KEY,permit_id BIGINT NOT NULL,permit_version INT NOT NULL,
 item_id BIGINT NOT NULL,batch_id BIGINT NOT NULL,new_skipped JSON NOT NULL,execution_snapshot JSON NOT NULL,
 FOREIGN KEY(line_id) REFERENCES metal_warehouse_order_line(line_id),
 FOREIGN KEY(permit_id,permit_version) REFERENCES metal_route_skip_permit_version(permit_id,version),
 KEY(item_id,batch_id)
);
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
VALUES(3811,'工艺跳步许可配置',3300,11,'#',1,0,'F','0','0','metal:route-skip:configure','#','system',NOW()),
(3812,'整单重量总账导出',3300,12,'#',1,0,'F','0','0','metal:work-order:weight-export','#','system',NOW());
