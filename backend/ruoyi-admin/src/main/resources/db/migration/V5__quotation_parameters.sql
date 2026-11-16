CREATE TABLE metal_quote_rule (
 rule_id BIGINT NOT NULL PRIMARY KEY,
 base_price DECIMAL(20,6) NOT NULL,
 pi_value DECIMAL(20,15) NOT NULL,
 version INT NOT NULL DEFAULT 0,
 update_by VARCHAR(64) NOT NULL DEFAULT '',
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT chk_quote_price CHECK(base_price > 0),
 CONSTRAINT chk_quote_pi CHECK(pi_value > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO metal_quote_rule(rule_id,base_price,pi_value) VALUES(1,22.65,3.141592653589793);
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES
(3200,'报价管理',0,1,'quotation',NULL,1,1,'M','0','0','','money','system',NOW()),
(3201,'报价规则管理',3200,1,'/metal/quote-rule','metal/quote-rule/index',1,1,'C','0','0','metal:quote-rule:list','money','system',NOW()),
(3202,'报价规则修改',3201,1,'#','',1,1,'F','0','0','metal:quote-rule:edit','#','system',NOW()),
(3203,'自动报价单',3200,2,'/metal/quotation','metal/quotation/index',1,1,'C','0','0','metal:quotation:list','money','system',NOW());
INSERT INTO sys_role_menu(role_id,menu_id) VALUES(1,3200),(1,3201),(1,3202),(1,3203);
