-- Events storage: TimescaleDB hypertable partitioned on time.
-- Hypertables require the partitioning column in the primary key.
CREATE TABLE events (
    id      UUID        NOT NULL DEFAULT gen_random_uuid(),
    time    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    type    TEXT        NOT NULL,
    payload JSONB,
    PRIMARY KEY (time, id)
);

SELECT create_hypertable('events', 'time');
