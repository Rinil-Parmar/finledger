# ADR-0001: Technology stack and foundational conventions

- Status: Accepted
- Date: 2026-09-08

## Context

FinLedger is a financial Book of Record. The system must be correct under retries,
concurrency, and partial failure, and its financial history must be auditable. We need a
stack that supports strong database guarantees, real integration testing, and versioned
schema evolution, while staying free to run locally.

## Decision

- **Java 17 + Spring Boot 4.1.** Java 17 is installed and is the supported baseline for
  Spring Boot 4.x; there is no need to install Java 21. Spring Boot gives a mature testing
  and data-access ecosystem.
- **PostgreSQL 16 as the single source of truth.** It provides exact `NUMERIC` arithmetic,
  strong constraints, and triggers we will use to enforce ledger immutability.
- **Flyway owns the schema.** Hibernate runs in `ddl-auto: validate` and is never allowed
  to create or alter tables. Every schema change is a reviewed, versioned migration.
- **`BigDecimal` / `NUMERIC(19,4)` for all monetary values.** Binary floating point
  (`double`/`float`) is never used for money because it cannot represent decimal values
  exactly.
- **Testcontainers for integration tests.** Tests run against a real PostgreSQL container
  rather than an in-memory substitute, so behaviour under real SQL semantics is verified.
- **GitHub Actions CI** runs `./mvnw verify` (including Testcontainers) on every push and PR.

## Consequences

- Running the app and its tests requires Docker to be available.
- `validate` mode means an entity/schema mismatch fails fast at startup — intentional.
- We accept Spring Boot 4.x being relatively new in exchange for staying on a current,
  supported line; the APIs we depend on (Web MVC, Data JPA, Flyway) are stable across the
  3.x → 4.x boundary.
