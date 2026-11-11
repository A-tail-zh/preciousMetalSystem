-- Preserve empty execution identifiers and their cancelled-document history.
SET @execution_status_constraint=(SELECT tc.CONSTRAINT_NAME FROM information_schema.TABLE_CONSTRAINTS tc JOIN information_schema.CHECK_CONSTRAINTS cc ON cc.CONSTRAINT_SCHEMA=tc.CONSTRAINT_SCHEMA AND cc.CONSTRAINT_NAME=tc.CONSTRAINT_NAME WHERE tc.TABLE_SCHEMA=DATABASE() AND tc.TABLE_NAME='metal_production_execution' AND tc.CONSTRAINT_TYPE='CHECK' AND cc.CHECK_CLAUSE LIKE '%status%' LIMIT 1);
SET @execution_status_sql=IF(@execution_status_constraint IS NULL,'SELECT 1',CONCAT('ALTER TABLE metal_production_execution DROP CHECK `',REPLACE(@execution_status_constraint,'`','``'),'`'));
PREPARE execution_status_statement FROM @execution_status_sql;
EXECUTE execution_status_statement;
DEALLOCATE PREPARE execution_status_statement;
ALTER TABLE metal_production_execution ADD CONSTRAINT chk_production_execution_status CHECK(status IN ('OPEN','CLOSED','CANCELLED'));
