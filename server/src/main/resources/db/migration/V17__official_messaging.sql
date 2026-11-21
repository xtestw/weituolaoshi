INSERT INTO app_user(id, nickname, role) VALUES ('official-platform', '委托助手', 'official');

ALTER TABLE conversation ADD COLUMN kind VARCHAR(20) NOT NULL DEFAULT 'peer';
ALTER TABLE conversation ADD COLUMN official_target_id VARCHAR(64);
ALTER TABLE conversation ALTER COLUMN coser_id DROP NOT NULL;
ALTER TABLE conversation ADD CONSTRAINT ck_conversation_kind CHECK (
  (kind = 'peer' AND coser_id IS NOT NULL AND official_target_id IS NULL) OR
  (kind = 'official' AND coser_id IS NULL AND service_id IS NULL AND order_id IS NULL AND official_target_id = customer_id)
);
CREATE UNIQUE INDEX uq_conversation_official_user ON conversation(official_target_id);

INSERT INTO admin_permission VALUES ('messages:send', '官号向用户发送私信');
INSERT INTO admin_role_permission VALUES ('super_admin', 'messages:send');
