-- One row per delivery attempt: the "why did it fail?" record.
CREATE TABLE delivery_attempts (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    delivery_id      UUID        NOT NULL REFERENCES webhook_deliveries (id) ON DELETE CASCADE,
    attempt          INT         NOT NULL,
    attempted_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    status_code      INT,
    error            TEXT,
    response_snippet TEXT,
    duration_ms      BIGINT      NOT NULL,
    UNIQUE (delivery_id, attempt)
);
