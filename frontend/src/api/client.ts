import type { Account, Totals, MovementResult, TransferResult, LedgerEntry, OutboxEvent } from "../types";

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

function uuid(): string {
  return crypto.randomUUID();
}

export const api = {
  listAccounts: () => http<Account[]>("/accounts"),
  getTotals: () => http<Totals>("/ledger/totals"),
  createAccount: (ownerName: string, currency = "CAD") =>
    http<{ externalId: string }>("/accounts", {
      method: "POST",
      body: JSON.stringify({ ownerName, currency }),
    }),

  deposit: (externalId: string, amount: number, currency = "CAD") =>
    http<MovementResult>(`/accounts/${externalId}/deposits`, {
      method: "POST",
      body: JSON.stringify({ amount, currency }),
    }),

  withdraw: (externalId: string, amount: number, currency = "CAD") =>
    http<MovementResult>(`/accounts/${externalId}/withdrawals`, {
      method: "POST",
      body: JSON.stringify({ amount, currency }),
    }),

  transfer: (source: string, destination: string, amount: number, currency = "CAD") =>
    http<TransferResult>("/transfers", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Idempotency-Key": uuid(),
      },
      body: JSON.stringify({
        sourceAccountId: source,
        destinationAccountId: destination,
        amount,
        currency,
      }),
    }),

  getLedgerEntries: (externalId: string) =>
    http<LedgerEntry[]>(`/accounts/${externalId}/entries`),

  getOutboxEvents: () =>
    http<OutboxEvent[]>("/outbox/events"),

  getConsumedEvents: () =>
    http<string[]>("/outbox/consumed"),
};
