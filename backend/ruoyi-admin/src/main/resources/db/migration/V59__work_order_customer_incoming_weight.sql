ALTER TABLE metal_work_order
 ADD customer_incoming_weight DECIMAL(16,2) NULL COMMENT '本工单客户来料登记净重，仅作记录',
 ADD CONSTRAINT chk_work_order_customer_incoming_weight CHECK (customer_incoming_weight IS NULL OR customer_incoming_weight >= 0);
