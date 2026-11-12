ALTER TABLE metal_production_execution
 ADD COLUMN auto_created BOOLEAN NOT NULL DEFAULT FALSE AFTER create_by;

