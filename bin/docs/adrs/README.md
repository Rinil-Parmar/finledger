# Architecture Decision Records

Each ADR captures one significant decision: the context, the choice, **why** it matters
(in plain language), a small example, and the consequences. They double as a map of how
FinLedger is designed and why.

| # | Decision | Phase |
|---|---|---|
| [0001](ADR-0001-stack-and-foundations.md) | Technology stack & foundational conventions | 0 |
| [0002](ADR-0002-double-entry-ledger.md) | Double-entry ledger for all money movements | 1 |
| [0003](ADR-0003-append-only-ledger.md) | Append-only ledger enforced by database triggers | 1 |
| [0004](ADR-0004-derived-balances.md) | Balances derived from the ledger, never stored | 1 |
| [0005](ADR-0005-idempotency.md) | Idempotent money movement via an Idempotency-Key | 2 |
| [0006](ADR-0006-concurrency-locking.md) | Concurrency-safe balances via pessimistic row locking | 2 |
| [0007](ADR-0007-transactional-outbox.md) | Transactional outbox for reliable event publishing | 3 |
| [0008](ADR-0008-at-least-once-kafka.md) | At-least-once Kafka delivery with idempotent consumers | 3 |

Format: **Context → Decision → Why → Example → Consequences.**
