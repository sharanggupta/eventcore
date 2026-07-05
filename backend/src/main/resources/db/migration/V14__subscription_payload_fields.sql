-- Declarative payload minimization: a subscription may allow-list the top-level
-- payload fields it receives. NULL means the full payload (the default).
ALTER TABLE webhook_subscriptions ADD COLUMN payload_fields JSONB;
