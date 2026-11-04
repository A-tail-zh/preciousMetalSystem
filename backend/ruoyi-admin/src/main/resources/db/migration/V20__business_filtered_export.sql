-- Add business export action only; existing role grants stay unchanged.
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT 4200,'仓储工单导出',menu_id,20,'#',NULL,1,1,'F','0','0','metal:warehouse-order:export','#','admin',NOW()
FROM sys_menu WHERE perms='metal:warehouse-order:list' AND NOT EXISTS(SELECT 1 FROM (SELECT perms FROM sys_menu) x WHERE x.perms='metal:warehouse-order:export') LIMIT 1;
