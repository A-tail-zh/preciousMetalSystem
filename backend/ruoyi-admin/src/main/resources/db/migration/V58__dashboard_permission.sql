INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT '数据看板',0,0,'index','index',1,1,'C','0','0','metal:dashboard:list','dashboard','system',NOW()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='metal:dashboard:list');

INSERT INTO sys_role_menu(role_id,menu_id)
SELECT 1,m.menu_id FROM sys_menu m
WHERE m.perms='metal:dashboard:list'
AND NOT EXISTS(SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=1 AND rm.menu_id=m.menu_id);
