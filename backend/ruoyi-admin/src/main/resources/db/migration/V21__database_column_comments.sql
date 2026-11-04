-- 补充缺失的字段说明；保留原字段类型、默认值、可空性、生成表达式及索引。
-- 不改写历史迁移，也不更新业务数据。

ALTER TABLE `audit_cleanup_history`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录主键ID',
  MODIFY COLUMN `business_date` date NOT NULL COMMENT '清理任务对应的业务日期',
  MODIFY COLUMN `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '日志清理任务执行状态（RUNNING执行中，SUCCESS成功，FAILED失败）',
  MODIFY COLUMN `summary` text COLLATE utf8mb4_unicode_ci COMMENT '摘要说明',
  MODIFY COLUMN `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `stage` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OPERATION' COMMENT '日志清理阶段',
  MODIFY COLUMN `cutoffs_json` json DEFAULT NULL COMMENT '各类日志清理截止时间快照（JSON）',
  MODIFY COLUMN `operation_count` bigint NOT NULL DEFAULT '0' COMMENT '已清理的操作日志条数',
  MODIFY COLUMN `login_count` bigint NOT NULL DEFAULT '0' COMMENT '已清理的登录日志条数',
  MODIFY COLUMN `runtime_count` bigint NOT NULL DEFAULT '0' COMMENT '已清理的运行日志条数',
  MODIFY COLUMN `outbox_count` bigint NOT NULL DEFAULT '0' COMMENT '已清理的审计投递事件条数',
  MODIFY COLUMN `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后修改时间';

ALTER TABLE `audit_file_cursor`
  MODIFY COLUMN `file_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '日志文件名称',
  MODIFY COLUMN `file_offset` bigint NOT NULL DEFAULT '0' COMMENT '日志文件已消费的字节偏移量';

ALTER TABLE `audit_log_config`
  MODIFY COLUMN `id` int NOT NULL COMMENT '记录主键ID',
  MODIFY COLUMN `version` int NOT NULL DEFAULT '0' COMMENT '数据版本号，用于乐观锁校验',
  MODIFY COLUMN `operation_days` int NOT NULL DEFAULT '180' COMMENT '操作日志保留天数',
  MODIFY COLUMN `login_days` int NOT NULL DEFAULT '180' COMMENT '登录日志保留天数',
  MODIFY COLUMN `runtime_days` int NOT NULL DEFAULT '30' COMMENT '运行日志保留天数',
  MODIFY COLUMN `file_days` int NOT NULL DEFAULT '60' COMMENT '本地日志文件保留天数',
  MODIFY COLUMN `archive_days` int NOT NULL DEFAULT '365' COMMENT '日志归档保留天数',
  MODIFY COLUMN `slow_request_ms` int NOT NULL DEFAULT '2000' COMMENT '慢请求阈值（毫秒）',
  MODIFY COLUMN `slow_sql_ms` int NOT NULL DEFAULT '1000' COMMENT '慢SQL阈值（毫秒）',
  MODIFY COLUMN `cleanup_enabled` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否启用自动清理（0否，1是）';

ALTER TABLE `audit_outbox`
  MODIFY COLUMN `event_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '事件唯一标识，用于追踪及去重',
  MODIFY COLUMN `event_json` json NOT NULL COMMENT '待投递审计事件内容（JSON）',
  MODIFY COLUMN `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '审计事件投递状态（PENDING待投递，DELIVERED已投递）',
  MODIFY COLUMN `attempts` int NOT NULL DEFAULT '0' COMMENT '事件投递尝试次数',
  MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `next_retry` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下次允许重试投递时间',
  MODIFY COLUMN `delivered_time` datetime DEFAULT NULL COMMENT '事件投递完成时间';

ALTER TABLE `audit_scope`
  MODIFY COLUMN `oper_id` bigint NOT NULL COMMENT '操作日志ID',
  MODIFY COLUMN `factory_id` bigint NOT NULL COMMENT '所属工厂ID';

