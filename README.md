# FinLedger

A production-grade **financial Book of Record** - an append-only, double-entry ledger system built with the same engineering patterns used at Wealthsimple, Stripe, and every serious fintech.

> **Not a CRUD app.** This is a financial infrastructure project that demonstrates correctness under concurrency, auditability through immutable history, and reliable event delivery through a transactional outbox to Kafka.

---

## What makes this different

In most apps, updating a balance looks like `balance = balance + amount`. In financial systems, that's a bug waiting to happen.

FinLedger records every money movement as **balanced double-entry journal postings** against an **append-only ledger**. An account balance is always *derived* by summing postings - never stored and mutated. Every customer wallet is a **liability** (money the bank owes), and the pooled cash is an **asset**. So:

```
Assets = Liabilities    (always, provably, in real-time)
```

This is the same invariant that every bank, brokerage, and payment processor enforces.

---

## System guarantees

| Guarantee | How it works | Formal property |
|---|---|---|
| **Double-entry ledger** | Every journal entry creates balanced debit/credit pairs | `Sigma debits = Sigma credits` for every journal |
| **Append-only history** | PostgreSQL triggers prevent UPDATE and DELETE on ledger rows | `INSERT only - no UPDATE, no DELETE` |
| **Derived balances** | Balance computed from journal entries, never stored | `balance(a) = Sigma credits(a) - Sigma debits(a)` |
| **Idempotent transfers** | Unique key per transfer, INSERT ON CONFLICT DO NOTHING | `f(x) = f(f(x))` - exactly-once semantics |
| **Concurrency safety** | SELECT FOR UPDATE with ordered lock acquisition | `for all concurrent tx: balance >= 0` |
| **Transactional outbox** | Event written in same DB transaction as business change | `BEGIN -> write(ledger) + write(outbox) -> COMMIT` |
| **At-least-once delivery** | Background publisher drains outbox to Kafka; consumers are idempotent | `PENDING -> poll -> send -> ACK -> PUBLISHED` |
| **Compensating reversals** | Corrections post a mirror journal; history is never edited | `reverse(tx) -> new compensating entry` |
| **Reconciliation** | Internal ledger checked against external settlement feeds | `internal intersection external -> {matched, break}` |

---

## Architecture

```
                       HTTP (REST)
                            |
                 AccountController / TransferController / OutboxController
                            |
        AccountService - CashService - TransferService - IdempotencyService
                            |
                       LedgerService  -- enforces debits == credits
                            |
                        PostgreSQL  (source of truth, append-only ledger)
                            |  same transaction
                       outbox_events (PENDING)
                            |
                     OutboxPublisher  (@Scheduled, FOR UPDATE SKIP LOCKED)
                            |
                          Kafka  -->  EventConsumer (idempotent)
```

---

## Tech stack

| Concern | Choice |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.4 |
| Build | Maven (wrapper - no global install needed) |
| Database | PostgreSQL 16 |
| Migrations | Flyway (8 versioned migrations; Hibernate validates only) |
| Money type | `BigDecimal` / `NUMERIC(19,4)` - never `double` |
| Messaging | Apache Kafka (KRaft mode, spring-kafka) |
| Tests | JUnit 5 + Testcontainers (real PostgreSQL and Kafka) |
| CI | GitHub Actions |
| Frontend | React 18 + TypeScript + Vite 5 |
| API testing | Postman collection + Newman |

---

## Frontend

A multi-page product website built with React, TypeScript, and Vite:

- **Landing page** - hero, feature cards with formal guarantees, "How Banking Works" section showing real-world deposit/transfer/stock-purchase ledger entries, architecture flow
- **Accounts dashboard** - live invariant proof (Assets = Liabilities), account cards, create/deposit/withdraw/transfer actions with animated balance counters
- **Account detail** - balance hero, tabbed actions (deposit/withdraw/transfer), journal entry timeline with colored debit/credit indicators
- **Event pipeline** - outbox pattern explainer, 6-step event lifecycle visualization with animated flow, delivery guarantee cards, live event log with status tracking and polling
- **Dark mode** - full dark theme via `prefers-color-scheme` with manual override support
- **Inter typeface** - professional fintech typography

---

## Quick start

Requires JDK 17, Docker, and Node.js 18+.

