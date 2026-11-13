INSERT INTO identity_verification(id, user_id, legal_gender, adult, status, verified_at)
VALUES ('iv-demo-customer', 'demo-customer', 'F', TRUE, 'verified', CURRENT_TIMESTAMP);
INSERT INTO identity_verification(id, user_id, legal_gender, adult, status, verified_at)
VALUES ('iv-demo-coser', 'demo-coser-f', 'F', TRUE, 'verified', CURRENT_TIMESTAMP);
INSERT INTO availability_slot(id, coser_id, start_at, end_at, status)
VALUES ('slot-demo-1', 'demo-coser-f', TIMESTAMP '2026-11-16 10:00:00', TIMESTAMP '2026-11-16 22:00:00', 'open');
INSERT INTO venue(id, provider_poi_id, name, city, district, category, address, review_status)
VALUES ('venue-demo-expo', 'demo-poi-expo', '国家会展中心', '上海', '青浦', 'exhibition', '上海市青浦区崧泽大道333号', 'approved');
