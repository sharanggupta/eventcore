-- Endpoints that receive ingested events over HTTP.
CREATE TABLE webhook_subscriptions (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    url        TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
