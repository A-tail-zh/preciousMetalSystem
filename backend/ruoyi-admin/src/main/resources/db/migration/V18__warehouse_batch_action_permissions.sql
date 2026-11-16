-- Automatic batch actions remain usable after the standalone batch page is retired.
UPDATE sys_menu SET parent_id=(SELECT x.menu_id FROM (SELECT menu_id FROM sys_menu WHERE perms='metal:warehouse-order:list' AND status='0' ORDER BY menu_id LIMIT 1) x),status='0' WHERE perms IN ('metal:process-batch:settle','metal:process-batch:reverse');
