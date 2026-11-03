INSERT INTO sys_menu(menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT '库存条件导出',4020,20,'#',1,1,'F','0','0','metal:inventory:export','#','system',NOW()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='metal:inventory:export');
