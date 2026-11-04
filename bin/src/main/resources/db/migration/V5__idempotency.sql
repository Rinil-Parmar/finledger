-- V5: Idempotency records.
--
-- Lets a client safely retry a money-moving request: it sends a unique
-- Idempotency-Key, and the server remembers keys it has already processed so the
-- same request performs its effect exactly once.
--
-- response_status / response_body are filled in AFTER the work succeeds; they are
-- null while the owning transaction is still running.

CREATE TABLE idempotency_records (
    idempotency_key  VARCHAR(255) PRIMARY KEY,
    request_hash     VARCHAR(64)  NOT NULL,   -- SHA-256 of the request's key fields
    response_status  INT,
    response_body    TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
