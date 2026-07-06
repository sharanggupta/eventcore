-- The exponential backoff resets each redelivery cycle. Record the attempt count
-- at which the current cycle began, so the exponent is (attempts - cycle_start) —
-- independent of the live max-attempts config. Deriving the cycle start from
-- gives_up_after minus the current max-attempts used to make the exponent negative
-- (and the backoff shorter than the base interval) if max-attempts was lowered
-- while deliveries were mid-flight.
ALTER TABLE webhook_deliveries ADD COLUMN cycle_start_attempts INT NOT NULL DEFAULT 0;
