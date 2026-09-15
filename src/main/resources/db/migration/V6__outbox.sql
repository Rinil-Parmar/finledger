-- V6: Transactional outbox.
--
-- Every money movement writes a row here IN THE SAME TRANSACTION as the business
-- change. Because they commit together, the database and the "event to publish" can
-- never disagree. A separate publisher (Phase 3.2) later drains PENDING rows to Kafka
-- and marks them PUBLISHED.

CREATE TABLE outbox_events (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(50)  NOT NULL,      -- e.g. 'transfer', 'account'
    aggregate_id   VARCHAR(100) NOT NULL,      -- the business id, e.g. a transfer reference
    event_type     VARCHAR(100) NOT NULL,      -- e.g. 'transfer.completed', 'cash.deposited'
    payload        TEXT         NOT NULL,      -- JSON describing what happened
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',   -- PENDING, PUBLISHED
    attempts       INT          NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    published_at   TIMESTAMPTZ
);

-- The publisher only cares about PENDING rows, oldest first. A partial index keeps
-- that lookup fast and small even as published rows pile up.
CREATE INDEX idx_outbox_pending ON outbox_events (created_at) WHERE status = 'PENDING';
