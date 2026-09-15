# ADR-0007: Transactional outbox for reliable event publishing

- Status: Accepted
- Date: 2026-09-15

## Context

When money moves, other systems need to know (notifications, analytics, reconciliation).
The obvious approach is a **dual write**: save to the database, then publish to Kafka. But a
database commit and a Kafka publish are two different systems and cannot be made atomic —
either can fail independently:

- DB commits, Kafka publish fails → the movement happened but no one is told.
- Kafka publish succeeds, DB rolls back → systems act on money that never moved (worse).

## Decision

In the request, **only write to the database** — and write the event to an `outbox_events`
table **in the same transaction** as the money movement. A separate background publisher
(ADR-0008) later sends outbox rows to Kafka.

## Why it matters

- **No dual-write inconsistency:** the business change and the "event to publish" commit
  together or not at all — they can never disagree.
- **The request stays fast and simple:** no network call to Kafka on the hot path.

## Example

```
ONE transaction:
   save journal + ledger entries + transfer row
   INSERT outbox_events (transfer.completed, PENDING)
   COMMIT        ← both, or neither

A rejected transfer rolls back → no outbox row (proven by OutboxIT).
```

## Consequences

- Adds an `outbox_events` table and an `OutboxService.append(...)` called inside each
  money-movement transaction (deliberately *not* `@Transactional` itself).
- Delivery to Kafka is deferred and becomes at-least-once — see ADR-0008.
