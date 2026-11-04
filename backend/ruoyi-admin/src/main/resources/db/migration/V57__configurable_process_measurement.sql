-- Keep historical RAW zero plans; new configurable PIECE preparation has an effective piece plan.
-- Application validation selects the rule from the immutable measurement-policy snapshot.
ALTER TABLE metal_process_batch
 DROP CHECK chk_processing_batch_quantity,
 ADD CONSTRAINT chk_processing_batch_quantity CHECK(
  (processing_policy_version>=2 AND output_stage='RAW' AND
   (planned_quantity=0 OR (measurement_mode='PIECE' AND planned_quantity BETWEEN 1 AND 1000000)))
  OR (COALESCE(output_stage,'')<>'RAW' AND planned_quantity BETWEEN 1 AND 1000000)
 );
