# ADR-0008: At-least-once Kafka delivery with idempotent consumers

- Status: Accepted
- Date: 2026-09-15

## Context

The transactional outbox (ADR-0007) guarantees an event is *recorded*. It still has to be
*delivered* to Kafka reliably, even when the broker is briefly down or a send times out
after the broker already stored the message.

## Decision

A background `OutboxPublisher` runs on a timer:

1. fetch a batch of `PENDING` rows with `SELECT ... FOR UPDATE SKIP LOCKED`,
2. send each to the `finledger.events` topic,
3. mark it `PUBLISHED`. A failed send leaves the row `PENDING` for the next poll.

This gives **at-least-once** delivery. Because an event can therefore be delivered more than
once, **consumers must be idempotent**.

## Why it matters

- **Survives broker downtime:** events wait as `PENDING` and go out when Kafka is back — the
  money data was already safely committed.
- **`SKIP LOCKED`** lets multiple app instances publish in parallel without ever sending the
  same event twice.
- **At-least-once + idempotent consumers = exactly-once *effect*** without needing (fragile,
  expensive) exactly-once *delivery* — the standard production pattern.

## Example

```
publisher tick:
   lock PENDING batch (SKIP LOCKED)
   send → Kafka           ── broker down? leave PENDING, retry next tick
   mark PUBLISHED

consumer receives event twice → because it's idempotent, the second is a no-op
```

## Consequences

- Delivery is asynchronous (sub-second in practice); consumers see events shortly after commit.
- Consumers must key their side effects so duplicates are harmless.
- Observable over HTTP: `GET /api/v1/outbox/events` (PENDING→PUBLISHED) and
  `GET /api/v1/outbox/consumed`.
