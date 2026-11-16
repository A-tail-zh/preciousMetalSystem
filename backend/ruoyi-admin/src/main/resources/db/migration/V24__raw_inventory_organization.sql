-- Historical balances and document snapshots are not redistributed or rewritten.
ALTER TABLE metal_inventory
 ADD COLUMN ownership_status VARCHAR(16) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL=普通库存; REVIEW=历史来源待核对',
 ADD COLUMN raw_organization VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin
 GENERATED ALWAYS AS (CASE WHEN category='RAW' THEN COALESCE(TRIM(organization),'') ELSE '' END) STORED,
 ADD CONSTRAINT chk_inventory_ownership CHECK (ownership_status IN ('NORMAL','REVIEW'));

-- Include empty source names and archived/void documents: source ambiguity must not disappear on reversal.
UPDATE metal_inventory i JOIN (
 SELECT l.inventory_id FROM metal_warehouse_order_line l
 JOIN metal_warehouse_order o ON o.order_id=l.order_id
 WHERE l.category='RAW' AND o.type IN ('OPENING','RAW_IN')
 GROUP BY l.inventory_id
 HAVING COUNT(DISTINCT HEX(COALESCE(TRIM(l.organization),'')))>1
) mixed ON mixed.inventory_id=i.inventory_id
SET i.ownership_status='REVIEW' WHERE i.category='RAW';

-- The original unique key also supports the factory FK; create its replacement first.
ALTER TABLE metal_inventory
 ADD UNIQUE KEY uq_inventory_source (factory_id,warehouse_id,category,reference_id,batch_no,raw_organization,ownership_status);
ALTER TABLE metal_inventory DROP INDEX factory_id;
