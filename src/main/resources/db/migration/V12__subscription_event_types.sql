-- Optional per-subscription filter: NULL means the subscription receives every event.
ALTER TABLE webhook_subscriptions ADD COLUMN event_types JSONB;