ALTER TABLE `metal_factory`
  MODIFY COLUMN `factory_id` bigint NOT NULL AUTO_INCREMENT COMMENT '所属工厂ID',
  MODIFY COLUMN `factory_name` varchar(100) NOT NULL COMMENT '工厂名称',
  MODIFY COLUMN `address` varchar(500) NOT NULL COMMENT '详细地址',
  MODIFY COLUMN `introduction` text COMMENT '简介',
  MODIFY COLUMN `create_by` varchar(64) NOT NULL DEFAULT '' COMMENT '创建人登录账号',
  MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `update_by` varchar(64) NOT NULL DEFAULT '' COMMENT '最后修改人登录账号',
  MODIFY COLUMN `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后修改时间',
  MODIFY COLUMN `del_flag` char(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除标记（0正常，2已删除）',
  MODIFY COLUMN `version` int NOT NULL DEFAULT '0' COMMENT '数据版本号，用于乐观锁校验';

ALTER TABLE `metal_inventory`
  MODIFY COLUMN `inventory_id` bigint NOT NULL AUTO_INCREMENT COMMENT '库存记录ID',
  MODIFY COLUMN `factory_id` bigint NOT NULL COMMENT '所属工厂ID',
  MODIFY COLUMN `warehouse_id` bigint NOT NULL COMMENT '库房ID',
  MODIFY COLUMN `category` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '库存分类（RAW原材料，FINISHED成品，SEMI半成品）',
  MODIFY COLUMN `reference_id` bigint NOT NULL COMMENT '资料ID，RAW关联物料，FINISHED和SEMI关联产品',
  MODIFY COLUMN `batch_no` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '批次编号',
  MODIFY COLUMN `code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物料或产品编码快照',
  MODIFY COLUMN `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物料或产品名称快照',
  MODIFY COLUMN `specification` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '规格型号',
  MODIFY COLUMN `purity` decimal(12,6) DEFAULT NULL COMMENT '纯度（%，范围0至100）',
  MODIFY COLUMN `organization` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '单位名称',
  MODIFY COLUMN `quantity` bigint NOT NULL DEFAULT '0' COMMENT '数量（件）；原材料按净重管理，数量为0',
  MODIFY COLUMN `net_weight` decimal(20,6) NOT NULL DEFAULT '0.000000' COMMENT '净重（g）',
  MODIFY COLUMN `origin_batch_id` bigint DEFAULT NULL COMMENT '来源加工批次ID',
  MODIFY COLUMN `version` int NOT NULL DEFAULT '0' COMMENT '数据版本号，用于乐观锁校验',
  MODIFY COLUMN `del_flag` char(1) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除标记（0正常，2已删除）',
  MODIFY COLUMN `update_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '最后修改时间';

ALTER TABLE `metal_line_product`
  MODIFY COLUMN `production_line_id` bigint NOT NULL COMMENT '产线ID',
  MODIFY COLUMN `product_id` bigint NOT NULL COMMENT '产品ID';