```bash
# 1. Start infrastructure (PostgreSQL + Kafka)
docker compose up -d

# 2. Run the backend
./mvnw spring-boot:run

# 3. Run the frontend (in a second terminal)
cd frontend && npm install && npm run dev
```

The app is at `http://localhost:5173`. Backend API at `http://localhost:8080`.

> **Note:** The local Postgres container maps host port **5433** (to avoid clashing with a native PostgreSQL on 5432). Kafka is on **9092**.

---

## API

All under `http://localhost:8080`.

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/accounts` | Create a customer account |
| `GET` | `/api/v1/accounts` | List all accounts |
| `GET` | `/api/v1/accounts/{id}/balance` | Derived balance |
| `GET` | `/api/v1/accounts/{id}/entries` | Journal entries for account |
| `POST` | `/api/v1/accounts/{id}/deposits` | Deposit cash |
| `POST` | `/api/v1/accounts/{id}/withdrawals` | Withdraw cash |
| `POST` | `/api/v1/transfers` | Transfer between accounts (requires `Idempotency-Key` header) |
| `POST` | `/api/v1/reversals` | Reverse a journal with a compensating entry |
| `POST` | `/api/v1/reconciliation/external` | Record an external settlement |
| `POST` | `/api/v1/reconciliation/runs` | Run reconciliation |
| `GET` | `/api/v1/reconciliation/runs/{id}/breaks` | List breaks from a recon run |
| `GET` | `/api/v1/outbox/events` | Inspect outbox events |
| `GET` | `/api/v1/outbox/consumed` | Events the Kafka consumer received |
| `GET` | `/api/v1/ledger/totals` | Asset/liability totals with balance check |

Errors: `400` invalid input, `404` unknown entity, `409` conflict (idempotency or already-reversed).

---

## Tests

```bash
./mvnw verify
```

**30 integration tests** across 14 test classes, running against **real PostgreSQL and Kafka** via Testcontainers. No mocks for infrastructure. GitHub Actions runs the same suite on every push.

Key test scenarios:
- Deposit and withdrawal with balance verification
- Concurrent transfers (20 threads) proving zero overdrafts
- Idempotency key deduplication
- Outbox event creation and Kafka publishing
- Ledger immutability (UPDATE/DELETE blocked by triggers)
- Compensating reversals
- Reconciliation break detection

---

## Testing with Postman

A ready-to-run Postman collection lives in [`postman/`](postman/). Start the app, import the JSON files, and run the collection. See [`postman/README.md`](postman/README.md).

---

## Architecture decisions

10 ADRs document every significant design choice:

| ADR | Decision |
|---|---|
| [ADR-0001](docs/adrs/ADR-0001-stack-and-foundations.md) | Stack and foundations |
| [ADR-0002](docs/adrs/ADR-0002-double-entry-ledger.md) | Double-entry ledger design |
| [ADR-0003](docs/adrs/ADR-0003-append-only-ledger.md) | Append-only immutability |
| [ADR-0004](docs/adrs/ADR-0004-derived-balances.md) | Derived balances (never stored) |
| [ADR-0005](docs/adrs/ADR-0005-idempotency.md) | Idempotency mechanism |
| [ADR-0006](docs/adrs/ADR-0006-concurrency-locking.md) | Concurrency and locking strategy |
| [ADR-0007](docs/adrs/ADR-0007-transactional-outbox.md) | Transactional outbox pattern |
| [ADR-0008](docs/adrs/ADR-0008-at-least-once-kafka.md) | At-least-once Kafka delivery |
| [ADR-0009](docs/adrs/ADR-0009-compensating-reversals.md) | Compensating reversals |
| [ADR-0010](docs/adrs/ADR-0010-reconciliation.md) | Reconciliation engine |

---

## Build roadmap

| Phase | Focus | Status |
|---|---|---|
| 0 | Foundation: scaffold, Postgres, Flyway, CI | Done |
| 1 | Append-only double-entry ledger, deposits, withdrawals, derived balances | Done |
| 2 | Transfers, idempotency, concurrency safety | Done |
| 3 | Transactional outbox, Kafka delivery | Done |
| 4 | Compensating reversals, reconciliation engine | Done |
| 5 | Frontend: multi-page product website with live dashboard | Done |

---

## Data model

See [docs/data-model.md](docs/data-model.md) for the full schema, worked examples, and T-account diagrams.

---

Built by **Rinil Parmar** - 30 integration tests, 8 Flyway migrations, 10 ADRs
