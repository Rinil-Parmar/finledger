# FinLedger

A production-minded **financial Book of Record**: an append-only, double-entry ledger
that records what actually happened to money, with idempotent APIs, concurrency-safe
balances, and reliable event publishing via a transactional outbox to Kafka.

> A learning project built to show how financial backends differ from ordinary CRUD apps —
> correctness, auditability, and safe behaviour under retries, concurrency, and partial failure.

## Core idea

Domain services decide whether something *should* happen. The **Book of Record** records
what *did* happen. Money movements are never expressed as `balance = balance + x`. They are
recorded as balanced double-entry journal postings against an **append-only** ledger, and an
account balance is always *derived* by summing those postings — never stored and mutated.

Every customer wallet is modeled as a **liability** we owe the customer; the pooled bank cash
is an **asset**. So total assets always equal total customer liabilities. See the full data
model with worked examples in **[docs/data-model.md](docs/data-model.md)**.

## System guarantees

- ✅ **Double-entry ledger** — every journal balances (`Σ debits == Σ credits`), enforced in code
- ✅ **Append-only history** — ledger rows can never be updated or deleted (PostgreSQL triggers)
- ✅ **Balances derived** from the ledger, never stored
- ✅ **Idempotent money movement** — same `Idempotency-Key` ⇒ money moves exactly once
- ✅ **Concurrency-safe balances** — row-level locking; parallel transfers can't overdraw (proven by a 20-thread test)
- ✅ **Transactional outbox** — the DB commit and the "event to publish" are one atomic write
- ✅ **At-least-once delivery to Kafka** — a background publisher drains the outbox; consumers are idempotent
- ⬜ Reconciliation against an external institution + compensating reversals *(Phase 4)*

## Architecture

```
                       HTTP (REST)
                            │
                 AccountController / TransferController / OutboxController
                            │
        AccountService · CashService · TransferService · IdempotencyService
                            │
                       LedgerService  ── enforces debits == credits
                            │
                        PostgreSQL  (source of truth, append-only ledger)
                            │  same transaction
                       outbox_events (PENDING)
                            │
                     OutboxPublisher  (@Scheduled, FOR UPDATE SKIP LOCKED)
                            │
                          Kafka  ──►  EventConsumer (idempotent)
```

## Tech stack

| Concern         | Choice                                            |
|-----------------|---------------------------------------------------|
| Language        | Java 17                                           |
| Framework       | Spring Boot 3.4                                    |
| Build           | Maven (wrapper — no global install)               |
| Source of truth | PostgreSQL 16                                      |
| Migrations      | Flyway (schema is Flyway-owned; Hibernate validates) |
| Money type      | `BigDecimal` / `NUMERIC(19,4)` — never `double`   |
| Messaging       | Apache Kafka (spring-kafka)                        |
| Tests           | JUnit 5 + Testcontainers (real PostgreSQL & Kafka)|
| CI              | GitHub Actions                                    |
| API testing     | Postman collection + Newman                       |

## API

All under `http://localhost:8080`.

| Method & path | Purpose | Notes |
|---|---|---|
| `POST /api/v1/accounts` | Create a customer account | body: `ownerName`, `currency` |
| `GET /api/v1/accounts/{externalId}/balance` | Derived balance | — |
| `POST /api/v1/accounts/{externalId}/deposits` | Deposit cash | body: `amount`, `currency` |
| `POST /api/v1/accounts/{externalId}/withdrawals` | Withdraw cash | rejects insufficient funds |
| `POST /api/v1/transfers` | Transfer between accounts | **requires `Idempotency-Key` header** |
| `GET /api/v1/outbox/events` | Inspect outbox events | optional `?aggregateId=` |
| `GET /api/v1/outbox/consumed` | Events the Kafka consumer received | — |
| `GET /actuator/health` | Health (incl. DB) | — |

Errors use standard codes: `400` invalid input, `404` unknown account, `409` idempotency conflict.

## Quick start

Requires JDK 17 and Docker.

```bash
# 1. Start local infrastructure (PostgreSQL + Kafka)
docker compose up -d

# 2. Run the app
./mvnw spring-boot:run        # Git Bash;  on PowerShell: .\mvnw.cmd spring-boot:run

# 3. Try it
curl http://localhost:8080/actuator/health
```

A full worked example (create account → deposit → transfer → observe the Kafka event) is in
[docs/data-model.md](docs/data-model.md#worked-example).

> Note: the local Postgres container maps host port **5433** (to avoid clashing with a native
> PostgreSQL on 5432); Kafka is on **9092**.

## Tests

```bash
./mvnw verify
```

Runs the full suite (**18 integration tests**) against **real PostgreSQL and Kafka**
containers via Testcontainers — Docker must be running. GitHub Actions runs the same on
every push.

## Testing the API with Postman

A ready-to-run Postman collection (with automatic tests, Newman-verified) lives in
[`postman/`](postman/). Start the app, import the two JSON files, and run the collection.
See [`postman/README.md`](postman/README.md).

## Build roadmap

| Phase | Focus                                                        | Status     |
|-------|--------------------------------------------------------------|------------|
| 0     | Foundation: scaffold, Postgres, Flyway, CI, smoke slice      | ✅ done     |
| 1     | Append-only double-entry ledger + deposits/withdrawals + derived balances | ✅ done |
| 2     | Transfers + idempotency + concurrency safety                 | ✅ done     |
| 3     | Transactional outbox + Kafka delivery                        | ✅ done     |
| 4     | Reconciliation + compensating reversals                      | ⬜ planned  |
| 5+    | (Stretch) trading, security, observability, deployment       | ⬜ later    |

Architecture decisions are recorded in [`docs/adrs/`](docs/adrs/).
