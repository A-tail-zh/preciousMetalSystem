-- Validate before persistent DDL: slices partition the existing physical/ownership ledger.
-- Never repair a mismatch by changing historical stock or ownership quantities.
CREATE TEMPORARY TABLE p2_slice_preflight (
 invalid_rows BIGINT NOT NULL,
 CONSTRAINT chk_p2_stock_ownership_balanced CHECK(invalid_rows=0)
);
INSERT INTO p2_slice_preflight(invalid_rows)
SELECT COUNT(*) FROM metal_storage_stock s
LEFT JOIN (
 SELECT factory_id,stock_id,SUM(weight) weight,SUM(quantity) quantity
 FROM metal_storage_ownership GROUP BY factory_id,stock_id
) o ON o.factory_id=s.factory_id AND o.stock_id=s.id
WHERE s.net_weight<>COALESCE(o.weight,0) OR s.quantity<>COALESCE(o.quantity,0);
INSERT INTO p2_slice_preflight(invalid_rows)
SELECT COUNT(*) FROM metal_storage_ownership o
LEFT JOIN metal_storage_stock s ON s.factory_id=o.factory_id AND s.id=o.stock_id
WHERE s.id IS NULL;
DROP TEMPORARY TABLE p2_slice_preflight;

CREATE TABLE metal_storage_batch_slice (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 factory_id BIGINT NOT NULL,
 stock_id BIGINT NOT NULL,
 batch_id BIGINT NULL,
 batch_key BIGINT GENERATED ALWAYS AS (COALESCE(batch_id,0)) STORED,
 slice_kind VARCHAR(16) NOT NULL,
 weight DECIMAL(20,2) NOT NULL DEFAULT 0,
 quantity BIGINT NOT NULL DEFAULT 0,
 version INT NOT NULL DEFAULT 0,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_storage_slice_scope(factory_id,id),
 UNIQUE KEY uk_storage_slice_stock_scope(factory_id,stock_id,id),
 UNIQUE KEY uk_storage_slice_batch(factory_id,stock_id,batch_key),
 KEY idx_storage_slice_batch(factory_id,batch_id,id),
 CONSTRAINT fk_storage_slice_stock FOREIGN KEY(factory_id,stock_id)
  REFERENCES metal_storage_stock(factory_id,id),
 CONSTRAINT fk_storage_slice_batch FOREIGN KEY(factory_id,batch_id)
  REFERENCES metal_production_batch(factory_id,id),
 CONSTRAINT chk_storage_slice_kind CHECK((slice_kind='UNASSIGNED' AND batch_id IS NULL) OR (slice_kind='BATCH' AND batch_id IS NOT NULL)),
 CONSTRAINT chk_storage_slice_balance CHECK(weight>=0 AND quantity>=0),
 CONSTRAINT chk_storage_slice_version CHECK(version>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE metal_storage_batch_slice_owner (
 factory_id BIGINT NOT NULL,
 stock_slice_id BIGINT NOT NULL,
 owner_id BIGINT NOT NULL,
 weight DECIMAL(20,2) NOT NULL DEFAULT 0,
 quantity BIGINT NOT NULL DEFAULT 0,
 PRIMARY KEY(stock_slice_id,owner_id),
 CONSTRAINT fk_storage_slice_owner FOREIGN KEY(factory_id,stock_slice_id)
  REFERENCES metal_storage_batch_slice(factory_id,id),
 CONSTRAINT chk_storage_slice_owner_balance CHECK(owner_id>=0 AND weight>=0 AND quantity>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Historical inventory is deliberately unassigned; origin batch is never guessed.
INSERT INTO metal_storage_batch_slice(factory_id,stock_id,batch_id,slice_kind,weight,quantity)
SELECT factory_id,id,NULL,'UNASSIGNED',net_weight,quantity FROM metal_storage_stock;
INSERT INTO metal_storage_batch_slice_owner(factory_id,stock_slice_id,owner_id,weight,quantity)
SELECT o.factory_id,s.id,o.owner_id,o.weight,o.quantity
FROM metal_storage_ownership o
JOIN metal_storage_batch_slice s ON s.factory_id=o.factory_id AND s.stock_id=o.stock_id AND s.batch_key=0;

ALTER TABLE metal_storage_event
 ADD UNIQUE KEY uk_storage_event_scope(factory_id,id),
 ADD COLUMN stock_slice_id BIGINT NULL,
 ADD CONSTRAINT fk_storage_event_slice FOREIGN KEY(factory_id,stock_slice_id)
  REFERENCES metal_storage_batch_slice(factory_id,id);

CREATE TABLE metal_storage_batch_slice_entry (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 event_id BIGINT NOT NULL,
 factory_id BIGINT NOT NULL,
 stock_id BIGINT NOT NULL,
 stock_slice_id BIGINT NOT NULL,
 owner_id BIGINT NOT NULL,
 weight_delta DECIMAL(20,2) NOT NULL,
 quantity_delta BIGINT NOT NULL,
 reason VARCHAR(64) NOT NULL,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY idx_storage_slice_entry_event(factory_id,event_id,id),
 KEY idx_storage_slice_entry_slice(factory_id,stock_slice_id,id),
 CONSTRAINT fk_storage_slice_entry_event FOREIGN KEY(factory_id,event_id)
  REFERENCES metal_storage_event(factory_id,id),
 CONSTRAINT fk_storage_slice_entry_slice FOREIGN KEY(factory_id,stock_id,stock_slice_id)
  REFERENCES metal_storage_batch_slice(factory_id,stock_id,id),
 CONSTRAINT chk_storage_slice_entry_owner CHECK(owner_id>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE metal_storage_reservation
 ADD COLUMN stock_slice_id BIGINT NULL,
 ADD KEY idx_storage_reservation_slice(factory_id,stock_slice_id,owner_id,session_id),
 ADD CONSTRAINT fk_storage_reservation_slice FOREIGN KEY(factory_id,stock_id,stock_slice_id)
  REFERENCES metal_storage_batch_slice(factory_id,stock_id,id);
ALTER TABLE metal_storage_issue_allocation
 ADD COLUMN stock_slice_id BIGINT NULL,
 ADD KEY idx_storage_issue_slice(factory_id,stock_slice_id,id),
 ADD CONSTRAINT fk_storage_issue_slice FOREIGN KEY(factory_id,stock_id,stock_slice_id)
  REFERENCES metal_storage_batch_slice(factory_id,stock_id,id);
ALTER TABLE metal_storage_document
 ADD COLUMN batch_id BIGINT NULL,
 ADD KEY idx_storage_document_batch(factory_id,batch_id,status,id),
 ADD CONSTRAINT fk_storage_document_batch FOREIGN KEY(factory_id,batch_id)
  REFERENCES metal_production_batch(factory_id,id);
