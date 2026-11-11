ALTER TABLE metal_process_type
 ADD COLUMN builtin_code VARCHAR(16) NULL,
 ADD UNIQUE KEY uq_factory_builtin_process_type(factory_id,builtin_code),
 ADD CONSTRAINT chk_builtin_process_type CHECK(builtin_code IS NULL OR
  (factory_id IS NOT NULL AND ((builtin_code='ACID_WASH' AND type_name='酸洗') OR
   (builtin_code='SMELTING' AND type_name='融定'))));