ALTER TABLE `metal_material`
  MODIFY COLUMN `material_id` bigint NOT NULL AUTO_INCREMENT COMMENT '物料ID',
  MODIFY COLUMN `material_code` varchar(64) NOT NULL COMMENT '物料编码',
  MODIFY COLUMN `material_name` varchar(100) NOT NULL COMMENT '物料名称',
  MODIFY COLUMN `material_type` varchar(30) NOT NULL COMMENT '物料类型（旧铱坩埚、旧铱杆、铱块、铱粉）',
  MODIFY COLUMN `specification` varchar(200) DEFAULT NULL COMMENT '规格型号',
  MODIFY COLUMN `create_by` varchar(64) NOT NULL DEFAULT '' COMMENT '创建人登录账号',
  MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `update_by` varchar(64) NOT NULL DEFAULT '' COMMENT '最后修改人登录账号',
  MODIFY COLUMN `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后修改时间',
  MODIFY COLUMN `del_flag` char(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除标记（0正常，2已删除）',
  MODIFY COLUMN `version` int NOT NULL DEFAULT '0' COMMENT '数据版本号，用于乐观锁校验';

ALTER TABLE `metal_process`
  MODIFY COLUMN `process_id` bigint NOT NULL AUTO_INCREMENT COMMENT '工序ID',
  MODIFY COLUMN `process_name` varchar(100) NOT NULL COMMENT '工序名称',
  MODIFY COLUMN `description` varchar(2000) DEFAULT NULL COMMENT '描述说明',
  MODIFY COLUMN `process_type` tinyint NOT NULL COMMENT '工序类型编号（1至9，对应系统工序类型定义）',
  MODIFY COLUMN `create_by` varchar(64) NOT NULL DEFAULT '' COMMENT '创建人登录账号',
  MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `update_by` varchar(64) NOT NULL DEFAULT '' COMMENT '最后修改人登录账号',
  MODIFY COLUMN `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后修改时间',
  MODIFY COLUMN `del_flag` char(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除标记（0正常，2已删除）',
  MODIFY COLUMN `version` int NOT NULL DEFAULT '0' COMMENT '数据版本号，用于乐观锁校验';

ALTER TABLE `metal_process_batch`
  MODIFY COLUMN `batch_id` bigint NOT NULL AUTO_INCREMENT COMMENT '加工批次ID',
  MODIFY COLUMN `request_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '请求唯一标识，用于幂等校验',
  MODIFY COLUMN `request_hash` char(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '请求内容摘要，用于校验重复请求的一致性',
  MODIFY COLUMN `request_actor` bigint NOT NULL COMMENT '发起请求的用户ID',
  MODIFY COLUMN `factory_id` bigint NOT NULL COMMENT '所属工厂ID',
  MODIFY COLUMN `work_order_id` bigint NOT NULL COMMENT '生产工单ID',
  MODIFY COLUMN `item_id` bigint NOT NULL COMMENT '工单产品明细ID',
  MODIFY COLUMN `process_id` bigint NOT NULL COMMENT '工序ID',
  MODIFY COLUMN `sequence_no` int NOT NULL COMMENT '顺序号，从1开始',
  MODIFY COLUMN `planned_quantity` int NOT NULL COMMENT '计划加工数量（件）',
  MODIFY COLUMN `completed_quantity` int NOT NULL DEFAULT '0' COMMENT '完成数量（件）',
  MODIFY COLUMN `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OPEN' COMMENT '加工批次状态（OPEN未结算，SETTLED已结算，VOID已冲销）',
  MODIFY COLUMN `version` int NOT NULL DEFAULT '0' COMMENT '数据版本号，用于乐观锁校验',
  MODIFY COLUMN `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `batch_no` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批次编号';

ALTER TABLE `metal_product`
  MODIFY COLUMN `product_id` bigint NOT NULL AUTO_INCREMENT COMMENT '产品ID',
  MODIFY COLUMN `product_code` varchar(64) NOT NULL COMMENT '产品编号',
  MODIFY COLUMN `product_name` varchar(100) NOT NULL COMMENT '产品名称',
  MODIFY COLUMN `product_category` varchar(30) NOT NULL COMMENT '产品类别（铱坩埚弧底、铱坩埚平底、铱坩埚梯形、铱环、铱杆实心、铱杆空心）',
  MODIFY COLUMN `specification` varchar(200) DEFAULT NULL COMMENT '规格型号',
  MODIFY COLUMN `dimensions` json NOT NULL COMMENT '产品尺寸参数（JSON，尺寸单位cm）',
  MODIFY COLUMN `volume` decimal(20,6) DEFAULT NULL COMMENT '产品体积（cm³）',
  MODIFY COLUMN `purity` decimal(20,6) DEFAULT NULL COMMENT '纯度（%，范围0至100）',
  MODIFY COLUMN `unit` varchar(20) NOT NULL COMMENT '历史单位字段，当前产品新增时写入空字符串',
  MODIFY COLUMN `theoretical_weight` decimal(20,6) DEFAULT NULL COMMENT '理论成品净重（g）',
  MODIFY COLUMN `create_by` varchar(64) NOT NULL DEFAULT '' COMMENT '创建人登录账号',
  MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `update_by` varchar(64) NOT NULL DEFAULT '' COMMENT '最后修改人登录账号',
  MODIFY COLUMN `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后修改时间',
  MODIFY COLUMN `del_flag` char(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除标记（0正常，2已删除）',
  MODIFY COLUMN `version` int NOT NULL DEFAULT '0' COMMENT '数据版本号，用于乐观锁校验';

ALTER TABLE `metal_product_process`
  MODIFY COLUMN `product_id` bigint NOT NULL COMMENT '产品ID',
  MODIFY COLUMN `process_id` bigint NOT NULL COMMENT '工序ID',
  MODIFY COLUMN `sequence_no` int NOT NULL COMMENT '顺序号，从1开始',
  MODIFY COLUMN `theoretical_loss` decimal(20,6) NOT NULL COMMENT '单件理论损耗（g）';

ALTER TABLE `metal_production_line`
  MODIFY COLUMN `production_line_id` bigint NOT NULL AUTO_INCREMENT COMMENT '产线ID',
  MODIFY COLUMN `production_line_code` varchar(64) NOT NULL COMMENT '产线编号',
  MODIFY COLUMN `production_line_name` varchar(100) NOT NULL COMMENT '产线名称',
  MODIFY COLUMN `factory_id` bigint DEFAULT NULL COMMENT '所属工厂ID',
  MODIFY COLUMN `create_by` varchar(64) NOT NULL DEFAULT '' COMMENT '创建人登录账号',
  MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `update_by` varchar(64) NOT NULL DEFAULT '' COMMENT '最后修改人登录账号',
  MODIFY COLUMN `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后修改时间',
  MODIFY COLUMN `version` int NOT NULL DEFAULT '0' COMMENT '数据版本号，用于乐观锁校验';

ALTER TABLE `metal_quote_rule`
  MODIFY COLUMN `rule_id` bigint NOT NULL COMMENT '规则ID',
  MODIFY COLUMN `base_price` decimal(20,6) NOT NULL COMMENT '基础单价（按产品体积参与报价计算）',
  MODIFY COLUMN `pi_value` decimal(20,15) NOT NULL COMMENT '计算体积使用的圆周率',
  MODIFY COLUMN `version` int NOT NULL DEFAULT '0' COMMENT '数据版本号，用于乐观锁校验',
  MODIFY COLUMN `update_by` varchar(64) NOT NULL DEFAULT '' COMMENT '最后修改人登录账号',
  MODIFY COLUMN `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后修改时间';

ALTER TABLE `metal_report_material`
  MODIFY COLUMN `report_material_id` bigint NOT NULL AUTO_INCREMENT COMMENT '报工物料损耗明细ID',
  MODIFY COLUMN `report_id` bigint NOT NULL COMMENT '损耗报工ID',
  MODIFY COLUMN `code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物料或产品编码快照',
  MODIFY COLUMN `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '名称',
  MODIFY COLUMN `issued_weight` decimal(20,6) NOT NULL COMMENT '领用净重合计（g）',
  MODIFY COLUMN `loss_weight` decimal(20,6) NOT NULL COMMENT '实际损耗净重（g）',
  MODIFY COLUMN `average_loss` decimal(20,6) NOT NULL COMMENT '单件平均损耗（g/件）';

ALTER TABLE `metal_saved_quotation`
  MODIFY COLUMN `quote_id` bigint NOT NULL AUTO_INCREMENT COMMENT '已保存报价单ID',
  MODIFY COLUMN `quote_no` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '报价单编号',
  MODIFY COLUMN `request_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '请求唯一标识，用于幂等校验',
  MODIFY COLUMN `request_hash` char(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '请求内容摘要，用于校验重复请求的一致性',
  MODIFY COLUMN `request_actor` bigint NOT NULL COMMENT '发起请求的用户ID',
  MODIFY COLUMN `factory_id` bigint NOT NULL COMMENT '所属工厂ID',
  MODIFY COLUMN `category_labels` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '报价单产品类别展示文本',
  MODIFY COLUMN `item_snapshot` json NOT NULL COMMENT '报价明细快照（JSON）',
  MODIFY COLUMN `total_price` decimal(32,6) NOT NULL COMMENT '报价总金额',
  MODIFY COLUMN `rule_version` int NOT NULL COMMENT '计算时采用的规则版本号',
  MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

ALTER TABLE `metal_stock_ledger`
  MODIFY COLUMN `ledger_id` bigint NOT NULL AUTO_INCREMENT COMMENT '库存流水ID',
  MODIFY COLUMN `inventory_id` bigint NOT NULL COMMENT '库存记录ID',
  MODIFY COLUMN `order_id` bigint NOT NULL COMMENT '仓储单据ID',
  MODIFY COLUMN `line_id` bigint NOT NULL COMMENT '仓储单据明细ID',
  MODIFY COLUMN `delta_weight` decimal(20,6) NOT NULL COMMENT '本次净重变动（g，正数增加，负数减少）',
  MODIFY COLUMN `delta_quantity` bigint NOT NULL COMMENT '本次数量变动（件，正数增加，负数减少）',
  MODIFY COLUMN `balance_weight` decimal(20,6) NOT NULL COMMENT '变动后的库存净重余额（g）',
  MODIFY COLUMN `balance_quantity` bigint NOT NULL COMMENT '变动后的库存数量余额（件）',
  MODIFY COLUMN `reversal_of` bigint DEFAULT NULL COMMENT '被冲销的原库存流水ID',
  MODIFY COLUMN `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

ALTER TABLE `metal_stock_sequence`
  MODIFY COLUMN `sequence_date` date NOT NULL COMMENT '编号序列对应日期',
  MODIFY COLUMN `prefix` varchar(8) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '单据编号前缀',
  MODIFY COLUMN `sequence_value` int NOT NULL COMMENT '当前已使用的流水序号';

ALTER TABLE `metal_warehouse`
  MODIFY COLUMN `warehouse_id` bigint NOT NULL AUTO_INCREMENT COMMENT '库房ID',
  MODIFY COLUMN `factory_id` bigint NOT NULL COMMENT '所属工厂ID',
  MODIFY COLUMN `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '库房名称',
  MODIFY COLUMN `sort_order` int NOT NULL DEFAULT '0' COMMENT '显示顺序（非负整数）',
  MODIFY COLUMN `status` char(1) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '库房状态（0启用，1停用）',
  MODIFY COLUMN `version` int NOT NULL DEFAULT '0' COMMENT '数据版本号，用于乐观锁校验',
  MODIFY COLUMN `del_flag` char(1) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除标记（0正常，2已删除）',
  MODIFY COLUMN `create_by` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '创建人登录账号',
  MODIFY COLUMN `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `update_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '最后修改时间';

ALTER TABLE `metal_warehouse_number`
  MODIFY COLUMN `request_id` varchar(36) NOT NULL COMMENT '请求唯一标识，用于幂等校验',
  MODIFY COLUMN `actor_id` bigint NOT NULL COMMENT '操作人用户ID',
  MODIFY COLUMN `kind` varchar(8) NOT NULL COMMENT '单据类别（OUT出库，IN入库，RET还库，ADJ调整）',
  MODIFY COLUMN `order_no` varchar(32) DEFAULT NULL COMMENT '单据编号',
  MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

ALTER TABLE `metal_warehouse_order`
  MODIFY COLUMN `order_id` bigint NOT NULL AUTO_INCREMENT COMMENT '仓储单据ID',
  MODIFY COLUMN `order_no` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '单据编号',
  MODIFY COLUMN `request_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '请求唯一标识，用于幂等校验',
  MODIFY COLUMN `request_hash` char(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '请求内容摘要，用于校验重复请求的一致性',
  MODIFY COLUMN `request_actor` bigint NOT NULL COMMENT '发起请求的用户ID',
  MODIFY COLUMN `factory_id` bigint NOT NULL COMMENT '所属工厂ID',
  MODIFY COLUMN `warehouse_id` bigint NOT NULL COMMENT '库房ID',
  MODIFY COLUMN `kind` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '单据类别（OUT出库，IN入库，RET还库，ADJ调整）',
  MODIFY COLUMN `type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '仓储业务类型编码（生产出库、产品出库、入库、还库、期初或调整等）',
  MODIFY COLUMN `order_date` date NOT NULL COMMENT '单据业务日期',
  MODIFY COLUMN `work_order_id` bigint DEFAULT NULL COMMENT '生产工单ID',
  MODIFY COLUMN `batch_id` bigint DEFAULT NULL COMMENT '加工批次ID',
  MODIFY COLUMN `operator_id` bigint DEFAULT NULL COMMENT '经手人用户ID（多经手人时保留首个）',
  MODIFY COLUMN `keeper_id` bigint NOT NULL COMMENT '仓管员用户ID',
  MODIFY COLUMN `recipient_id` bigint DEFAULT NULL COMMENT '领用人用户ID',
  MODIFY COLUMN `inspector_id` bigint DEFAULT NULL COMMENT '检验人用户ID',
  MODIFY COLUMN `reviewer_id` bigint DEFAULT NULL COMMENT '审核人用户ID',
  MODIFY COLUMN `operator_name` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '经手人姓名快照',
  MODIFY COLUMN `keeper_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '仓管员姓名快照',
  MODIFY COLUMN `recipient_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '领用人姓名快照',
  MODIFY COLUMN `inspector_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '检验人姓名快照',
  MODIFY COLUMN `reviewer_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '审核人姓名快照',
  MODIFY COLUMN `warehouse_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '库房名称快照',
  MODIFY COLUMN `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'POSTED' COMMENT '单据状态（POSTED已生效，VOID已冲销）',
  MODIFY COLUMN `reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '业务操作原因或说明',
  MODIFY COLUMN `version` int NOT NULL DEFAULT '0' COMMENT '数据版本号，用于乐观锁校验',
  MODIFY COLUMN `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `operator_ids` json DEFAULT NULL COMMENT '经手人用户ID列表（JSON）';

ALTER TABLE `metal_warehouse_order_line`
  MODIFY COLUMN `line_id` bigint NOT NULL AUTO_INCREMENT COMMENT '仓储单据明细ID',
  MODIFY COLUMN `order_id` bigint NOT NULL COMMENT '仓储单据ID',
  MODIFY COLUMN `inventory_id` bigint NOT NULL COMMENT '库存记录ID',
  MODIFY COLUMN `source_line_id` bigint DEFAULT NULL COMMENT '退还关联的原出库单明细ID',
  MODIFY COLUMN `sequence_no` int NOT NULL COMMENT '顺序号，从1开始',
  MODIFY COLUMN `category` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '库存分类（RAW原材料，FINISHED成品，SEMI半成品）',
  MODIFY COLUMN `reference_id` bigint NOT NULL COMMENT '资料ID，RAW关联物料，FINISHED和SEMI关联产品',
  MODIFY COLUMN `code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物料或产品编码快照',
  MODIFY COLUMN `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '名称',
  MODIFY COLUMN `specification` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '规格型号',
  MODIFY COLUMN `batch_no` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '批次编号',
  MODIFY COLUMN `net_weight` decimal(20,6) NOT NULL COMMENT '净重（g）',
  MODIFY COLUMN `quantity` bigint NOT NULL DEFAULT '0' COMMENT '数量（件）；原材料按净重管理，数量为0',
  MODIFY COLUMN `gross_weight` decimal(20,6) DEFAULT NULL COMMENT '毛重（g）',
  MODIFY COLUMN `impurity` decimal(20,6) DEFAULT NULL COMMENT '杂质重量（g）',
  MODIFY COLUMN `purity` decimal(12,6) DEFAULT NULL COMMENT '纯度（%，范围0至100）',
  MODIFY COLUMN `organization` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '单位名称',
  MODIFY COLUMN `purpose` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用途',
  MODIFY COLUMN `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注';

ALTER TABLE `metal_warehouse_request`
  MODIFY COLUMN `request_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '请求唯一标识，用于幂等校验',
  MODIFY COLUMN `kind` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '单据类别（OUT出库，IN入库，RET还库，ADJ调整）',
  MODIFY COLUMN `request_hash` char(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '请求内容摘要，用于校验重复请求的一致性',
  MODIFY COLUMN `actor_id` bigint NOT NULL COMMENT '操作人用户ID',
  MODIFY COLUMN `result_id` bigint NOT NULL COMMENT '幂等请求已生成的业务记录ID',
  MODIFY COLUMN `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

ALTER TABLE `metal_warning_history`
  MODIFY COLUMN `history_id` bigint NOT NULL AUTO_INCREMENT COMMENT '历史记录ID',
  MODIFY COLUMN `warning_id` bigint NOT NULL COMMENT '损耗预警记录ID',
  MODIFY COLUMN `action` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '业务动作编码',
  MODIFY COLUMN `result` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '处理结果或说明',
  MODIFY COLUMN `actor_id` bigint NOT NULL COMMENT '操作人用户ID',
  MODIFY COLUMN `actor_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作人姓名快照',
  MODIFY COLUMN `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

ALTER TABLE `metal_warning_record`
  MODIFY COLUMN `warning_id` bigint NOT NULL AUTO_INCREMENT COMMENT '损耗预警记录ID',
  MODIFY COLUMN `report_id` bigint NOT NULL COMMENT '损耗报工ID',
  MODIFY COLUMN `factory_id` bigint NOT NULL COMMENT '所属工厂ID',
  MODIFY COLUMN `process_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工序名称',
  MODIFY COLUMN `product_category` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '产品类别（铱坩埚弧底、铱坩埚平底、铱坩埚梯形、铱环、铱杆实心、铱杆空心）',
  MODIFY COLUMN `specification` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '规格型号',
  MODIFY COLUMN `order_no` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '单据编号',
  MODIFY COLUMN `threshold_value` decimal(20,6) NOT NULL COMMENT '单件损耗预警阈值（g/件）',
  MODIFY COLUMN `theoretical_loss` decimal(20,6) NOT NULL COMMENT '单件理论损耗（g）',
  MODIFY COLUMN `average_loss` decimal(20,6) NOT NULL COMMENT '单件平均损耗（g/件）',
  MODIFY COLUMN `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '预警状态（PENDING待处理，HANDLED已处理）',
  MODIFY COLUMN `source_void` tinyint(1) NOT NULL DEFAULT '0' COMMENT '预警来源是否已冲销（0否，1是）',
  MODIFY COLUMN `result` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '处理结果或说明',
  MODIFY COLUMN `handler_id` bigint DEFAULT NULL COMMENT '预警处理人用户ID',
  MODIFY COLUMN `handler_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '预警处理人姓名快照',
  MODIFY COLUMN `handled_time` datetime DEFAULT NULL COMMENT '预警处理时间',
  MODIFY COLUMN `version` int NOT NULL DEFAULT '0' COMMENT '数据版本号，用于乐观锁校验',
  MODIFY COLUMN `del_flag` char(1) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除标记（0正常，2已删除）',
  MODIFY COLUMN `trigger_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '预警触发时间';

ALTER TABLE `metal_warning_rule`
  MODIFY COLUMN `rule_id` bigint NOT NULL AUTO_INCREMENT COMMENT '规则ID',
  MODIFY COLUMN `factory_id` bigint NOT NULL COMMENT '所属工厂ID',
  MODIFY COLUMN `product_id` bigint NOT NULL COMMENT '产品ID',
  MODIFY COLUMN `process_id` bigint NOT NULL COMMENT '工序ID',
  MODIFY COLUMN `tolerance` decimal(20,6) NOT NULL COMMENT '历史允许超差（g/件），当前预警按工单理论损耗判断',
  MODIFY COLUMN `status` char(1) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '历史预警规则状态（0启用，1停用）',
  MODIFY COLUMN `version` int NOT NULL DEFAULT '0' COMMENT '数据版本号，用于乐观锁校验',
  MODIFY COLUMN `del_flag` char(1) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除标记（0正常，2已删除）',
  MODIFY COLUMN `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

ALTER TABLE `metal_work_order`
  MODIFY COLUMN `work_order_id` bigint NOT NULL AUTO_INCREMENT COMMENT '生产工单ID',
  MODIFY COLUMN `order_no` varchar(32) NOT NULL COMMENT '单据编号',
  MODIFY COLUMN `request_id` varchar(36) NOT NULL COMMENT '请求唯一标识，用于幂等校验',
  MODIFY COLUMN `request_hash` char(64) NOT NULL COMMENT '请求内容摘要，用于校验重复请求的一致性',
  MODIFY COLUMN `request_actor` bigint NOT NULL COMMENT '发起请求的用户ID',
  MODIFY COLUMN `customer` varchar(100) NOT NULL COMMENT '客户名称',
  MODIFY COLUMN `factory_id` bigint NOT NULL COMMENT '所属工厂ID',
  MODIFY COLUMN `production_line_id` bigint NOT NULL COMMENT '产线ID',
  MODIFY COLUMN `owner_id` bigint NOT NULL COMMENT '工单负责人用户ID',
  MODIFY COLUMN `factory_name` varchar(100) NOT NULL COMMENT '工厂名称',
  MODIFY COLUMN `line_name` varchar(100) NOT NULL COMMENT '产线名称快照',
  MODIFY COLUMN `owner_name` varchar(100) NOT NULL COMMENT '工单负责人姓名快照',
  MODIFY COLUMN `order_date` date NOT NULL COMMENT '单据业务日期',
  MODIFY COLUMN `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '工单状态（PENDING待审核，APPROVED已通过，REJECTED已驳回，IN_PROGRESS生产中，COMPLETED已完成）',
  MODIFY COLUMN `reject_reason` varchar(500) DEFAULT NULL COMMENT '工单驳回原因',
  MODIFY COLUMN `create_by` varchar(64) NOT NULL COMMENT '创建人登录账号',
  MODIFY COLUMN `creator_name` varchar(100) NOT NULL COMMENT '创建人姓名快照',
  MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `update_by` varchar(64) NOT NULL COMMENT '最后修改人登录账号',
  MODIFY COLUMN `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后修改时间',
  MODIFY COLUMN `del_flag` char(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除标记（0正常，2已删除）',
  MODIFY COLUMN `version` int NOT NULL DEFAULT '0' COMMENT '数据版本号，用于乐观锁校验';

ALTER TABLE `metal_work_order_item`
  MODIFY COLUMN `item_id` bigint NOT NULL AUTO_INCREMENT COMMENT '工单产品明细ID',
  MODIFY COLUMN `work_order_id` bigint NOT NULL COMMENT '生产工单ID',
  MODIFY COLUMN `sequence_no` int NOT NULL COMMENT '顺序号，从1开始',
  MODIFY COLUMN `product_id` bigint NOT NULL COMMENT '产品ID',
  MODIFY COLUMN `quantity` int NOT NULL COMMENT '数量（件）；原材料按净重管理，数量为0',
  MODIFY COLUMN `product_snapshot` json NOT NULL COMMENT '工单产品资料快照（JSON）';

ALTER TABLE `metal_work_order_legacy_plan`
  MODIFY COLUMN `work_order_id` bigint NOT NULL DEFAULT '0' COMMENT '生产工单ID',
  MODIFY COLUMN `step_id` bigint NOT NULL DEFAULT '0' COMMENT '工单工序计划明细ID',
  MODIFY COLUMN `item_id` bigint NOT NULL COMMENT '工单产品明细ID',
  MODIFY COLUMN `process_id` bigint NOT NULL COMMENT '工序ID',
  MODIFY COLUMN `sequence_no` int NOT NULL COMMENT '顺序号，从1开始',
  MODIFY COLUMN `process_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '工序名称',
  MODIFY COLUMN `start_time` datetime NOT NULL COMMENT '迁移前归档的历史计划开始时间',
  MODIFY COLUMN `end_time` datetime NOT NULL COMMENT '迁移前归档的历史计划结束时间';

ALTER TABLE `metal_work_order_number`
  MODIFY COLUMN `request_id` varchar(36) NOT NULL COMMENT '请求唯一标识，用于幂等校验',
  MODIFY COLUMN `actor_id` bigint NOT NULL COMMENT '操作人用户ID',
  MODIFY COLUMN `order_no` varchar(32) DEFAULT NULL COMMENT '单据编号',
  MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

ALTER TABLE `metal_work_order_review`
  MODIFY COLUMN `review_id` bigint NOT NULL AUTO_INCREMENT COMMENT '工单审核记录ID',
  MODIFY COLUMN `work_order_id` bigint NOT NULL COMMENT '生产工单ID',
  MODIFY COLUMN `action` varchar(20) NOT NULL COMMENT '业务动作编码',
  MODIFY COLUMN `from_status` varchar(20) DEFAULT NULL COMMENT '变更前业务状态',
  MODIFY COLUMN `to_status` varchar(20) NOT NULL COMMENT '变更后业务状态',
  MODIFY COLUMN `reason` varchar(500) DEFAULT NULL COMMENT '业务操作原因或说明',
  MODIFY COLUMN `actor_id` bigint NOT NULL COMMENT '操作人用户ID',
  MODIFY COLUMN `actor_name` varchar(100) NOT NULL COMMENT '操作人姓名快照',
  MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

ALTER TABLE `metal_work_order_sequence`
  MODIFY COLUMN `business_date` date NOT NULL COMMENT '清理任务对应的业务日期',
  MODIFY COLUMN `sequence_value` int NOT NULL DEFAULT '0' COMMENT '当前已使用的流水序号';

ALTER TABLE `metal_work_order_step`
  MODIFY COLUMN `step_id` bigint NOT NULL AUTO_INCREMENT COMMENT '工单工序计划明细ID',
  MODIFY COLUMN `item_id` bigint NOT NULL COMMENT '工单产品明细ID',
  MODIFY COLUMN `process_id` bigint NOT NULL COMMENT '工序ID',
  MODIFY COLUMN `sequence_no` int NOT NULL COMMENT '顺序号，从1开始',
  MODIFY COLUMN `process_name` varchar(100) NOT NULL COMMENT '工序名称',
  MODIFY COLUMN `theoretical_loss` decimal(20,6) NOT NULL COMMENT '单件理论损耗（g）',
  MODIFY COLUMN `start_time` datetime DEFAULT NULL COMMENT '计划开始时间',
  MODIFY COLUMN `end_time` datetime DEFAULT NULL COMMENT '计划结束时间',
  MODIFY COLUMN `actual_start_time` datetime DEFAULT NULL COMMENT '历史实际开始时间，当前业务不再使用',
  MODIFY COLUMN `actual_end_time` datetime DEFAULT NULL COMMENT '历史实际结束时间，当前业务不再使用';

ALTER TABLE `metal_work_report`
  MODIFY COLUMN `report_id` bigint NOT NULL AUTO_INCREMENT COMMENT '损耗报工ID',
  MODIFY COLUMN `batch_id` bigint NOT NULL COMMENT '加工批次ID',
  MODIFY COLUMN `factory_id` bigint NOT NULL COMMENT '所属工厂ID',
  MODIFY COLUMN `item_id` bigint NOT NULL COMMENT '工单产品明细ID',
  MODIFY COLUMN `process_id` bigint NOT NULL COMMENT '工序ID',
  MODIFY COLUMN `completed_quantity` int NOT NULL COMMENT '完成数量（件）',
  MODIFY COLUMN `issued_weight` decimal(20,6) NOT NULL COMMENT '领用净重合计（g）',
  MODIFY COLUMN `returned_weight` decimal(20,6) NOT NULL COMMENT '回收净重合计（g，按报工结算口径统计回料与产出）',
  MODIFY COLUMN `loss_weight` decimal(20,6) NOT NULL COMMENT '实际损耗净重（g）',
  MODIFY COLUMN `average_loss` decimal(20,6) NOT NULL COMMENT '单件平均损耗（g/件）',
  MODIFY COLUMN `theoretical_loss` decimal(20,6) NOT NULL COMMENT '单件理论损耗（g）',
  MODIFY COLUMN `tolerance` decimal(20,6) NOT NULL COMMENT '历史允许超差（g/件），当前预警按工单理论损耗判断',
  MODIFY COLUMN `threshold_value` decimal(20,6) NOT NULL COMMENT '单件损耗预警阈值（g/件）',
  MODIFY COLUMN `rule_id` bigint DEFAULT NULL COMMENT '规则ID',
  MODIFY COLUMN `rule_version` int DEFAULT NULL COMMENT '计算时采用的规则版本号',
  MODIFY COLUMN `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '损耗状态（NORMAL正常，ABNORMAL异常）',
  MODIFY COLUMN `voided` tinyint(1) NOT NULL DEFAULT '0' COMMENT '报工是否已作废（0否，1是）',
  MODIFY COLUMN `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

ALTER TABLE `sys_logininfor`
  MODIFY COLUMN `trace_id` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '请求链路追踪ID',
  MODIFY COLUMN `event_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '事件唯一标识，用于追踪及去重',
  MODIFY COLUMN `reason_category` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '登录结果原因分类';

ALTER TABLE `sys_oper_log`
  MODIFY COLUMN `oper_param` text COLLATE utf8mb4_unicode_ci COMMENT '操作请求参数（敏感字段过滤后）',
  MODIFY COLUMN `json_result` text COLLATE utf8mb4_unicode_ci COMMENT '操作响应内容',
  MODIFY COLUMN `event_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '事件唯一标识，用于追踪及去重',
  MODIFY COLUMN `trace_id` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '请求链路追踪ID',
  MODIFY COLUMN `actor_user_id` bigint DEFAULT NULL COMMENT '操作人用户ID',
  MODIFY COLUMN `actor_factory_id` bigint DEFAULT NULL COMMENT '操作人所属工厂ID',
  MODIFY COLUMN `business_factory_id` bigint DEFAULT NULL COMMENT '操作涉及的业务工厂ID',
  MODIFY COLUMN `factory_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工厂名称',
  MODIFY COLUMN `actor_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作人姓名快照',
  MODIFY COLUMN `module_code` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '业务模块编码',
  MODIFY COLUMN `action_code` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作动作编码',
  MODIFY COLUMN `event_key` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '审计事件分类标识',
  MODIFY COLUMN `result_code` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作结果编码',
  MODIFY COLUMN `business_code` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '业务标识编码',
  MODIFY COLUMN `object_type` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作对象类型',
  MODIFY COLUMN `objects_json` json DEFAULT NULL COMMENT '操作对象列表（JSON）',
  MODIFY COLUMN `changes_json` json DEFAULT NULL COMMENT '字段变更明细（JSON）',
  MODIFY COLUMN `error_category` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '异常类别',
  MODIFY COLUMN `replayed` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否为重放事件（0否，1是）',
  MODIFY COLUMN `truncated` tinyint(1) NOT NULL DEFAULT '0' COMMENT '日志内容是否已截断（0否，1是）',
  MODIFY COLUMN `schema_version` int NOT NULL DEFAULT '1' COMMENT '审计事件结构版本号';

ALTER TABLE `sys_role`
  MODIFY COLUMN `role_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色名称（在所属工厂内唯一）',
  MODIFY COLUMN `factory_id` bigint DEFAULT NULL COMMENT '所属工厂ID',
  MODIFY COLUMN `version` int NOT NULL DEFAULT '0' COMMENT '数据版本号，用于乐观锁校验',
  MODIFY COLUMN `active_role_name` varchar(100) COLLATE utf8mb4_unicode_ci GENERATED ALWAYS AS ((case when (`del_flag` = _utf8mb4'0') then `role_name` else NULL end)) STORED COMMENT '未删除角色名称的生成列，用于所属工厂内角色名称唯一约束';

ALTER TABLE `sys_runtime_log`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录主键ID',
  MODIFY COLUMN `event_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '事件唯一标识，用于追踪及去重',
  MODIFY COLUMN `event_time` datetime NOT NULL COMMENT '事件发生时间',
  MODIFY COLUMN `level` varchar(8) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '日志级别',
  MODIFY COLUMN `service` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '服务名称',
  MODIFY COLUMN `component` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '产生日志的组件名称',
  MODIFY COLUMN `event_type` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '运行事件类型',
  MODIFY COLUMN `summary` text COLLATE utf8mb4_unicode_ci COMMENT '摘要说明',
  MODIFY COLUMN `trace_id` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '请求链路追踪ID',
  MODIFY COLUMN `exception_class` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '异常类全名',
  MODIFY COLUMN `detail_json` json DEFAULT NULL COMMENT '运行事件详细内容（JSON）',
  MODIFY COLUMN `cost_ms` bigint DEFAULT NULL COMMENT '执行耗时（毫秒）',
  MODIFY COLUMN `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

ALTER TABLE `sys_user`
  MODIFY COLUMN `nick_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工作人员姓名',
  MODIFY COLUMN `factory_id` bigint DEFAULT NULL COMMENT '所属工厂ID',
  MODIFY COLUMN `version` int NOT NULL DEFAULT '0' COMMENT '数据版本号，用于乐观锁校验',
  MODIFY COLUMN `unique_phone` varchar(11) COLLATE utf8mb4_unicode_ci GENERATED ALWAYS AS (nullif(`phonenumber`,_utf8mb4'')) STORED COMMENT '手机号唯一索引辅助列，将空字符串映射为NULL',
  MODIFY COLUMN `auth_version` bigint NOT NULL DEFAULT '0' COMMENT '账号认证版本号，用于识别权限及会话变更';
