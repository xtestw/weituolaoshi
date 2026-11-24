ALTER TABLE service_post ADD COLUMN place_name VARCHAR(200);
CREATE TABLE service_question (
  id VARCHAR(64) PRIMARY KEY,
  service_id VARCHAR(64) NOT NULL REFERENCES service_post(id),
  text VARCHAR(60) NOT NULL,
  sort_order INT NOT NULL,
  UNIQUE(service_id,sort_order)
);
