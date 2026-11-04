ALTER TABLE metal_warehouse_order ADD COLUMN operator_ids JSON NULL, MODIFY COLUMN operator_name TEXT NOT NULL;
UPDATE metal_warehouse_order SET operator_ids=JSON_ARRAY(CAST(operator_id AS CHAR));
