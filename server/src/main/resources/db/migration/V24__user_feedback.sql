CREATE TABLE user_feedback (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL REFERENCES app_user(id),
    body VARCHAR(2000) NOT NULL,
    contact VARCHAR(100) NOT NULL DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_feedback_created ON user_feedback(created_at);
CREATE TABLE feedback_image (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL REFERENCES app_user(id),
    feedback_id VARCHAR(64) REFERENCES user_feedback(id),
    mime_type VARCHAR(40) NOT NULL,
    image_data BYTEA NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_feedback_image_feedback ON feedback_image(feedback_id);
INSERT INTO admin_permission VALUES ('feedback:read','查看用户反馈及截图');
INSERT INTO admin_role_permission VALUES ('super_admin','feedback:read');
