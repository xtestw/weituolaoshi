-- Additive H2 migration for the existing application. The MySQL v4 source in
-- reference/transfer is a separate greenfield target, not an upgrade script.

ALTER TABLE app_user ADD COLUMN account_status VARCHAR(16) NOT NULL DEFAULT 'active';
ALTER TABLE app_user ADD COLUMN identity_provider VARCHAR(32) NOT NULL DEFAULT 'wechat';
ALTER TABLE app_user ADD COLUMN identity_subject VARCHAR(191);
ALTER TABLE app_user ADD COLUMN age INT;
ALTER TABLE app_user ADD COLUMN bio VARCHAR(500);
ALTER TABLE app_user ADD COLUMN avatar_key VARCHAR(512);
ALTER TABLE app_user ADD COLUMN gender_code VARCHAR(32);
ALTER TABLE app_user ADD COLUMN same_gender_commission_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE app_user ADD COLUMN last_login_at TIMESTAMP;
ALTER TABLE app_user ADD COLUMN closed_at TIMESTAMP;
ALTER TABLE app_user ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE app_user ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
UPDATE app_user SET identity_subject=wechat_openid WHERE wechat_openid IS NOT NULL;
UPDATE app_user SET identity_provider='demo' WHERE id LIKE 'demo-%' AND wechat_openid IS NULL;
CREATE UNIQUE INDEX uq_app_user_identity ON app_user(identity_provider,identity_subject);
ALTER TABLE app_user ADD CONSTRAINT ck_app_user_status CHECK (account_status IN ('active','frozen','closed'));
ALTER TABLE app_user ADD CONSTRAINT ck_app_user_closed CHECK ((account_status='closed' AND closed_at IS NOT NULL) OR (account_status<>'closed' AND closed_at IS NULL));
ALTER TABLE app_user ADD CONSTRAINT ck_app_user_age CHECK (age IS NULL OR age BETWEEN 0 AND 120);

ALTER TABLE coser_profile ADD COLUMN introduction CLOB;
ALTER TABLE coser_profile ADD COLUMN timezone_name VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai';
ALTER TABLE coser_profile ADD COLUMN verification_status VARCHAR(16) NOT NULL DEFAULT 'unverified';
ALTER TABLE coser_profile ADD COLUMN service_status VARCHAR(16) NOT NULL DEFAULT 'inactive';
ALTER TABLE coser_profile ADD COLUMN hourly_price_cents BIGINT;
ALTER TABLE coser_profile ADD COLUMN currency CHAR(3) NOT NULL DEFAULT 'CNY';
ALTER TABLE coser_profile ADD COLUMN booking_step_minutes INT NOT NULL DEFAULT 60;
ALTER TABLE coser_profile ADD COLUMN buffer_before_minutes INT NOT NULL DEFAULT 0;
ALTER TABLE coser_profile ADD COLUMN buffer_after_minutes INT NOT NULL DEFAULT 0;
ALTER TABLE coser_profile ADD COLUMN min_lead_minutes INT NOT NULL DEFAULT 1440;
ALTER TABLE coser_profile ADD COLUMN version INT NOT NULL DEFAULT 0;
ALTER TABLE coser_profile ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE coser_profile ADD CONSTRAINT ck_coser_policy CHECK (booking_step_minutes BETWEEN 1 AND 1440 AND buffer_before_minutes>=0 AND buffer_after_minutes>=0 AND min_lead_minutes>=1440 AND (hourly_price_cents IS NULL OR hourly_price_cents>0));
ALTER TABLE coser_profile ADD CONSTRAINT ck_coser_status CHECK (verification_status IN ('unverified','pending','verified','rejected') AND service_status IN ('inactive','active','paused','disabled'));
UPDATE coser_profile SET verification_status='verified',service_status='active' WHERE user_id IN (SELECT user_id FROM identity_verification WHERE status='verified' AND mode='coser');

CREATE TABLE coser_role (
  id VARCHAR(64) PRIMARY KEY,
  coser_id VARCHAR(64) NOT NULL REFERENCES coser_profile(user_id),
  ip_name VARCHAR(100) NOT NULL,
  character_name VARCHAR(100) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'active',
  version INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(coser_id,ip_name,character_name),
  UNIQUE(id,coser_id),
  CHECK (status IN ('active','paused')),
  CHECK (LENGTH(TRIM(ip_name))>0 AND LENGTH(TRIM(character_name))>0)
);

