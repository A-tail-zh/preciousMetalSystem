ALTER TABLE metal_warehouse_order_line ADD COLUMN receiving_organization VARCHAR(100) NULL COMMENT '收货单位，与所属客户分开保存' AFTER organization;
