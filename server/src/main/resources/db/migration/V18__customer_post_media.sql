CREATE TABLE customer_post_media (
  id VARCHAR(64) PRIMARY KEY,
  post_id VARCHAR(64) NOT NULL REFERENCES customer_post(id),
  media_url VARCHAR(2048) NOT NULL,
  object_key VARCHAR(512) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMP,
  UNIQUE(object_key),
  CHECK (sort_order >= 0)
);
CREATE INDEX idx_customer_post_media_post ON customer_post_media(post_id, deleted_at, sort_order);
