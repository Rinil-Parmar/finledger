# ADR-0005: Idempotent money movement via an Idempotency-Key

- Status: Accepted
- Date: 2026-09-13

## Context

Networks and clients retry. A request can time out *after* the server processed it; the
client, seeing no response, sends it again. Without protection, a retried transfer moves
money **twice**.

## Decision

Money-moving requests (transfers) require a client-supplied **`Idempotency-Key`** header.
The server records each key and processes it **once**; a repeat of the same key returns the
**stored response** instead of doing the work again. Reusing a key with a *different* request
body is rejected with `409`.

The key is claimed atomically with `INSERT ... ON CONFLICT DO NOTHING` **in the same
transaction as the transfer**, so even simultaneous duplicates are serialized by the database.

## Why it matters

- **Retries become safe:** the same request, sent any number of times, has one effect.
- **Handles concurrency, not just sequential retries:** two duplicates arriving at once still
  result in a single transfer, because the key claim and the transfer commit together.

## Example

```
POST /transfers  Idempotency-Key: k1   {A→B, 200}
   1st time  → claim k1, do transfer, store response          → 201 (money moves)
   retry     → k1 already claimed, hashes match → replay      → 201 (no new movement)
   k1 + different body (A→B, 999) → hash mismatch              → 409 Conflict
```

## Consequences

- Adds an `idempotency_records` table and requires the header on transfers.
- Failed requests aren't cached (the transaction rolls back), so a client may safely retry a
  genuine failure.
- Consumers of events must also be idempotent — see ADR-0008.
