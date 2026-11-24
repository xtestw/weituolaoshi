CREATE TABLE order_checkin (
  order_id VARCHAR(64) PRIMARY KEY REFERENCES booking_order(id),
  code_hash VARCHAR(64) NOT NULL,
  customer_code CHAR(4) NOT NULL,
  code_expires_at TIMESTAMP NOT NULL,
  used_at TIMESTAMP,
  latitude DOUBLE PRECISION,
  longitude DOUBLE PRECISION,
  photo_path VARCHAR(500),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE order_completion_confirmation (
  order_id VARCHAR(64) NOT NULL REFERENCES booking_order(id),
  actor_id VARCHAR(64) NOT NULL REFERENCES app_user(id),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY(order_id,actor_id)
);
CREATE TABLE order_exception (
  id VARCHAR(64) PRIMARY KEY,
  order_id VARCHAR(64) NOT NULL REFERENCES booking_order(id),
  actor_id VARCHAR(64) NOT NULL REFERENCES app_user(id),
  reason VARCHAR(200) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'pending',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
