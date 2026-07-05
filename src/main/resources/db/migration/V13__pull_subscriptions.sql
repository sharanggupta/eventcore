-- Named durable cursors over the event log: each consumer pulls at its own
-- pace, commits its position explicitly, and can rewind to replay history.
CREATE TABLE pull_subscriptions (
    name          TEXT        PRIMARY KEY,
    position_time TIMESTAMPTZ,
    position_id   UUID,
    event_types   JSONB,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
