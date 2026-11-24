CREATE TABLE customer_post (
  id VARCHAR(64) PRIMARY KEY,
  author_id VARCHAR(64) NOT NULL REFERENCES app_user(id),
  title VARCHAR(200) NOT NULL,
  body CLOB NOT NULL,
  city VARCHAR(80),
  status VARCHAR(16) NOT NULL DEFAULT 'published',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMP,
  CONSTRAINT ck_customer_post_status CHECK (status IN ('draft','published'))
);
CREATE INDEX idx_customer_post_feed ON customer_post(status, created_at);
CREATE INDEX idx_customer_post_author ON customer_post(author_id, created_at);
