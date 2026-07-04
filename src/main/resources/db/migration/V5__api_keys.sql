-- API keys are stored only as SHA-256 hashes; the plaintext is shown once at issuance.
CREATE TABLE api_keys (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name       TEXT        NOT NULL,
    key_hash   TEXT        NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
