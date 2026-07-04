-- Outbox for webhook deliveries: one row per (event, subscription),
-- written in the same transaction as the event so deliveries survive restarts.
CREATE TABLE webhook_deliveries (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id        UUID        NOT NULL,
    subscription_id UUID        NOT NULL REFERENCES webhook_subscriptions (id),
    body            JSONB       NOT NULL,
    status          TEXT        NOT NULL DEFAULT 'pending',
    attempts        INT         NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX webhook_deliveries_due
    ON webhook_deliveries (next_attempt_at)
    WHERE status = 'pending';
