# ADR-0010: Reconciliation against an external settlement feed

- Status: Accepted
- Date: 2026-11-04

## Context

Internal consistency (debits = credits, derived balances, assets = liabilities) proves our
books agree with *themselves*. It does not prove they agree with the **real bank** holding
the pooled cash. A deposit can succeed in our API but never actually settle externally. A
financial institution must check its ledger against the outside world — every day.

## Decision

Compare the internal CASH movements against an external settlement feed (simulated by
`external_transactions`), matching **by reference**, and record every disagreement as a typed
**break**: `MISSING_EXTERNAL`, `MISSING_INTERNAL`, `AMOUNT_MISMATCH`, `DIRECTION_MISMATCH`. A
run stores summary counts; breaks are surfaced for operators, not auto-fixed.

## Why it matters

- **"Successful in the API" ≠ "settled at the bank."** Reconciliation is the proof, not the
  promise. It also catches problems on the *external* side, which we don't control.
- **It's the safety net** for everything else: idempotency, the outbox, and locking reduce
  drift; reconciliation catches whatever slips through.
- **Typed breaks drive a workflow:** missing/amount/direction each triage differently (pending
  settlement vs fee vs possible fraud), and are typically resolved with a reversal (ADR-0009).

## Example

```
Internal CASH movements     External feed          Break
  jnl_1 DEBIT 1000    ==     jnl_1 DEBIT 1000   →   (matched)
  jnl_2 DEBIT  300    →      (none)             →   MISSING_EXTERNAL
  (none)              ←      ext_9 DEBIT 200    →   MISSING_INTERNAL
  jnl_3 DEBIT  500    ≠      jnl_3 DEBIT 999    →   AMOUNT_MISMATCH
```

## Consequences

- Adds `external_transactions`, `reconciliation_runs`, `reconciliation_breaks`, and endpoints
  to feed records, run reconciliation, and list breaks.
- Reconciliation currently covers cash movements (deposits/withdrawals/reversals), which are
  the bank-facing ones; internal transfers never touch the pool.
