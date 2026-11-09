ALTER TABLE metal_material
 DROP CHECK chk_material_type,
 MODIFY material_type VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '当前工厂物料类型名称；历史值保留',
 ADD CONSTRAINT chk_material_type_name_nonblank CHECK(CHAR_LENGTH(TRIM(material_type))>0);
