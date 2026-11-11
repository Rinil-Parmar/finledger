import type { Account, Totals, MovementResult, TransferResult, LedgerEntry, OutboxEvent } from "../types";

const BASE = "/api/v1";

let authToken: string | null = null;

export function setToken(token: string | null) {
  authToken = token;
  if (token) {
    try { localStorage.setItem("finledger_token", token); } catch {}
  } else {
    try { localStorage.removeItem("finledger_token"); } catch {}
  }
}

export function getToken(): string | null {
  if (authToken) return authToken;
  try { authToken = localStorage.getItem("finledger_token"); } catch {}
  return authToken;
}

export function clearToken() {
  authToken = null;
  try { localStorage.removeItem("finledger_token"); } catch {}
}

async function http<T>(path: string, init?: RequestInit): Promise<T> {
  const token = getToken();
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (token) headers["Authorization"] = `Bearer ${token}`;

  const mergedHeaders = { ...headers, ...(init?.headers as Record<string, string> || {}) };
  const res = await fetch(BASE + path, {
    ...init,
    headers: mergedHeaders,
  });
  const text = await res.text();
  if (!res.ok) {
    if (res.status === 401) {
      clearToken();
      throw new Error("Session expired - please log in again");
    }
    throw new Error(`${res.status}: ${text}`);
  }
  return (text ? JSON.parse(text) : undefined) as T;
}

function uuid(): string {
  return crypto.randomUUID();
}

export interface AuthResponse {
  token: string;
  email: string;
  role: string;
}

export const api = {
  register: (email: string, password: string, role = "USER") =>
    http<AuthResponse>("/auth/register", {
      method: "POST",
      body: JSON.stringify({ email, password, role }),
    }),

  login: (email: string, password: string) =>
    http<AuthResponse>("/auth/login", {
      method: "POST",
      body: JSON.stringify({ email, password }),
    }),

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

  getAuditLog: () =>
    http<AuditEntry[]>("/ledger/audit"),
};

export interface AuditEntry {
  reference: string;
  type: string;
  description: string;
  performedBy: string | null;
}