ALTER TABLE service_post ADD COLUMN content_kind VARCHAR(16) NOT NULL DEFAULT 'commission';
ALTER TABLE service_post ADD COLUMN role_id VARCHAR(64);
ALTER TABLE service_post ADD COLUMN ip_name_snapshot VARCHAR(100);
ALTER TABLE service_post ADD COLUMN character_name_snapshot VARCHAR(100);
ALTER TABLE service_post ADD COLUMN service_mode VARCHAR(16) NOT NULL DEFAULT 'offline';
ALTER TABLE service_post ADD COLUMN cover_object_key VARCHAR(512);
ALTER TABLE service_post ADD COLUMN cover_url VARCHAR(2048);
ALTER TABLE service_post ADD COLUMN currency CHAR(3) NOT NULL DEFAULT 'CNY';
ALTER TABLE service_post ADD COLUMN max_hours INT NOT NULL DEFAULT 8;
ALTER TABLE service_post ADD COLUMN service_terms CLOB;
ALTER TABLE service_post ADD COLUMN visibility VARCHAR(16) NOT NULL DEFAULT 'public';
ALTER TABLE service_post ADD COLUMN audit_status VARCHAR(16) NOT NULL DEFAULT 'approved';
ALTER TABLE service_post ADD COLUMN version INT NOT NULL DEFAULT 0;
ALTER TABLE service_post ADD COLUMN deleted_at TIMESTAMP;
ALTER TABLE service_post ADD CONSTRAINT fk_service_role_owner FOREIGN KEY(role_id,coser_id) REFERENCES coser_role(id,coser_id);
ALTER TABLE service_post ADD CONSTRAINT uq_service_owner_role UNIQUE(id,coser_id,role_id);
ALTER TABLE service_post ADD CONSTRAINT ck_service_kind CHECK (content_kind IN ('showcase','commission'));
ALTER TABLE service_post ADD CONSTRAINT ck_service_duration CHECK (max_hours BETWEEN min_hours AND 24);
ALTER TABLE service_post ADD CONSTRAINT ck_service_visibility CHECK (visibility IN ('public','private'));
ALTER TABLE service_post ADD CONSTRAINT ck_service_audit CHECK (audit_status IN ('draft','pending','approved','rejected'));
CREATE INDEX idx_service_role ON service_post(role_id,status);

CREATE TABLE service_media (
  id VARCHAR(64) PRIMARY KEY,
  service_id VARCHAR(64) NOT NULL REFERENCES service_post(id),
  media_type VARCHAR(16) NOT NULL,
  object_key VARCHAR(512) NOT NULL,
  thumbnail_key VARCHAR(512),
  media_url VARCHAR(2048),
  thumbnail_url VARCHAR(2048),
  processing_status VARCHAR(16) NOT NULL DEFAULT 'processing',
  audit_status VARCHAR(16) NOT NULL DEFAULT 'pending',
  sort_order INT NOT NULL,
  width_px INT,
  height_px INT,
  duration_ms BIGINT,
  deleted_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(service_id,sort_order),
  UNIQUE(object_key),
  CHECK (media_type IN ('image','video')),
  CHECK (processing_status IN ('processing','ready','failed')),
  CHECK (audit_status IN ('pending','approved','rejected')),
  CHECK (sort_order>=0 AND (width_px IS NULL OR width_px>0) AND (height_px IS NULL OR height_px>0) AND (duration_ms IS NULL OR duration_ms>0))
);
CREATE INDEX idx_service_media_eligible ON service_media(service_id,deleted_at,processing_status,audit_status,sort_order);

ALTER TABLE availability_slot ADD COLUMN timezone_name VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai';
ALTER TABLE availability_slot ADD COLUMN version INT NOT NULL DEFAULT 0;
ALTER TABLE availability_slot ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE availability_slot ADD CONSTRAINT ck_availability_status CHECK (status IN ('open','closed'));
ALTER TABLE availability_slot ADD CONSTRAINT uq_availability_owner UNIQUE(id,coser_id);

