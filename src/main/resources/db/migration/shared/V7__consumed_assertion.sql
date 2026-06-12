-- ============================================================================
-- B10 — service-assertion replay store (shared/public schema).
-- A sibling service authenticates the federated token-exchange + integration
-- push endpoints with a short-lived signed "service assertion" (RS256). Even a
-- valid, in-lifetime assertion must be usable only ONCE: the first verification
-- records its jti here; a second presentation of the same jti before its exp is
-- rejected as a replay. Persisted (not in-memory) so the guard survives restarts
-- and holds across replicas — every node sees the same consumed set.
-- See ServiceCredentialValidator + .planning/reviews/e2e-audit-2026-06-11.md (B10).
-- ============================================================================
CREATE TABLE consumed_assertion (
    jti           VARCHAR(255) PRIMARY KEY,        -- the assertion's unique id (required claim)
    source_system VARCHAR(32)  NOT NULL,           -- ACCOUNTING | SCREENING (which key verified it)
    consumed_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at    TIMESTAMPTZ  NOT NULL            -- the assertion's own exp; rows are safe to purge past this
);

-- Opportunistic cleanup: delete rows whose assertion has already expired (a replay of an expired
-- assertion is independently rejected by the exp check, so the row is no longer needed).
CREATE INDEX idx_consumed_assertion_expires_at ON consumed_assertion(expires_at);
