ALTER TABLE metal_work_order ADD handover_policy_version INT NOT NULL DEFAULT 1;
ALTER TABLE metal_process_batch ADD handover_policy_version INT NOT NULL DEFAULT 1;

ALTER TABLE metal_warehouse_flow
 ADD handover_policy_version INT NOT NULL DEFAULT 1,
 ADD segment_no INT NULL,
 DROP INDEX uq_active_handover,
 DROP COLUMN active_handover,
 ADD active_handover BIGINT GENERATED ALWAYS AS
  (CASE WHEN kind='HANDOVER' AND handover_policy_version=1 AND status NOT IN ('VOID','CANCELLED') THEN batch_id ELSE NULL END) STORED,
 ADD UNIQUE KEY uq_active_handover(active_handover),
 ADD UNIQUE KEY uq_handover_segment(batch_id,segment_no);

ALTER TABLE metal_inventory ADD origin_handover_flow_id BIGINT NULL,
 ADD CONSTRAINT fk_inventory_handover FOREIGN KEY(origin_handover_flow_id) REFERENCES metal_warehouse_flow(flow_id);

ALTER TABLE metal_work_report
 ADD handover_flow_id BIGINT NULL,
 ADD KEY idx_report_batch(batch_id),
 DROP INDEX batch_id,
 ADD legacy_batch_id BIGINT GENERATED ALWAYS AS (CASE WHEN handover_flow_id IS NULL THEN batch_id ELSE NULL END) STORED,
 ADD UNIQUE KEY uq_report_legacy_batch(legacy_batch_id),
 ADD UNIQUE KEY uq_report_handover(handover_flow_id),
 ADD CONSTRAINT fk_report_handover FOREIGN KEY(handover_flow_id) REFERENCES metal_warehouse_flow(flow_id);

ALTER TABLE metal_work_report DROP CHECK chk_report_loss_unit,
 ADD CONSTRAINT chk_report_loss_unit CHECK (
  loss_unit IN ('G_PER_PIECE','PERCENT') AND
  (loss_unit<>'PERCENT' OR (theoretical_loss BETWEEN 0 AND 100 AND
   ((net_input_weight>0 AND loss_rate_percent BETWEEN 0 AND 100) OR
    (handover_flow_id IS NOT NULL AND completed_quantity=0 AND issued_weight>0
     AND returned_weight=issued_weight AND loss_weight=0 AND net_input_weight=0
     AND COALESCE(processor_output_weight,0)=0 AND (loss_rate_percent IS NULL OR loss_rate_percent=0)))))
 );

CREATE TABLE metal_batch_wip_balance (
 source_line_id BIGINT PRIMARY KEY,
 batch_id BIGINT NOT NULL,
 actor_id BIGINT NOT NULL,
 group_key VARCHAR(160) NOT NULL,
 remaining_weight DECIMAL(20,6) NOT NULL,
 version INT NOT NULL DEFAULT 0,
 FOREIGN KEY(source_line_id) REFERENCES metal_warehouse_order_line(line_id),
 FOREIGN KEY(batch_id) REFERENCES metal_process_batch(batch_id),
 KEY idx_wip_batch_actor(batch_id,actor_id),
 CHECK(remaining_weight>=0)
);

CREATE TABLE metal_handover_allocation (
 flow_id BIGINT NOT NULL,
 allocation_version INT NOT NULL DEFAULT 1,
 source_line_id BIGINT NOT NULL,
 before_weight DECIMAL(20,6) NOT NULL,
 allocated_weight DECIMAL(20,6) NOT NULL,
 after_weight DECIMAL(20,6) NOT NULL,
 returned_weight DECIMAL(20,6) NOT NULL,
 voided BOOLEAN NOT NULL DEFAULT FALSE,
 PRIMARY KEY(flow_id,allocation_version,source_line_id),
 FOREIGN KEY(flow_id) REFERENCES metal_warehouse_flow(flow_id),
 FOREIGN KEY(source_line_id) REFERENCES metal_batch_wip_balance(source_line_id),
 KEY idx_allocation_source(source_line_id,flow_id),
 CHECK(before_weight>=0 AND allocated_weight>=0 AND after_weight>=0 AND returned_weight>=0),
 CHECK(before_weight=allocated_weight+after_weight),
 CONSTRAINT chk_handover_allocation_version CHECK(allocation_version>=1)
);

ALTER TABLE metal_warehouse_flow_line
 ADD reference_id_key VARCHAR(64) GENERATED ALWAYS AS
  (NULLIF(JSON_UNQUOTE(JSON_EXTRACT(line_json,'$.referenceId')),'null')) STORED,
 ADD category_key VARCHAR(16) GENERATED ALWAYS AS
  (NULLIF(JSON_UNQUOTE(JSON_EXTRACT(line_json,'$.category')),'null')) STORED,
 ADD KEY idx_flow_line_reference(reference_id_key,category_key,group_id);
