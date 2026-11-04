# ADR-0006: Concurrency-safe balances via pessimistic row locking

- Status: Accepted
- Date: 2026-09-15

## Context

Two transfers from the same account can run at the same instant. If each one reads the
balance, checks funds, and posts independently, both can pass the check against the *same*
starting balance and overdraw the account. Idempotency (ADR-0005) does not help — these are
two *different* requests, not a retry.

## Decision

Before checking the balance, **lock the account rows** with `SELECT ... FOR UPDATE`
(`@Lock(PESSIMISTIC_WRITE)`). This forces balance-changing operations on the same account to
run **one at a time**. When a transfer touches two accounts, they are locked in a **consistent
(sorted) order** so two transfers can never deadlock by locking the same pair in opposite orders.

## Why it matters

- **No overdrafts under load:** the second transfer waits for the first to commit, then sees
  the updated balance before its own funds check.
- **No deadlocks:** a fixed lock ordering removes the classic "A waits for B, B waits for A" trap.
- A deliberate trade-off: **correctness over maximum throughput** on the money-write path.

## Example

```
Account balance 1000. Two transfers of 800 arrive together:
  without locks:  both read 1000, both approve 800  → balance -600  ❌
  with locks:     T1 locks, posts (→200), commits;
                  T2 then reads 200, 800 > 200       → rejected      ✅
```

Proven by `TransferConcurrencyIT`: 20 parallel transfers against a balance funding only 10 →
exactly 10 succeed, 10 rejected, balance never negative.

## Consequences

- Transfers serialize per account; fine for this domain, where correctness dominates.
- Locks are held only for the brief transaction; ordering keeps them deadlock-free.
