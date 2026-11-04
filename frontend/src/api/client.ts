import type { Account, Totals } from "../types";

// All requests go to /api/* which Vite proxies to the backend on :8080.
const BASE = "/api/v1";

async function http<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(BASE + path, {
    headers: { "Content-Type": "application/json" },
    ...init,
  });
  const text = await res.text();
  if (!res.ok) {
    throw new Error(`${res.status}: ${text}`);
  }
  return (text ? JSON.parse(text) : undefined) as T;
}

export const api = {
  listAccounts: () => http<Account[]>("/accounts"),
  getTotals: () => http<Totals>("/ledger/totals"),
  createAccount: (ownerName: string, currency = "CAD") =>
    http<{ externalId: string }>("/accounts", {
      method: "POST",
      body: JSON.stringify({ ownerName, currency }),
    }),
};
