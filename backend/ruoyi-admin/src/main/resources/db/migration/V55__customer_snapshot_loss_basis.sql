ALTER TABLE metal_warehouse_order_line ADD COLUMN customer_snapshot JSON NULL;
ALTER TABLE metal_work_order ADD COLUMN loss_rate_basis VARCHAR(20) NOT NULL DEFAULT 'NET_INPUT',
  ADD CONSTRAINT ck_work_order_loss_basis CHECK(loss_rate_basis IN ('NET_INPUT','OUTBOUND_TOTAL'));
ALTER TABLE metal_process_batch ADD COLUMN loss_rate_basis VARCHAR(20) NOT NULL DEFAULT 'NET_INPUT',
  ADD CONSTRAINT ck_batch_loss_basis CHECK(loss_rate_basis IN ('NET_INPUT','OUTBOUND_TOTAL'));
ALTER TABLE metal_work_report ADD COLUMN loss_rate_basis VARCHAR(20) NOT NULL DEFAULT 'NET_INPUT',
  ADD COLUMN loss_rate_denominator_weight DECIMAL(20,6) NULL,
  ADD CONSTRAINT ck_report_loss_basis CHECK(loss_rate_basis IN ('NET_INPUT','OUTBOUND_TOTAL'));
ALTER TABLE metal_warning_record ADD COLUMN loss_rate_basis VARCHAR(20) NOT NULL DEFAULT 'NET_INPUT',
  ADD COLUMN loss_rate_denominator_weight DECIMAL(20,6) NULL;
