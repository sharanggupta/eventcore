-- Revoked keys stay on record for the audit trail; they just stop authenticating.
ALTER TABLE api_keys ADD COLUMN revoked_at TIMESTAMPTZ;
