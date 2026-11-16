-- Preserve existing planned timestamps; never infer actual execution from plans.
CREATE TABLE metal_work_order_legacy_plan AS
SELECT w.work_order_id, s.step_id, s.item_id, s.process_id, s.sequence_no, s.process_name, s.start_time, s.end_time
FROM metal_work_order_step s JOIN metal_work_order_item i ON i.item_id=s.item_id JOIN metal_work_order w ON w.work_order_id=i.work_order_id;
ALTER TABLE metal_work_order_legacy_plan ADD PRIMARY KEY(step_id), ADD INDEX idx_legacy_order(work_order_id);
ALTER TABLE metal_work_order_step MODIFY start_time DATETIME NULL, MODIFY end_time DATETIME NULL,
 ADD CONSTRAINT chk_wo_actual_time CHECK ((actual_start_time IS NULL AND actual_end_time IS NULL) OR (actual_start_time IS NOT NULL AND actual_end_time IS NOT NULL AND actual_start_time < actual_end_time));
UPDATE metal_work_order SET version=version+1;
