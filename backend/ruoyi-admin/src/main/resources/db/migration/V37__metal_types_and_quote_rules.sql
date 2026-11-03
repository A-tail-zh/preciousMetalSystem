-- Add classification only; historical numeric values, JSON and fingerprints remain untouched.
CREATE TABLE metal_type (
 metal_type_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 name VARCHAR(100) NOT NULL,
 create_by VARCHAR(64) NOT NULL DEFAULT '',
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_metal_type_name(name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO metal_type(metal_type_id,name,create_by) VALUES(1,'铱','migration');
CREATE TABLE metal_type_quote_rule (
 metal_type_id BIGINT NOT NULL PRIMARY KEY,
 density DECIMAL(16,2) NOT NULL,
 pi_value DECIMAL(7,2) NOT NULL,
 powder_price DECIMAL(16,2) NULL,
 non_powder_price DECIMAL(16,2) NULL,
 shortage_price DECIMAL(16,2) NULL,
 loss_price DECIMAL(16,2) NULL,
 version INT NOT NULL DEFAULT 0,
 update_by VARCHAR(64) NOT NULL DEFAULT '',
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(metal_type_id) REFERENCES metal_type(metal_type_id),
 CHECK(density>0 AND pi_value>0),
 CHECK(powder_price IS NULL OR powder_price>=0),
 CHECK(non_powder_price IS NULL OR non_powder_price>=0),
 CHECK(shortage_price IS NULL OR shortage_price>=0),
 CHECK(loss_price IS NULL OR loss_price>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO metal_type_quote_rule(metal_type_id,density,pi_value,update_by)
 SELECT 1,ROUND(base_price,2),ROUND(pi_value,2),'migration' FROM metal_quote_rule WHERE rule_id=1;
ALTER TABLE metal_product DROP CHECK chk_product_category,
 MODIFY product_category VARCHAR(160) NOT NULL,
 ADD metal_type_id BIGINT NULL,
 ADD product_type VARCHAR(20) NULL;
UPDATE metal_product SET metal_type_id=1,product_type=SUBSTRING(product_category,2)
 WHERE product_category IN ('铱坩埚弧底','铱坩埚平底','铱坩埚梯形','铱环','铱杆实心','铱杆空心');
ALTER TABLE metal_product MODIFY metal_type_id BIGINT NOT NULL,MODIFY product_type VARCHAR(20) NOT NULL,
 ADD FOREIGN KEY(metal_type_id) REFERENCES metal_type(metal_type_id),
 ADD CONSTRAINT chk_product_type CHECK(product_type IN ('坩埚弧底','坩埚平底','坩埚梯形','环','杆实心','杆空心'));
