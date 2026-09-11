-- V2: Core double-entry ledger.
--
-- Introduces customer-facing cash accounts, the chart of accounts (ledger_accounts),
-- and the append-only journal/ledger tables that record every money movement.
-- A customer's balance is DERIVED by summing ledger entries -- never stored.

-- Customer-facing cash accounts.
-- Deliberately NO balance column: the balance is computed from the ledger.
CREATE TABLE accounts (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_id  VARCHAR(50)  UNIQUE NOT NULL,             -- human-facing id, e.g. 'acc_001'
    owner_name   VARCHAR(255) NOT NULL,
    currency     VARCHAR(3)   NOT NULL DEFAULT 'CAD',
    status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'    -- ACTIVE, CLOSED
                 CHECK (status IN ('ACTIVE', 'CLOSED')),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Chart of accounts: the accounting "buckets" that entries post against.
-- Examples: 'CASH' (asset), 'CUSTOMER_CASH:acc_001' (liability owed to a customer).
-- normal_balance says which side INCREASES the account:
--   ASSET/EXPENSE increase on DEBIT; LIABILITY/EQUITY/REVENUE increase on CREDIT.
CREATE TABLE ledger_accounts (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code           VARCHAR(100) UNIQUE NOT NULL,
    name           VARCHAR(255) NOT NULL,
    account_type   VARCHAR(20)  NOT NULL
                   CHECK (account_type IN ('ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE')),
    normal_balance VARCHAR(6)   NOT NULL
                   CHECK (normal_balance IN ('DEBIT', 'CREDIT')),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- A journal is ONE financial event (e.g. one deposit). Its ledger entries must balance
-- (total debits = total credits). Journals are immutable -- note there is no updated_at.
CREATE TABLE journal_entries (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference    VARCHAR(50)  UNIQUE NOT NULL,             -- human-facing id, e.g. 'jnl_...'
    entry_type   VARCHAR(30)  NOT NULL,                    -- DEPOSIT, WITHDRAWAL, ...
    description  TEXT,
    posted_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Individual debit/credit lines. Append-only: never updated or deleted (enforced next step).
-- amount is always POSITIVE; the direction column says whether it is a debit or a credit.
CREATE TABLE ledger_entries (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    journal_id        UUID          NOT NULL REFERENCES journal_entries(id),
    ledger_account_id UUID          NOT NULL REFERENCES ledger_accounts(id),
    direction         VARCHAR(6)    NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount            NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    currency          VARCHAR(3)    NOT NULL,
    posted_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ledger_entries_account ON ledger_entries (ledger_account_id);
CREATE INDEX idx_ledger_entries_journal ON ledger_entries (journal_id);

-- Seed the single global platform cash account (an ASSET, increases on DEBIT).
INSERT INTO ledger_accounts (code, name, account_type, normal_balance)
VALUES ('CASH', 'Platform cash', 'ASSET', 'DEBIT');
