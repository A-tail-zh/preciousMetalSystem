CREATE TABLE metal_process_type (
 type_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '工序类型编号',
 type_name VARCHAR(100) NOT NULL COMMENT '工序类型名称',
 create_by VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建人',
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 UNIQUE KEY uk_process_type_name(type_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='全局工序类型目录';
INSERT INTO metal_process_type(type_id,type_name) VALUES
 (1,'粉料'),(2,'旧锅清理'),(3,'碎料'),(4,'融定'),(5,'闸板'),(6,'线切割'),(7,'卷板成型'),(8,'焊接'),(9,'喷砂成品');
ALTER TABLE metal_process DROP CHECK chk_process_type,
 MODIFY COLUMN process_type INT NOT NULL COMMENT '工序类型编号，对应metal_process_type',
 ADD CONSTRAINT chk_process_type CHECK(process_type>0);
