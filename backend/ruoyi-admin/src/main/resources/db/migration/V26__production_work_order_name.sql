UPDATE sys_menu SET menu_name=REPLACE(menu_name,'工单管理','生产工单管理') WHERE perms LIKE 'metal:work-order:%' AND (menu_name='工单管理' OR menu_name LIKE '工单管理/%');
