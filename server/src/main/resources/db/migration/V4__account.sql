ALTER TABLE app_user ADD COLUMN avatar_url VARCHAR(512);
CREATE TABLE user_session (
  token_hash VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL REFERENCES app_user(id),
  expires_at TIMESTAMP NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_user_session_user ON user_session(user_id);
