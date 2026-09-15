# ADR-0002: Double-entry ledger for all money movements

- Status: Accepted
- Date: 2026-09-11

## Context

Money has to be recorded so that it can be trusted and audited. The naive approach —
`UPDATE accounts SET balance = balance + amount` — has no history, no built-in error
check, and makes it easy to create or lose money through a bug.

## Decision

Record every money movement as a **double-entry journal**: a set of two or more lines
where **total debits equal total credits**. Each line posts to a *ledger account* (a
"bucket") as either a DEBIT or a CREDIT. The service layer refuses to save a journal that
does not balance.

## Why it matters

- **Self-checking:** because every journal must balance, money can never appear from
  nowhere or vanish — it only *moves between buckets*. A bug that breaks this is rejected,
  not silently persisted.
- **Auditable:** the journal is a permanent record of *what happened*, not just the current
  number.
- **It's the industry standard** — the same technique banks have used for 500 years.

## Example

Deposit $100 into a customer's wallet:

```
Journal (DEPOSIT), debits == credits:
   Debit  CASH (asset, the bank pool)        100
   Credit CUSTOMER_CASH:acc_1 (liability)    100
100 == 100  ✅  → saved
```

If a buggy version tried to credit only 90, the debits (100) ≠ credits (90) check fails and
nothing is written.

## Consequences

- Writes go through a single `LedgerService.post(...)` that validates the balance.
- Slightly more to record than a single balance update — but that structure is exactly what
  gives correctness and auditability.
- Enables everything downstream: derived balances (ADR-0004), reconciliation, and reversals.
