ALTER TABLE sys_user ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE audit_outbox ADD COLUMN delivered_time DATETIME NULL,ADD KEY ix_delivered_time(status,delivered_time),ADD KEY ix_delivery_ready(status,next_retry,create_time,event_id);
ALTER TABLE audit_cleanup_history ADD COLUMN stage VARCHAR(20) NOT NULL DEFAULT 'OPERATION',
 ADD COLUMN cutoffs_json JSON NULL,ADD COLUMN operation_count BIGINT NOT NULL DEFAULT 0,
 ADD COLUMN login_count BIGINT NOT NULL DEFAULT 0,ADD COLUMN runtime_count BIGINT NOT NULL DEFAULT 0,
 ADD COLUMN outbox_count BIGINT NOT NULL DEFAULT 0,ADD COLUMN update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;
