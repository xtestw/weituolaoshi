CREATE TABLE customer_post_like (
  post_id VARCHAR(64) NOT NULL REFERENCES customer_post(id),
  user_id VARCHAR(64) NOT NULL REFERENCES app_user(id),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (post_id, user_id)
);
CREATE INDEX idx_customer_post_like_user ON customer_post_like(user_id, created_at);

CREATE TABLE customer_post_comment (
  id VARCHAR(64) PRIMARY KEY,
  post_id VARCHAR(64) NOT NULL REFERENCES customer_post(id),
  author_id VARCHAR(64) NOT NULL REFERENCES app_user(id),
  body CLOB NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMP
);
CREATE INDEX idx_customer_post_comment_post ON customer_post_comment(post_id, created_at);
CREATE INDEX idx_customer_post_comment_author ON customer_post_comment(author_id, created_at);
