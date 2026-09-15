# ADR-0004: Balances derived from the ledger, never stored

- Status: Accepted
- Date: 2026-09-11

## Context

A balance can be kept two ways: as a stored number you update on every movement, or as a
value computed from history. A stored balance is one more thing that can drift out of sync
with the ledger (a partial failure, a missed update, a bug) — and then which number is
right?

## Decision

Do **not** store a balance. The `accounts` table has **no balance column**. A balance is
always computed by summing that account's ledger entries.

## Why it matters

- **Cannot drift:** there is a single source of truth (the ledger). The balance is a
  *view* of it, so it can never disagree with the history that produced it.
- **Explainable:** you can always answer "why is the balance 800?" by listing the entries.
- **Consistent with append-only** (ADR-0003): history is the truth; the balance follows.

## Example

For a customer wallet (a liability — credits increase it):

```sql
balance = SUM(CASE WHEN direction = 'CREDIT' THEN amount ELSE -amount END)
          FROM ledger_entries WHERE ledger_account_id = <wallet>;
```

```
deposit  +1000 (CREDIT)
transfer  -200 (DEBIT)
------------------------
balance =  800   ← recomputed, never stored
```

## Consequences

- Reads sum entries. At small scale this is trivial; at large scale it is addressed with
  indexes and, if ever needed, periodic balance snapshots — *without* giving up the ledger
  as the source of truth.
- Trade-off deliberately favours **correctness over read micro-optimisation** on the money path.
