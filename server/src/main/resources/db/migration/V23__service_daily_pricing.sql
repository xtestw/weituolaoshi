ALTER TABLE service_post ADD COLUMN pricing_unit VARCHAR(8) NOT NULL DEFAULT 'hour';
ALTER TABLE service_post ADD COLUMN price_per_day BIGINT;
ALTER TABLE service_post ADD CONSTRAINT ck_service_pricing_unit CHECK (pricing_unit IN ('hour', 'day', 'both'));
ALTER TABLE service_post ADD CONSTRAINT ck_service_day_price CHECK (price_per_day IS NULL OR price_per_day > 0);
ALTER TABLE booking_order ADD COLUMN unit_price_unit VARCHAR(8) NOT NULL DEFAULT 'hour';
ALTER TABLE price_quote ADD COLUMN pricing_unit VARCHAR(8) NOT NULL DEFAULT 'hour';
ALTER TABLE price_quote ADD COLUMN unit_price_cents BIGINT;
ALTER TABLE service_post ALTER COLUMN max_hours SET DEFAULT 24;
UPDATE service_post SET max_hours=24;
