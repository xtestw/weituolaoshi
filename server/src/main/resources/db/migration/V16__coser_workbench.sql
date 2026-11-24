-- Coser 工作台：异常责任判定、结算字段、客服工单

ALTER TABLE order_exception ADD COLUMN responsible_party VARCHAR(16);
ALTER TABLE order_exception ADD COLUMN fulfillment_incomplete BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE order_exception ADD COLUMN review_note VARCHAR(500);
ALTER TABLE order_exception ADD COLUMN reviewed_at TIMESTAMP;
ALTER TABLE order_exception ADD COLUMN reviewed_by VARCHAR(64);
ALTER TABLE order_exception ADD CONSTRAINT ck_order_exception_status
  CHECK (status IN ('pending','approved','rejected','closed'));
ALTER TABLE order_exception ADD CONSTRAINT ck_order_exception_party
  CHECK (responsible_party IS NULL OR responsible_party IN ('coser','customer','none','both'));
CREATE INDEX idx_order_exception_order ON order_exception(order_id, status, created_at);

ALTER TABLE settlement ADD COLUMN coser_id VARCHAR(64);
ALTER TABLE settlement ADD COLUMN settled_at TIMESTAMP;
ALTER TABLE settlement ADD COLUMN currency CHAR(3) NOT NULL DEFAULT 'CNY';
UPDATE settlement s SET coser_id=(SELECT o.coser_id FROM booking_order o WHERE o.id=s.order_id)
  WHERE coser_id IS NULL;
ALTER TABLE settlement ADD CONSTRAINT fk_settlement_coser FOREIGN KEY(coser_id) REFERENCES coser_profile(user_id);
ALTER TABLE settlement ADD CONSTRAINT ck_settlement_status CHECK (status IN ('pending','settled','held','cancelled'));
ALTER TABLE settlement ADD CONSTRAINT ck_settlement_amounts CHECK (gross_amount>=0 AND platform_fee>=0 AND payable_amount>=0 AND payable_amount=gross_amount-platform_fee);
CREATE INDEX idx_settlement_coser_status ON settlement(coser_id, status, created_at);

CREATE TABLE support_ticket (
  id VARCHAR(64) PRIMARY KEY,
  requester_id VARCHAR(64) NOT NULL REFERENCES app_user(id),
  role_snapshot VARCHAR(20) NOT NULL,
  category VARCHAR(40) NOT NULL,
  subject VARCHAR(120) NOT NULL,
  body VARCHAR(2000) NOT NULL,
  order_id VARCHAR(64) REFERENCES booking_order(id),
  status VARCHAR(24) NOT NULL DEFAULT 'open',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (role_snapshot IN ('customer','coser')),
  CHECK (status IN ('open','in_progress','resolved','closed')),
  CHECK (LENGTH(TRIM(category))>0 AND LENGTH(TRIM(subject))>0 AND LENGTH(TRIM(body))>0)
);
CREATE INDEX idx_support_ticket_requester ON support_ticket(requester_id, created_at);

INSERT INTO admin_permission VALUES
  ('orders:read','查看订单与异常'),
  ('orders:review','审核履约异常与责任判定'),
  ('support:read','查看客服工单'),
  ('support:write','处理客服工单');
INSERT INTO admin_role_permission SELECT 'super_admin', code FROM admin_permission
  WHERE code IN ('orders:read','orders:review','support:read','support:write');
