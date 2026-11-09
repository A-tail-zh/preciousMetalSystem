ALTER TABLE metal_material_type
    ADD COLUMN material_category VARCHAR(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '物料分类：旧料、粉料、块料；历史记录待补充',
    ADD CONSTRAINT chk_material_type_category CHECK (material_category IN ('旧料','粉料','块料'));
