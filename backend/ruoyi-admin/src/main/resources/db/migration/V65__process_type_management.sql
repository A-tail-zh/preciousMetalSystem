ALTER TABLE metal_process_type
 ADD version INT NOT NULL DEFAULT 0,
 ADD update_by VARCHAR(64) NOT NULL DEFAULT '',
 ADD update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;
UPDATE metal_process_type SET update_time=create_time;

INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT CONCAT('工序类型',a.label),m.menu_id,40+a.seq,'#','',1,1,'F','0','0',CONCAT('metal:process-type:',a.action),'#','system',NOW()
FROM sys_menu m CROSS JOIN
 (SELECT 'list' action,'查看' label,1 seq UNION ALL SELECT 'add','新增',2 UNION ALL SELECT 'edit','编辑',3 UNION ALL SELECT 'remove','删除',4) a
WHERE m.component='metal/type-management/index';
INSERT INTO sys_role_menu(role_id,menu_id)
SELECT 1,menu_id FROM sys_menu WHERE perms IN ('metal:process-type:list','metal:process-type:add','metal:process-type:edit','metal:process-type:remove');
