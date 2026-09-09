# FinLedger

A production-minded **financial Book of Record**: an append-only, double-entry ledger
that records what actually happened to money, with idempotent APIs, atomic event
publication, and automated reconciliation.

> This is a learning project built to demonstrate how financial backend systems differ
> from ordinary CRUD applications — correctness, auditability, and safe behaviour under
> retries, concurrency, and partial failure.

## Core idea

Domain services decide whether something *should* happen. The **Book of Record** records
what *did* happen. Money movements are never expressed as `balance = balance + x`. They
are recorded as balanced double-entry journal postings against an **append-only** ledger,
and every account balance is *derived* from those postings — never stored and mutated.

Corrections never edit history. A wrong posting is fixed with a **compensating reversal**,
so the full financial history stays auditable.

## System guarantees (target)

- ✅ Double-entry ledger — every journal balances (`Σ debits == Σ credits`)
- ✅ Append-only financial history — ledger rows are never updated or deleted (DB-enforced)
- ✅ Balances derived from the ledger, never stored
- ⬜ Idempotent money-movement APIs (same request N times → one financial effect) *(Phase 2)*
- ⬜ Concurrency-safe balances (no overdraft under parallel requests) *(Phase 2)*
- ⬜ Transactional outbox — DB commit and event publish are atomic *(Phase 3)*
- ⬜ Automated reconciliation against an external institution *(Phase 4)*

## Tech stack

| Concern            | Choice                                  |
|--------------------|-----------------------------------------|
| Language           | Java 17                                 |
| Framework          | Spring Boot 4.1                          |
| Build              | Maven (wrapper — no global install)     |
| Source of truth    | PostgreSQL 16                           |
| Migrations         | Flyway (schema is Flyway-owned)         |
| Money type         | `BigDecimal` / `NUMERIC(19,4)` — never `double` |
| Tests              | JUnit 5 + Testcontainers (real Postgres)|
| Messaging          | Kafka *(Phase 3)*                        |
| CI                 | GitHub Actions                          |

## Quick start

Requires JDK 17 and Docker.

```bash
# 1. Start local infrastructure (PostgreSQL)
docker compose up -d

# 2. Run the app
./mvnw spring-boot:run

# 3. Smoke test
curl http://localhost:8080/api/v1/ping
curl http://localhost:8080/actuator/health
```

Run the tests (starts a throwaway PostgreSQL container automatically — Docker must be running):

```bash
./mvnw verify
```

## Build roadmap

| Phase | Focus                                                        | Status      |
|-------|--------------------------------------------------------------|-------------|
| 0     | Foundation: scaffold, Postgres, Flyway, CI, smoke slice      | ✅ done      |
| 1     | Domain + append-only double-entry ledger + derived balances  | ⬜ next      |
| 2     | Transfers + idempotency + concurrency safety                 | ⬜ planned   |
| 3     | Transactional outbox + Kafka                                 | ⬜ planned   |
| 4     | Reconciliation + reversals                                   | ⬜ planned   |
| 5+    | (Stretch) trading, security, observability, deployment       | ⬜ later     |

Architecture decisions are recorded in [`docs/adrs/`](docs/adrs/).
