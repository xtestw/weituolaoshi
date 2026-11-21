CREATE TABLE face_verification_attempt (
  token_hash VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL REFERENCES app_user(id),
  mode VARCHAR(20) NOT NULL CHECK (mode IN ('customer','coser')),
  expires_at TIMESTAMP NOT NULL,
  consumed_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_face_attempt_user ON face_verification_attempt(user_id);

-- 旧的姓名/身份证二要素结论不能当作人脸核身结果。演示夹具没有供应商流水号，仍供本地测试。
UPDATE identity_verification SET status='unverified', review_reason='请完成人脸核身' WHERE provider_reference IS NOT NULL AND status='verified';
UPDATE coser_profile SET verification_status='unverified', service_status='inactive' WHERE user_id IN (SELECT user_id FROM identity_verification WHERE status='unverified');
