-- Preserve old login history without guessing identity from masked telephone numbers.
ALTER TABLE sys_logininfor ADD COLUMN account_user_id BIGINT NULL COMMENT '登录目标账号固定人员ID';
CREATE INDEX idx_login_account_time ON sys_logininfor(account_user_id,login_time,info_id);
CREATE TABLE audit_login_identity_boundary (
 id TINYINT PRIMARY KEY,
 historical_max_id BIGINT NOT NULL,
 migration_time DATETIME(6) NOT NULL
);
INSERT INTO audit_login_identity_boundary(id,historical_max_id,migration_time)
 SELECT 1,COALESCE(MAX(info_id),0),CURRENT_TIMESTAMP(6) FROM sys_logininfor;
