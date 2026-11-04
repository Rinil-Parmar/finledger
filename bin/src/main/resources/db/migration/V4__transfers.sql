-- V4: Account-to-account transfers.
--
-- A transfer is recorded two ways:
--   1. As a first-class business record in this `transfers` table (who -> who, how much).
--   2. As a balanced journal in the ledger (debit source wallet, credit destination wallet).
-- The transfers row links to the journal that moved the money.

CREATE TABLE transfers (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference          VARCHAR(50)  UNIQUE NOT NULL,       -- e.g. 'txn_...'
    source_account_id  UUID          NOT NULL REFERENCES accounts(id),
    dest_account_id    UUID          NOT NULL REFERENCES accounts(id),
    amount             NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    currency           VARCHAR(3)    NOT NULL,
    status             VARCHAR(20)   NOT NULL DEFAULT 'COMPLETED',
    journal_id         UUID          REFERENCES journal_entries(id),
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    -- A transfer must move money between two DIFFERENT accounts.
    CONSTRAINT transfers_distinct_accounts CHECK (source_account_id <> dest_account_id)
);

CREATE INDEX idx_transfers_source ON transfers (source_account_id);
CREATE INDEX idx_transfers_dest ON transfers (dest_account_id);
