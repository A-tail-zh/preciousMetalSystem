-- Reversal reuses immutable production operations; existing business rows are unchanged.
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
SELECT '生产批次拆合撤销',m.menu_id,91,'#',NULL,1,0,'F','0','0','metal:production-batch:reverse','#','admin',NOW(),'无有效下游时逆向恢复统一生产批次及加工责任'
FROM sys_menu m WHERE m.perms='metal:work-order:list'
 AND NOT EXISTS(SELECT 1 FROM sys_menu existing WHERE existing.perms='metal:production-batch:reverse') LIMIT 1;
