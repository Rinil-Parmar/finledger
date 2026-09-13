# Postman collection — FinLedger (Phase 1)

A ready-to-run collection for the Phase 1 API: create an account, deposit, withdraw,
and read the derived balance. Each request has built-in tests, and **Create account**
saves the new account id into the `{{externalId}}` variable that later requests reuse.

## Files
- `FinLedger.postman_collection.json` — the requests + tests
- `FinLedger.local.postman_environment.json` — the `baseUrl` (http://localhost:8080)

## Prerequisites
Start the app first (Postman only sends HTTP; it does not run the app):

```bash
docker compose up -d          # start PostgreSQL
./mvnw spring-boot:run        # start the app on http://localhost:8080
```

## Import into Postman
1. Open Postman → **Import** (top-left).
2. Drag in **both** JSON files from this folder (or Import → Files → select them).
3. Top-right environment dropdown → select **FinLedger Local**.

## Run it
- **One at a time:** open a request and click **Send**. Start with **Create account**
  (it captures `{{externalId}}`), then Deposit / Balance / Withdraw.
- **All at once:** click the collection → **Run** (Collection Runner) → **Run FinLedger
  API (Phase 1)**. The requests execute in order and the **Test Results** tab shows every
  assertion passing (balances of 1000 → 1500 → 1200, and the two 400 rejections).

## What the tests check
| Request | Expected |
|---|---|
| Health check | 200, database `UP` |
| Create account | 201, `externalId` starts with `acc_`, captured into the variable |
| Deposit 1000 / 500 | 201, `newBalance` = 1000 then 1500 |
| Get balance | 200, `balance` = 1500 |
| Withdraw 300 | 201, `newBalance` = 1200 |
| Withdraw beyond balance | 400, message mentions insufficient funds |
| Negative deposit | 400 |

> Re-running: the Collection Runner starts by creating a **fresh** account each time, so
> the expected balances above stay correct on every run.
