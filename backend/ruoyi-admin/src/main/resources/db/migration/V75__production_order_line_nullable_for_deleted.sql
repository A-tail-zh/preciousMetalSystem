-- Soft-deleted orders keep line name snapshots; allow releasing FKs so production lines can be removed.
ALTER TABLE metal_production_order
  MODIFY production_line_id bigint NULL;

ALTER TABLE metal_work_order
  MODIFY production_line_id bigint NULL;
