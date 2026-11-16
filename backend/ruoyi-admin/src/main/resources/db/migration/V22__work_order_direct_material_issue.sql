-- Retain historical states and audit rows; only new orders use the new workflow.
ALTER TABLE metal_work_order DROP CHECK chk_wo_status,
 ALTER COLUMN status SET DEFAULT 'WAITING_MATERIAL',
 ADD CONSTRAINT chk_wo_status CHECK(status IN ('PENDING','REJECTED','APPROVED','WAITING_MATERIAL','IN_PROGRESS','COMPLETED'));
UPDATE sys_menu SET visible='1',status='1' WHERE perms IN ('metal:work-order:approve','metal:work-order:reject','metal:work-order:resubmit');
