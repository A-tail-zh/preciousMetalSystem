-- Preserve the original classification and every existing material ID/reference.
ALTER TABLE metal_material
 DROP CHECK chk_material_type,
 ADD legacy_material_type VARCHAR(30) NULL COMMENT '迁移前物料类型，仅供追溯',
 ADD metal_type_id BIGINT NULL COMMENT '金属类型ID';

UPDATE metal_material SET legacy_material_type=material_type,metal_type_id=1,
 material_type=CASE material_type
  WHEN '铱粉' THEN '粉料' WHEN '铱块' THEN '块料'
  WHEN '旧铱杆' THEN '旧料' WHEN '旧铱坩埚' THEN '旧料'
  ELSE material_type END;

ALTER TABLE metal_material
 MODIFY material_type VARCHAR(30) NOT NULL COMMENT '物料类型（粉料、块料、旧料）',
 MODIFY metal_type_id BIGINT NOT NULL COMMENT '金属类型ID',
 ADD CONSTRAINT fk_material_metal FOREIGN KEY(metal_type_id) REFERENCES metal_type(metal_type_id),
 ADD CONSTRAINT chk_material_type CHECK(material_type IN ('粉料','块料','旧料'));
