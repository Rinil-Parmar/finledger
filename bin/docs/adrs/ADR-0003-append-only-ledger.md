# ADR-0003: Append-only ledger enforced by database triggers

- Status: Accepted
- Date: 2026-09-11

## Context

Financial history must be trustworthy. If ledger rows can be edited or deleted, the books
can be silently rewritten — by a bug, a bad migration, or someone with database access —
and no one can prove what really happened.

## Decision

Make `journal_entries` and `ledger_entries` **append-only**. Enforce it in the **database**
with `BEFORE UPDATE OR DELETE` triggers that raise an exception. Corrections are made by
posting a new **reversing** entry, never by changing the past.

## Why it matters

- **Defence in depth:** even if the application has a bug, or someone runs SQL by hand, the
  database itself refuses to alter history.
- **Auditability:** an auditor sees the original event *and* its correction — the full story.
- Enforcing it in the DB (not just app code) means the guarantee holds no matter what writes.

## Example

```sql
UPDATE ledger_entries SET amount = 999 WHERE id = '...';
-- ERROR: Ledger history is append-only: UPDATE on table ledger_entries is not allowed.

DELETE FROM ledger_entries WHERE id = '...';
-- ERROR: ... is not allowed. Post a reversing entry instead.
```

To fix a wrong $100 deposit, you don't edit it — you post its mirror:

```
Original:  Debit CASH 100 / Credit wallet 100
Reversal:  Debit wallet 100 / Credit CASH 100   → net effect zero, both kept in history
```

## Consequences

- A test (`LedgerImmutabilityIT`) proves the database rejects UPDATE and DELETE.
- Corrections require reversing entries (compensating transactions — Phase 4), which is the
  correct financial model anyway.