ALTER TABLE booking_order ADD COLUMN request_key VARCHAR(128);
ALTER TABLE booking_order ADD COLUMN role_id VARCHAR(64);
ALTER TABLE booking_order ADD COLUMN ip_snapshot VARCHAR(100);
ALTER TABLE booking_order ADD COLUMN character_snapshot VARCHAR(100);
ALTER TABLE booking_order ADD COLUMN service_title_snapshot VARCHAR(200);
ALTER TABLE booking_order ADD COLUMN seller_name_snapshot VARCHAR(100);
ALTER TABLE booking_order ADD COLUMN service_terms_snapshot CLOB;
ALTER TABLE booking_order ADD COLUMN media_keys_snapshot CLOB;
ALTER TABLE booking_order ADD COLUMN media_urls_snapshot CLOB;
ALTER TABLE booking_order ADD COLUMN cover_url_snapshot VARCHAR(2048);
ALTER TABLE booking_order ADD COLUMN service_version INT;
ALTER TABLE booking_order ADD COLUMN unit_price_cents BIGINT;
ALTER TABLE booking_order ADD COLUMN duration_minutes INT;
ALTER TABLE booking_order ADD COLUMN paid_amount BIGINT NOT NULL DEFAULT 0;
ALTER TABLE booking_order ADD COLUMN refunded_amount BIGINT NOT NULL DEFAULT 0;
ALTER TABLE booking_order ADD COLUMN payment_status VARCHAR(24) NOT NULL DEFAULT 'unpaid';
ALTER TABLE booking_order ADD COLUMN refund_status VARCHAR(24) NOT NULL DEFAULT 'none';
ALTER TABLE booking_order ADD COLUMN payment_expires_at TIMESTAMP;
ALTER TABLE booking_order ADD COLUMN confirm_expires_at TIMESTAMP;
ALTER TABLE booking_order ADD COLUMN min_lead_minutes_snapshot INT NOT NULL DEFAULT 1440;
ALTER TABLE booking_order ADD COLUMN cancel_policy_code VARCHAR(32) NOT NULL DEFAULT 'COS_CANCEL_V1';
ALTER TABLE booking_order ADD COLUMN free_cancel_before_hours INT NOT NULL DEFAULT 48;
ALTER TABLE booking_order ADD COLUMN partial_cancel_before_hours INT NOT NULL DEFAULT 24;
ALTER TABLE booking_order ADD COLUMN partial_refund_rate_bps INT NOT NULL DEFAULT 5000;
ALTER TABLE booking_order ADD COLUMN cancellation_requested_at TIMESTAMP;
ALTER TABLE booking_order ADD COLUMN cancelled_from_status VARCHAR(32);
ALTER TABLE booking_order ADD COLUMN cancel_reason_code VARCHAR(40);
ALTER TABLE booking_order ADD COLUMN cancel_base_start_at TIMESTAMP;
ALTER TABLE booking_order ADD COLUMN cancel_refund_rate_bps INT;
ALTER TABLE booking_order ADD COLUMN refund_amount_due BIGINT NOT NULL DEFAULT 0;
ALTER TABLE booking_order ADD COLUMN cancellation_fee_amount BIGINT NOT NULL DEFAULT 0;
ALTER TABLE booking_order ADD COLUMN completion_submitted_at TIMESTAMP;
ALTER TABLE booking_order ADD COLUMN completion_resolution_type VARCHAR(24);
ALTER TABLE booking_order ADD CONSTRAINT fk_order_role_owner FOREIGN KEY(role_id,coser_id) REFERENCES coser_role(id,coser_id);
ALTER TABLE booking_order ADD CONSTRAINT fk_order_service_role_owner FOREIGN KEY(service_id,coser_id,role_id) REFERENCES service_post(id,coser_id,role_id);
ALTER TABLE booking_order ADD CONSTRAINT uq_order_parties UNIQUE(id,customer_id,coser_id);
ALTER TABLE booking_order ADD CONSTRAINT uq_order_coser UNIQUE(id,coser_id);
ALTER TABLE booking_order ADD CONSTRAINT ck_order_amounts CHECK (payable_amount>=0 AND paid_amount>=0 AND refunded_amount>=0 AND refunded_amount<=paid_amount AND refund_amount_due>=0 AND cancellation_fee_amount>=0);
ALTER TABLE booking_order ADD CONSTRAINT ck_order_policy CHECK (min_lead_minutes_snapshot>=1440 AND free_cancel_before_hours>=partial_cancel_before_hours AND partial_refund_rate_bps BETWEEN 0 AND 10000 AND (cancel_refund_rate_bps IS NULL OR cancel_refund_rate_bps BETWEEN 0 AND 10000));
CREATE UNIQUE INDEX uq_order_customer_request ON booking_order(customer_id,request_key);

