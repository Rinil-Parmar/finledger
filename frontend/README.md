# FinLedger — Frontend

A small React + TypeScript + Vite app that visualizes the FinLedger backend: accounts and
derived balances, and the `CASH = Σ liabilities` invariant. It talks to the backend only
over HTTP (`/api/*`), proxied to `http://localhost:8080` by Vite in dev (so there's no CORS).

Pinned to **Vite 5 / React 18** for compatibility with Node 20.15.

## Run it

1. Start the backend first (from the repo root): `docker compose up -d` then `.\mvnw.cmd spring-boot:run`.
2. In this folder:

```bash
npm install     # first time only
npm run dev
```

3. Open http://localhost:5173.

## Layout

```
src/
├── main.tsx            # React entry
├── App.tsx             # the dashboard
├── types.ts            # API response types
├── api/client.ts       # all backend calls, typed
└── index.css           # styles
```

Build order: FE-0 accounts + totals · FE-1 actions · FE-2 ledger explorer ·
FE-3 live event pipeline · FE-4 invariant bar.
