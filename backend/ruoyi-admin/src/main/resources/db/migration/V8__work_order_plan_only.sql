-- Preserve original plans; carry forward manually entered times only where no plan exists.
UPDATE metal_work_order_step SET start_time=actual_start_time,end_time=actual_end_time WHERE start_time IS NULL AND end_time IS NULL AND actual_start_time IS NOT NULL AND actual_end_time IS NOT NULL;
UPDATE metal_work_order SET version=version+1;
