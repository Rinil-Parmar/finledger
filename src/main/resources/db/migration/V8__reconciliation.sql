-- V8: Reconciliation.
--
-- Our ledger records what WE think happened to cash. The external institution (the real
-- bank holding the pooled account) has its own settlement records. Reconciliation compares
-- the two and records any discrepancies ("breaks"). external_transactions simulates the
-- external institution's settlement feed.

CREATE TABLE external_transactions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference   VARCHAR(50)   NOT NULL,          -- matches an internal journal reference
    direction   VARCHAR(6)    NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount      NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    currency    VARCHAR(3)    NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_external_reference ON external_transactions (reference);

CREATE TABLE reconciliation_runs (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    status        VARCHAR(20) NOT NULL DEFAULT 'RUNNING',   -- RUNNING, COMPLETED
    total_matched INT         NOT NULL DEFAULT 0,
    total_breaks  INT         NOT NULL DEFAULT 0,
    started_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at  TIMESTAMPTZ
);

CREATE TABLE reconciliation_breaks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id          UUID        NOT NULL REFERENCES reconciliation_runs (id),
    break_type      VARCHAR(30) NOT NULL,
    -- MISSING_EXTERNAL, MISSING_INTERNAL, AMOUNT_MISMATCH, DIRECTION_MISMATCH
    reference       VARCHAR(50) NOT NULL,
    internal_amount NUMERIC(19,4),
    external_amount NUMERIC(19,4),
    detail          TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_breaks_run ON reconciliation_breaks (run_id);
