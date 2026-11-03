INSERT INTO metal_process_type(type_name,create_by)
 SELECT '酸洗','system' WHERE NOT EXISTS(SELECT 1 FROM metal_process_type WHERE type_name='酸洗');
INSERT INTO metal_process(process_name,description,process_type,create_by)
 SELECT '酸洗','独立原料前处理，产出结算后作为金属料领用',type_id,'system'
 FROM metal_process_type t WHERE t.type_name='酸洗'
 AND NOT EXISTS(SELECT 1 FROM metal_process p WHERE p.process_type=t.type_id AND p.del_flag='0');

CREATE TABLE metal_acid_wash_batch (
 acid_wash_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 batch_no VARCHAR(64) NULL UNIQUE,
 factory_id BIGINT NOT NULL,
 customer_id BIGINT NOT NULL,
 process_id BIGINT NOT NULL,
 process_snapshot JSON NOT NULL,
 request_id VARCHAR(36) NOT NULL UNIQUE,
 request_hash CHAR(64) NOT NULL,
 request_actor BIGINT NOT NULL,
 finish_request_id VARCHAR(36) NULL UNIQUE,
 finish_hash CHAR(64) NULL,
 finish_actor BIGINT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
 version INT NOT NULL DEFAULT 0,
 issued_weight DECIMAL(20,6) NOT NULL DEFAULT 0,
 output_weight DECIMAL(20,6) NOT NULL DEFAULT 0,
 returned_weight DECIMAL(20,6) NOT NULL DEFAULT 0,
 loss_weight DECIMAL(20,6) NULL,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 settled_time DATETIME NULL,
 reason VARCHAR(500) NULL,
 FOREIGN KEY(factory_id) REFERENCES metal_factory(factory_id),
 FOREIGN KEY(customer_id) REFERENCES metal_customer(customer_id),
 FOREIGN KEY(process_id) REFERENCES metal_process(process_id),
 CHECK(status IN ('OPEN','SETTLED','VOID')),
 KEY idx_acid_factory(factory_id,acid_wash_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='独立酸洗前处理，无产品工单或件数覆盖';

ALTER TABLE metal_inventory
 ADD raw_material_kind VARCHAR(16) NULL,
 ADD origin_acid_wash_id BIGINT NULL,
 ADD raw_kind_key VARCHAR(16) GENERATED ALWAYS AS (COALESCE(raw_material_kind,'')) STORED,
 ADD CONSTRAINT chk_raw_material_kind CHECK(raw_material_kind IS NULL OR raw_material_kind IN ('POWDER','UNPROCESSED','METAL')),
 ADD CONSTRAINT fk_inventory_acid FOREIGN KEY(origin_acid_wash_id) REFERENCES metal_acid_wash_batch(acid_wash_id);
UPDATE metal_inventory i JOIN metal_material m ON i.category='RAW' AND m.material_id=i.reference_id
 SET i.raw_material_kind=CASE WHEN m.material_type='粉料' THEN 'POWDER' ELSE 'UNPROCESSED' END;
ALTER TABLE metal_inventory ADD UNIQUE KEY uq_inventory_raw_kind(factory_id,warehouse_id,category,reference_id,batch_no,raw_customer_key,ownership_status,raw_kind_key);
ALTER TABLE metal_inventory DROP INDEX uq_inventory_customer_source;
ALTER TABLE metal_warehouse_order
 ADD acid_wash_id BIGINT NULL,
 ADD CONSTRAINT fk_warehouse_order_acid FOREIGN KEY(acid_wash_id) REFERENCES metal_acid_wash_batch(acid_wash_id);
ALTER TABLE metal_warehouse_order_line ADD raw_material_kind VARCHAR(16) NULL;
