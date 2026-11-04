ALTER TABLE metal_process_batch ADD COLUMN batch_no VARCHAR(256) NULL;
UPDATE metal_process_batch b JOIN metal_work_order w ON w.work_order_id=b.work_order_id JOIN metal_work_order_item i ON i.item_id=b.item_id SET b.batch_no=CONCAT(w.order_no,'-',DATE_FORMAT(b.create_time,'%Y%m%d%H%i%s%f'),'-',JSON_UNQUOTE(JSON_EXTRACT(i.product_snapshot,'$.code')),'-H',b.batch_id);
ALTER TABLE metal_process_batch ADD UNIQUE KEY uk_batch_no(batch_no);
UPDATE sys_menu SET visible='1',status='1' WHERE menu_id IN (4040,4110);
