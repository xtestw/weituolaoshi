ALTER TABLE service_post ADD COLUMN manually_offline BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE service_post ADD COLUMN moderation_reason VARCHAR(500);
ALTER TABLE service_post ADD COLUMN moderated_at TIMESTAMP;
INSERT INTO admin_permission VALUES ('services:read','查看Coser服务帖子');
INSERT INTO admin_permission VALUES ('services:moderate','人工下线或恢复Coser服务帖子');
INSERT INTO admin_role_permission(role_id,permission_code) VALUES ('super_admin','services:read');
INSERT INTO admin_role_permission(role_id,permission_code) VALUES ('super_admin','services:moderate');
