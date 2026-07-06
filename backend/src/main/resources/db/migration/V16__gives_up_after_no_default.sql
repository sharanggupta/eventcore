-- The give-up budget is always set explicitly by the application (to max-attempts
-- on enqueue, attempts + max-attempts on requeue), so the column default from V11
-- was dead and duplicated the eventcore.webhooks.max-attempts config. Drop it: the
-- app is the single source of truth, and an insert that forgets the budget now fails
-- fast instead of silently defaulting to a stale 5.
ALTER TABLE webhook_deliveries ALTER COLUMN gives_up_after DROP DEFAULT;
