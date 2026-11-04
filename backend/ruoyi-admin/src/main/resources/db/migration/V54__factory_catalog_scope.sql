-- Existing public records remain unassigned and are never copied into a factory.
ALTER TABLE metal_product ADD factory_id BIGINT NULL,
 DROP INDEX product_code, ADD UNIQUE KEY uq_factory_product_code(factory_id,product_code),
 ADD CONSTRAINT fk_product_factory FOREIGN KEY(factory_id) REFERENCES metal_factory(factory_id);
ALTER TABLE metal_material ADD factory_id BIGINT NULL,
 DROP INDEX material_code, ADD UNIQUE KEY uq_factory_material_code(factory_id,material_code),
 ADD CONSTRAINT fk_material_factory FOREIGN KEY(factory_id) REFERENCES metal_factory(factory_id);
ALTER TABLE metal_process ADD factory_id BIGINT NULL, ADD KEY ix_process_factory(factory_id),
 ADD CONSTRAINT fk_process_factory FOREIGN KEY(factory_id) REFERENCES metal_factory(factory_id);
ALTER TABLE metal_process_type ADD factory_id BIGINT NULL,
 DROP INDEX uk_process_type_name, ADD UNIQUE KEY uq_factory_process_type(factory_id,type_name),
 ADD CONSTRAINT fk_process_type_factory FOREIGN KEY(factory_id) REFERENCES metal_factory(factory_id);
ALTER TABLE metal_type ADD factory_id BIGINT NULL,
 DROP INDEX uk_metal_type_name, ADD UNIQUE KEY uq_factory_metal_name(factory_id,name),
 ADD CONSTRAINT fk_metal_type_factory FOREIGN KEY(factory_id) REFERENCES metal_factory(factory_id);

CREATE TABLE metal_factory_volume_rule (
 factory_id BIGINT NOT NULL PRIMARY KEY,
 pi_value DECIMAL(7,2) NOT NULL,
 curved_bottom_factor DECIMAL(16,2) NOT NULL,
 version INT NOT NULL DEFAULT 0,
 update_by VARCHAR(64) NOT NULL DEFAULT '',
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_factory_volume_rule FOREIGN KEY(factory_id) REFERENCES metal_factory(factory_id),
 CONSTRAINT chk_factory_volume_values CHECK(pi_value>0 AND curved_bottom_factor>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO metal_factory_volume_rule(factory_id,pi_value,curved_bottom_factor,update_by)
 SELECT f.factory_id,r.pi_value,r.curved_bottom_factor,'migration'
 FROM metal_factory f CROSS JOIN metal_volume_rule r WHERE f.del_flag='0' AND r.rule_id=1;
