-- Supports GET /v1/events?type=... with the same newest-first keyset ordering.
CREATE INDEX events_by_type ON events (type, time DESC, id DESC);
