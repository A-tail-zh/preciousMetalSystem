-- Adoption records book responsibility only; historical production events and ledgers stay unchanged.
ALTER TABLE metal_production_execution
 ADD COLUMN legacy_adoption_operation_id BIGINT NULL,
 ADD CONSTRAINT fk_execution_legacy_adoption FOREIGN KEY(factory_id,legacy_adoption_operation_id)
  REFERENCES metal_production_execution_operation(factory_id,id);
ALTER TABLE metal_production_material_source
 ADD COLUMN legacy_adoption_operation_id BIGINT NULL,
 ADD CONSTRAINT fk_material_source_legacy_adoption FOREIGN KEY(factory_id,legacy_adoption_operation_id)
  REFERENCES metal_production_execution_operation(factory_id,id);
ALTER TABLE metal_storage_batch_slice
 ADD COLUMN legacy_adoption_operation_id BIGINT NULL,
 ADD CONSTRAINT fk_storage_slice_legacy_adoption FOREIGN KEY(factory_id,legacy_adoption_operation_id)
  REFERENCES metal_production_execution_operation(factory_id,id);
