-- Initial schema migration
-- Enables TimescaleDB extension if available
CREATE EXTENSION IF NOT EXISTS timescaledb;

-- Placeholder table to verify migrations work
CREATE TABLE IF NOT EXISTS schema_info (
    id SERIAL PRIMARY KEY,
    version VARCHAR(50) NOT NULL,
    applied_at TIMESTAMPTZ DEFAULT NOW()
);

INSERT INTO schema_info (version) VALUES ('1.0.0');
