UPDATE identity_verification SET status='unverified', review_reason='请重新完成自动核验' WHERE status='pending' AND mode='coser';
DELETE FROM admin_role_permission WHERE permission_code='identity:review';
DELETE FROM admin_permission WHERE code='identity:review';
UPDATE admin_role SET name='认证查看员' WHERE id='reviewer';
