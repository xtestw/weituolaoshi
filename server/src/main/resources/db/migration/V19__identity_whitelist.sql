CREATE TABLE identity_test_whitelist (
 id VARCHAR(64) PRIMARY KEY,
 document_digest VARCHAR(64) UNIQUE NOT NULL,
 masked_number VARCHAR(32) NOT NULL,
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);
INSERT INTO admin_permission VALUES ('identity:whitelist','管理实名测试白名单');
INSERT INTO admin_role_permission VALUES ('super_admin','identity:whitelist');
