# Postman collection — FinLedger (Phase 1 & 2)

A ready-to-run collection for the API: create accounts, deposit, withdraw, read the
derived balance, and make idempotent account-to-account transfers. Each request has
built-in tests. Shared state flows through collection variables:
`{{externalId}}` (source account), `{{destExternalId}}` (destination), and
`{{idempotencyKey}}` (generated once and reused to demonstrate a safe retry).

## Files
- `FinLedger.postman_collection.json` — the requests + tests
- `FinLedger.local.postman_environment.json` — `baseUrl` (http://localhost:8080)

## Prerequisites
Start the app first (Postman only sends HTTP; it does not run the app):

```bash
docker compose up -d          # start PostgreSQL
./mvnw spring-boot:run        # start the app on http://localhost:8080
```

## Import into Postman
1. Open Postman -> **Import** (top-left).
2. Drag in **both** JSON files from this folder.
3. Top-right environment dropdown -> select **FinLedger Local**.

## Run it
- **All at once:** click the collection -> **Run** (Collection Runner). Requests execute
  in order and the Test Results tab shows every assertion passing.
- **One at a time:** start with **Create source account**, then follow the list down.

## Run headless with Newman (optional)
[Newman](https://github.com/postmanlabs/newman) runs the collection from the command line:

```bash
npm install -g newman
newman run postman/FinLedger.postman_collection.json -e postman/FinLedger.local.postman_environment.json
```

## What the tests check
| Request | Expected |
|---|---|
| Health check | 200, database `UP` |
| Create source account | 201, captures `{{externalId}}` |
| Deposit 1000 / 500 | balance 1000 -> 1500 |
| Get balance | 1500 |
| Withdraw 300 | 1200 |
| Create destination account | 201, captures `{{destExternalId}}` |
| Transfer 200 (A -> B) | 201, source 1000, destination 200; generates `{{idempotencyKey}}` |
| Transfer retry (same key) | 201, **same** transfer reference, source still 1000 (money moved once) |
| Transfer without Idempotency-Key | 400 |
| Withdraw beyond balance | 400 |
| Negative deposit | 400 |
| Outbox events for the transfer | 200, contains a `transfer.completed` event (status flips to PUBLISHED within ~1s) |
| Consumed events (Kafka) | 200, list of events the Kafka consumer received |

> Re-running: the Collection Runner creates fresh accounts each run, so the expected
> balances stay correct every time.
