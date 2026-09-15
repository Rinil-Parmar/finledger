# FinLedger — Data Model

This document explains every table, how they relate, and walks a real scenario through
them row by row. The schema is created by Flyway migrations in
[`src/main/resources/db/migration`](../src/main/resources/db/migration); Hibernate runs in
`validate` mode and never changes it.

## The one big idea

FinLedger keeps a **bank's-eye view** of money. All customers' cash sits in one pooled bank
account (an **asset**); each customer's wallet is money the platform **owes** them (a
**liability**). Every movement is a **double-entry journal**: two or more lines whose debits
equal its credits. A balance is never stored — it is the **sum of a ledger account's lines**.

```
ASSETS            =            LIABILITIES
CASH (bank pool)  =   Σ CUSTOMER_CASH:<account>  (what we owe every customer)
```

## Tables at a glance

| Table | What it holds | Written by |
|---|---|---|
| `accounts` | Customer cash accounts (no balance column) | account creation |
| `ledger_accounts` | Chart of accounts — the "buckets" (CASH, one per wallet) | account creation + seed |
| `journal_entries` | One row per financial event (deposit, withdrawal, transfer) | every money movement |
| `ledger_entries` | The individual debit/credit lines (append-only) | every money movement |
| `transfers` | First-class record of an account→account transfer | transfers |
| `idempotency_records` | Remembers processed `Idempotency-Key`s | transfers |
| `outbox_events` | Events awaiting (or already) published to Kafka | every money movement |
| `app_metadata` | Trivial key/value from the Phase 0 smoke slice | migration seed |

### How they relate

```
accounts (acc_001) ──paired 1:1──► ledger_accounts (CUSTOMER_CASH:acc_001, LIABILITY)
                                            ▲
journal_entries (one event)                 │ many lines reference one bucket
     │ 1                                     │
     │ has many                             │
     ▼ 2+ balanced lines                    │
ledger_entries ─────────────────────────────┘   (journal_id, ledger_account_id)

transfers.journal_id ──► journal_entries.id     (a transfer points at its journal)
outbox_events.aggregate_id  = a transfer reference or account id (loose link, by value)
```

## Table details

### `accounts`
A customer's cash account. **There is deliberately no `balance` column** — the balance is
derived from the ledger.

| column | notes |
|---|---|
| `id` (UUID, PK) | internal id |
| `external_id` | human-facing id, e.g. `acc_6b3f5faa8add` |
| `owner_name`, `currency`, `status` | `status` ∈ {ACTIVE, CLOSED} |

### `ledger_accounts` — the chart of accounts
The accounting "buckets". `normal_balance` says which side **increases** the bucket.

| column | notes |
|---|---|
| `code` | unique, e.g. `CASH` or `CUSTOMER_CASH:acc_001` |
| `account_type` | ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE |
| `normal_balance` | DEBIT (assets/expenses) or CREDIT (liabilities/equity/revenue) |

Seeded once: `CASH` (ASSET, normal DEBIT). Each new account adds `CUSTOMER_CASH:<id>`
(LIABILITY, normal CREDIT).

### `journal_entries`
One row = one financial event. **Immutable** (no `updated_at`; protected by a trigger).

| column | notes |
|---|---|
| `reference` | human-facing id, e.g. `jnl_…` |
| `entry_type` | DEPOSIT, WITHDRAWAL, TRANSFER |

### `ledger_entries` — the money lines
The heart of the ledger. **Append-only**: a trigger blocks every `UPDATE`/`DELETE`.

| column | notes |
|---|---|
| `journal_id` → `journal_entries` | which event this line belongs to |
| `ledger_account_id` → `ledger_accounts` | which bucket |
| `direction` | DEBIT or CREDIT |
| `amount` | `NUMERIC(19,4)`, always **positive** |

### `transfers`
A business-level record of a transfer, linked to the journal that moved the money.
Has a DB check that source ≠ destination.

### `idempotency_records`
Keyed by the client's `Idempotency-Key`. Stores a hash of the request and the saved
response, so a retry returns the original result instead of moving money again.

### `outbox_events`
Events written **in the same transaction** as the money movement, then published to Kafka
by a background job.

| column | notes |
|---|---|
| `event_type` | `cash.deposited`, `cash.withdrawn`, `transfer.completed` |
| `payload` | JSON describing what happened |
| `status` | PENDING → PUBLISHED |
| `attempts`, `published_at` | retry bookkeeping |

## Deriving a balance

For a customer wallet (a **liability**, so credits increase it):

```sql
balance = SUM(CASE WHEN direction = 'CREDIT' THEN amount ELSE -amount END)
          FROM ledger_entries
          WHERE ledger_account_id = <that wallet>;
```

## Worked example

Create account `acc_001`, deposit ₹1,000, then transfer ₹200 to `acc_002`.

**After creating `acc_001`** — `ledger_accounts` now has:

| code | type | normal_balance |
|---|---|---|
| `CASH` | ASSET | DEBIT |
| `CUSTOMER_CASH:acc_001` | LIABILITY | CREDIT |

**Deposit ₹1,000 into `acc_001`** — one journal, two balanced lines:

`journal_entries`: `{ reference: jnl_1, entry_type: DEPOSIT }`

| ledger_account | direction | amount |
|---|---|---|
| `CASH` | DEBIT | 1000.0000 |
| `CUSTOMER_CASH:acc_001` | CREDIT | 1000.0000 |

`acc_001` balance = credits − debits = **1000**. `outbox_events` gets a `cash.deposited` row (PENDING).

**Transfer ₹200 from `acc_001` → `acc_002`** — the pool (`CASH`) doesn't move:

`journal_entries`: `{ reference: jnl_2, entry_type: TRANSFER }`

| ledger_account | direction | amount |
|---|---|---|
| `CUSTOMER_CASH:acc_001` | DEBIT | 200.0000 |
| `CUSTOMER_CASH:acc_002` | CREDIT | 200.0000 |

`transfers` gets a row (`status: COMPLETED`, `journal_id: jnl_2`), and `outbox_events` gets a
`transfer.completed` row.

**Balances now:**
- `acc_001` = 1000 − 200 = **800**
- `acc_002` = **200**
- `CASH` (asset) = **1000** — the transfer never touched `CASH` (the money stayed in the
  pool, it just changed owner), so it still equals total liabilities (800 + 200). ✅

**The invariant that always holds:** `CASH` (total assets) = sum of all `CUSTOMER_CASH:*`
balances (total liabilities). If it ever doesn't, money was created or destroyed — a bug.

## The event pipeline (Phase 3)

```
money movement ── one transaction ──► ledger rows + outbox_events (PENDING)
                                             │
                    OutboxPublisher (@Scheduled, FOR UPDATE SKIP LOCKED)
                                             │  send + mark PUBLISHED
                                          Kafka topic  finledger.events
                                             │
                                        EventConsumer (idempotent)
```

You can observe this over HTTP: `GET /api/v1/outbox/events?aggregateId=<ref>` shows the row
flip from `PENDING` to `PUBLISHED`, and `GET /api/v1/outbox/consumed` shows what the consumer
received.
