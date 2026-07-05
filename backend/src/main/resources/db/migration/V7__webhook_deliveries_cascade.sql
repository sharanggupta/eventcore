-- Deleting a subscription removes its delivery history with it.
ALTER TABLE webhook_deliveries
    DROP CONSTRAINT webhook_deliveries_subscription_id_fkey,
    ADD CONSTRAINT webhook_deliveries_subscription_id_fkey
        FOREIGN KEY (subscription_id)
        REFERENCES webhook_subscriptions (id)
        ON DELETE CASCADE;
