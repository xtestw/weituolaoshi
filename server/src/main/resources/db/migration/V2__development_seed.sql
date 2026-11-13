INSERT INTO app_user(id, nickname, role) VALUES ('demo-customer', '小鱼', 'customer');
INSERT INTO app_user(id, nickname, role) VALUES ('demo-coser-f', '阿梨', 'coser');
INSERT INTO coser_profile(user_id, display_name, city, deposit_qualified) VALUES ('demo-coser-f', '阿梨', '上海', TRUE);
INSERT INTO service_post(id, coser_id, title, city, price_per_hour, min_hours, status)
VALUES ('svc-1', 'demo-coser-f', '三月七｜漫展同行与轻剧情互动', '上海', 18800, 2, 'published');
