ALTER TABLE metal_work_order
 ADD customer_id BIGINT NULL,
 ADD customer_version INT NULL,
 ADD customer_snapshot JSON NULL,
 ADD processing_type VARCHAR(16) NULL,
 ADD CONSTRAINT fk_work_order_customer FOREIGN KEY(customer_id) REFERENCES metal_customer(customer_id),
 ADD CONSTRAINT chk_work_order_processing CHECK(processing_type IS NULL OR processing_type IN ('MATERIAL','DIRECT','SHORTAGE'));

-- Existing amounts keep their historical unit; only explicitly entered new rates use PERCENT.
ALTER TABLE metal_product_process ADD loss_unit VARCHAR(16) NOT NULL DEFAULT 'G_PER_PIECE',
 ADD CONSTRAINT chk_product_loss_unit CHECK(loss_unit IN ('G_PER_PIECE','PERCENT') AND (loss_unit<>'PERCENT' OR theoretical_loss BETWEEN 0 AND 100));
ALTER TABLE metal_work_order_step ADD loss_unit VARCHAR(16) NOT NULL DEFAULT 'G_PER_PIECE',
 ADD CONSTRAINT chk_step_loss_unit CHECK(loss_unit IN ('G_PER_PIECE','PERCENT') AND (loss_unit<>'PERCENT' OR theoretical_loss BETWEEN 0 AND 100));
ALTER TABLE metal_work_report ADD loss_unit VARCHAR(16) NOT NULL DEFAULT 'G_PER_PIECE',
 ADD net_input_weight DECIMAL(20,6) NULL,
 ADD loss_rate_percent DECIMAL(7,2) NULL,
 ADD CONSTRAINT chk_report_loss_unit CHECK(loss_unit IN ('G_PER_PIECE','PERCENT') AND (loss_unit<>'PERCENT' OR (theoretical_loss BETWEEN 0 AND 100 AND net_input_weight>0 AND loss_rate_percent BETWEEN 0 AND 100)));
ALTER TABLE metal_warning_record ADD loss_unit VARCHAR(16) NOT NULL DEFAULT 'G_PER_PIECE',
 ADD loss_rate_percent DECIMAL(7,2) NULL,
 ADD CONSTRAINT chk_warning_loss_unit CHECK(loss_unit IN ('G_PER_PIECE','PERCENT'));
