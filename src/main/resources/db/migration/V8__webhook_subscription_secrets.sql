-- Every subscription signs its deliveries with a dedicated secret.
ALTER TABLE webhook_subscriptions ADD COLUMN secret TEXT;

-- Backfill any pre-existing rows with a generated secret.
UPDATE webhook_subscriptions
SET secret = 'whsec_' || md5(random()::text) || md5(random()::text)
WHERE secret IS NULL;

ALTER TABLE webhook_subscriptions ALTER COLUMN secret SET NOT NULL;
