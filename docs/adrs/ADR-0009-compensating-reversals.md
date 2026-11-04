# ADR-0009: Compensating reversals instead of edits

- Status: Accepted
- Date: 2026-11-03

## Context

The ledger is append-only (ADR-0003) — rows can never be updated or deleted. But mistakes
happen (a wrong transfer, a refund, a failed settlement). We need a way to *undo* a
movement without breaking the "never edit history" rule.

## Decision

Undo a journal by posting a **compensating reversal**: a new `REVERSAL` journal whose ledger
lines mirror the original (debits and credits swapped). The original is untouched; the
reversal links back to it via `reverses_journal_id`. A journal can be reversed only once —
checked by seeing whether any journal already points at it (a read, not an update).

## Why it matters

- **Keeps history intact and auditable:** both the mistake and its correction are permanent,
  linked records — exactly what an auditor needs.
- **Respects append-only even for the guard:** we detect "already reversed" without mutating
  the original row, so the immutability rule is never bent.
- **It's how real money systems correct:** refunds, chargebacks, reversed ACH, trade busts —
  all are new, linked entries, never deletions.

## Example

```
Deposit (jnl_1):   Debit CASH 100 / Credit wallet 100   → balance 100
Reverse jnl_1:     Credit CASH 100 / Debit wallet 100   → balance 0
Both journals remain; reversing jnl_1 again → 409 Conflict.
```

## Consequences

- Adds a `reverses_journal_id` column and `POST /api/v1/reversals`; emits a `journal.reversed`
  event through the outbox.
- A reversal is itself a cash movement, so it also shows up in reconciliation.
- Reversals are an admin/ops correction and are not blocked on the destination's current
  balance (an edge case a real system handles as a follow-up break).
