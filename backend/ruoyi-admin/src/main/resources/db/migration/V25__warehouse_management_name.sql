UPDATE sys_menu SET menu_name=REPLACE(menu_name,'仓库字典','仓库管理') WHERE perms LIKE 'metal:warehouse:%';
