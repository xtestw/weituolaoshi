-- The platform no longer requires a Coser deposit. Keep V1/V2 intact for Flyway checksums.
ALTER TABLE coser_profile DROP COLUMN deposit_qualified;

-- Under-24-hour bookings are allowed only with a Coser time confirmation.
-- The application validates that confirmation at quote and order creation.
ALTER TABLE coser_profile DROP CONSTRAINT ck_coser_policy;
ALTER TABLE coser_profile ALTER COLUMN min_lead_minutes SET DEFAULT 0;
UPDATE coser_profile SET min_lead_minutes=0 WHERE min_lead_minutes=1440;
ALTER TABLE coser_profile ADD CONSTRAINT ck_coser_policy CHECK
  (booking_step_minutes BETWEEN 1 AND 1440 AND buffer_before_minutes>=0 AND buffer_after_minutes>=0
   AND min_lead_minutes>=0 AND (hourly_price_cents IS NULL OR hourly_price_cents>0));

ALTER TABLE booking_order DROP CONSTRAINT ck_order_policy;
ALTER TABLE booking_order ALTER COLUMN min_lead_minutes_snapshot SET DEFAULT 0;
ALTER TABLE booking_order ADD CONSTRAINT ck_order_policy CHECK
  (min_lead_minutes_snapshot>=0 AND free_cancel_before_hours>=partial_cancel_before_hours
   AND partial_refund_rate_bps BETWEEN 0 AND 10000
   AND (cancel_refund_rate_bps IS NULL OR cancel_refund_rate_bps BETWEEN 0 AND 10000));
