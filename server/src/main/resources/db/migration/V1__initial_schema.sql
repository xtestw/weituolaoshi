CREATE TABLE app_user (
  id VARCHAR(64) PRIMARY KEY,
  wechat_openid VARCHAR(128) UNIQUE,
  nickname VARCHAR(100),
  role VARCHAR(20) NOT NULL DEFAULT 'customer',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE identity_verification (
  id VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL UNIQUE REFERENCES app_user(id),
  provider_reference VARCHAR(128),
  document_digest VARCHAR(128),
  masked_document_number VARCHAR(32),
  legal_gender CHAR(1),
  adult BOOLEAN NOT NULL DEFAULT FALSE,
  status VARCHAR(24) NOT NULL,
  verified_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT ck_identity_gender CHECK (legal_gender IN ('F','M') OR legal_gender IS NULL)
);

CREATE TABLE coser_profile (
  user_id VARCHAR(64) PRIMARY KEY REFERENCES app_user(id),
  display_name VARCHAR(100) NOT NULL,
  city VARCHAR(80) NOT NULL,
  deposit_qualified BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE service_post (
  id VARCHAR(64) PRIMARY KEY,
  coser_id VARCHAR(64) NOT NULL REFERENCES coser_profile(user_id),
  title VARCHAR(200) NOT NULL,
  description CLOB,
  city VARCHAR(80) NOT NULL,
  price_per_hour BIGINT NOT NULL CHECK (price_per_hour > 0),
  min_hours INT NOT NULL CHECK (min_hours BETWEEN 2 AND 8),
  status VARCHAR(24) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_service_post_status_city ON service_post(status, city);

CREATE TABLE service_image (
  id VARCHAR(64) PRIMARY KEY,
  service_id VARCHAR(64) NOT NULL REFERENCES service_post(id),
  object_key VARCHAR(512) NOT NULL,
  sort_order INT NOT NULL,
  review_status VARCHAR(24) NOT NULL,
  UNIQUE(service_id, sort_order)
);

CREATE TABLE service_boundary (
  id VARCHAR(64) PRIMARY KEY,
  service_id VARCHAR(64) NOT NULL REFERENCES service_post(id),
  text VARCHAR(300) NOT NULL,
  sort_order INT NOT NULL,
  UNIQUE(service_id, sort_order)
);

CREATE TABLE availability_slot (
  id VARCHAR(64) PRIMARY KEY,
  coser_id VARCHAR(64) NOT NULL REFERENCES coser_profile(user_id),
  start_at TIMESTAMP NOT NULL,
  end_at TIMESTAMP NOT NULL,
  status VARCHAR(24) NOT NULL,
  CHECK (end_at > start_at)
);
CREATE INDEX idx_slot_coser_time ON availability_slot(coser_id, start_at, end_at);

CREATE TABLE venue (
  id VARCHAR(64) PRIMARY KEY,
  provider_poi_id VARCHAR(128) UNIQUE,
  name VARCHAR(200) NOT NULL,
  city VARCHAR(80) NOT NULL,
  district VARCHAR(80),
  category VARCHAR(80) NOT NULL,
  address VARCHAR(500) NOT NULL,
  review_status VARCHAR(24) NOT NULL
);

CREATE TABLE price_quote (
  id VARCHAR(64) PRIMARY KEY,
  customer_id VARCHAR(64) NOT NULL REFERENCES app_user(id),
  service_id VARCHAR(64) NOT NULL REFERENCES service_post(id),
  start_at TIMESTAMP NOT NULL,
  end_at TIMESTAMP NOT NULL,
  service_amount BIGINT NOT NULL CHECK (service_amount >= 0),
  customer_platform_fee BIGINT NOT NULL DEFAULT 0,
  protection_fee BIGINT NOT NULL DEFAULT 0,
  payable_amount BIGINT NOT NULL CHECK (payable_amount >= 0),
  expires_at TIMESTAMP NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE booking_order (
  id VARCHAR(64) PRIMARY KEY,
  customer_id VARCHAR(64) NOT NULL REFERENCES app_user(id),
  coser_id VARCHAR(64) NOT NULL REFERENCES coser_profile(user_id),
  service_id VARCHAR(64) NOT NULL REFERENCES service_post(id),
  quote_id VARCHAR(64) NOT NULL UNIQUE REFERENCES price_quote(id),
  venue_id VARCHAR(64) NOT NULL REFERENCES venue(id),
  start_at TIMESTAMP NOT NULL,
  end_at TIMESTAMP NOT NULL,
  payable_amount BIGINT NOT NULL,
  currency CHAR(3) NOT NULL DEFAULT 'CNY',
  status VARCHAR(32) NOT NULL,
  version INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (end_at > start_at)
);
CREATE INDEX idx_order_coser_time ON booking_order(coser_id, start_at, end_at);
CREATE INDEX idx_order_customer ON booking_order(customer_id, created_at);

CREATE TABLE order_event (
  id VARCHAR(64) PRIMARY KEY,
  order_id VARCHAR(64) NOT NULL REFERENCES booking_order(id),
  actor_id VARCHAR(64) REFERENCES app_user(id),
  action VARCHAR(64) NOT NULL,
  from_status VARCHAR(32),
  to_status VARCHAR(32) NOT NULL,
  details CLOB,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_order_event_order ON order_event(order_id, created_at);

CREATE TABLE idempotency_record (
  actor_id VARCHAR(64) NOT NULL REFERENCES app_user(id),
  operation VARCHAR(80) NOT NULL,
  request_key VARCHAR(128) NOT NULL,
  result_id VARCHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY(actor_id, operation, request_key)
);

CREATE TABLE conversation (
  id VARCHAR(64) PRIMARY KEY,
  customer_id VARCHAR(64) NOT NULL REFERENCES app_user(id),
  coser_id VARCHAR(64) NOT NULL REFERENCES coser_profile(user_id),
  service_id VARCHAR(64) REFERENCES service_post(id),
  order_id VARCHAR(64) REFERENCES booking_order(id),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE chat_message (
  id VARCHAR(64) PRIMARY KEY,
  conversation_id VARCHAR(64) NOT NULL REFERENCES conversation(id),
  sender_id VARCHAR(64) NOT NULL REFERENCES app_user(id),
  body VARCHAR(2000) NOT NULL,
  moderation_status VARCHAR(24) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_chat_message_conversation ON chat_message(conversation_id, created_at);

CREATE TABLE time_confirmation (
  id VARCHAR(64) PRIMARY KEY,
  conversation_id VARCHAR(64) NOT NULL REFERENCES conversation(id),
  coser_id VARCHAR(64) NOT NULL REFERENCES coser_profile(user_id),
  start_at TIMESTAMP NOT NULL,
  end_at TIMESTAMP NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE payment_transaction (
  id VARCHAR(64) PRIMARY KEY,
  order_id VARCHAR(64) NOT NULL REFERENCES booking_order(id),
  provider_transaction_id VARCHAR(128) UNIQUE,
  type VARCHAR(24) NOT NULL,
  amount BIGINT NOT NULL CHECK (amount > 0),
  status VARCHAR(24) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE settlement (
  id VARCHAR(64) PRIMARY KEY,
  order_id VARCHAR(64) NOT NULL UNIQUE REFERENCES booking_order(id),
  gross_amount BIGINT NOT NULL,
  platform_fee BIGINT NOT NULL,
  payable_amount BIGINT NOT NULL,
  status VARCHAR(24) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