CREATE TABLE order_appointment (
  id VARCHAR(64) PRIMARY KEY,
  order_id VARCHAR(64) NOT NULL UNIQUE REFERENCES booking_order(id),
  coser_id VARCHAR(64) NOT NULL REFERENCES coser_profile(user_id),
  availability_id VARCHAR(64) NOT NULL REFERENCES availability_slot(id),
  service_start_at TIMESTAMP NOT NULL,
  service_end_at TIMESTAMP NOT NULL,
  buffer_before_minutes INT NOT NULL DEFAULT 0,
  buffer_after_minutes INT NOT NULL DEFAULT 0,
  occupied_start_at TIMESTAMP NOT NULL,
  occupied_end_at TIMESTAMP NOT NULL,
  timezone_name VARCHAR(64) NOT NULL,
  status VARCHAR(24) NOT NULL,
  hold_expires_at TIMESTAMP NOT NULL,
  confirm_expires_at TIMESTAMP,
  confirmed_at TIMESTAMP,
  released_at TIMESTAMP,
  actual_start_at TIMESTAMP,
  actual_end_at TIMESTAMP,
  version INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (service_end_at>service_start_at AND occupied_start_at<=service_start_at AND occupied_end_at>=service_end_at),
  CHECK (buffer_before_minutes>=0 AND buffer_after_minutes>=0),
  CHECK (status IN ('held','awaiting_coser','confirmed','in_service','ended','cancelled','expired')),
  CHECK (confirm_expires_at IS NULL OR confirm_expires_at<service_start_at)
);
CREATE INDEX idx_appointment_coser_occupied ON order_appointment(coser_id,occupied_start_at,occupied_end_at,status);
ALTER TABLE order_appointment ADD CONSTRAINT fk_appointment_order_owner FOREIGN KEY(order_id,coser_id) REFERENCES booking_order(id,coser_id);
ALTER TABLE order_appointment ADD CONSTRAINT fk_appointment_window_owner FOREIGN KEY(availability_id,coser_id) REFERENCES availability_slot(id,coser_id);

CREATE TABLE service_review (
  id VARCHAR(64) PRIMARY KEY,
  order_id VARCHAR(64) NOT NULL UNIQUE REFERENCES booking_order(id),
  customer_id VARCHAR(64) NOT NULL REFERENCES app_user(id),
  coser_id VARCHAR(64) NOT NULL REFERENCES coser_profile(user_id),
  score INT NOT NULL CHECK (score BETWEEN 1 AND 5),
  body CLOB NOT NULL,
  image_keys CLOB,
  image_urls CLOB,
  anonymous BOOLEAN NOT NULL DEFAULT FALSE,
  status VARCHAR(16) NOT NULL DEFAULT 'pending',
  audit_reason VARCHAR(500),
  coser_reply CLOB,
  reply_status VARCHAR(16) NOT NULL DEFAULT 'none',
  replied_at TIMESTAMP,
  version INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (status IN ('pending','public','hidden','deleted')),
  CHECK (reply_status IN ('none','pending','public','hidden'))
);

CREATE TABLE order_refund (
  id VARCHAR(64) PRIMARY KEY,
  refund_no VARCHAR(64) NOT NULL UNIQUE,
  order_id VARCHAR(64) NOT NULL REFERENCES booking_order(id),
  request_key VARCHAR(64) NOT NULL,
  reason_code VARCHAR(40) NOT NULL,
  actor_type VARCHAR(16) NOT NULL,
  actor_id VARCHAR(64) REFERENCES app_user(id),
  cancellation_requested_at TIMESTAMP,
  service_start_snapshot TIMESTAMP,
  cancel_policy_code VARCHAR(32) NOT NULL,
  calculation_base_amount BIGINT NOT NULL,
  refund_rate_bps INT NOT NULL,
  refund_amount BIGINT NOT NULL,
  currency CHAR(3) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'pending',
  payment_provider VARCHAR(32),
  provider_refund_no VARCHAR(128),
  submitted_at TIMESTAMP,
  succeeded_at TIMESTAMP,
  failed_at TIMESTAMP,
  failure_code VARCHAR(100),
  retry_count INT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(order_id,request_key),
  CHECK (actor_type IN ('customer','coser','system','admin')),
  CHECK (status IN ('pending','processing','succeeded','failed','closed')),
  CHECK (calculation_base_amount>0 AND refund_rate_bps BETWEEN 1 AND 10000 AND refund_amount>0 AND refund_amount<=calculation_base_amount AND retry_count>=0)
);
ALTER TABLE service_review ADD CONSTRAINT fk_review_order_parties FOREIGN KEY(order_id,customer_id,coser_id) REFERENCES booking_order(id,customer_id,coser_id);
CREATE INDEX idx_refund_order_status ON order_refund(order_id,status);

ALTER TABLE order_event ADD COLUMN event_key VARCHAR(96);
ALTER TABLE order_event ADD COLUMN actor_type VARCHAR(16);
ALTER TABLE order_event ADD COLUMN reason_code VARCHAR(64);
ALTER TABLE order_event ADD COLUMN occurred_at TIMESTAMP;
CREATE UNIQUE INDEX uq_order_event_key ON order_event(order_id,event_key);
