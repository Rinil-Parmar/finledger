import { useState } from "react";
import AnimatedMoney from "../components/AnimatedMoney";
import { money, timeAgo } from "../utils/helpers";
import type { Account, LedgerEntry } from "../types";
import type { Page } from "../App";

type ActionTab = "deposit" | "withdraw" | "transfer";

interface AccountDetailProps {
  account: Account;
  entries: LedgerEntry[];
  others: Account[];
  setPage: (p: Page) => void;
  onAction: (tab: ActionTab, amount: number, destAccount?: string) => Promise<void>;
  loading: boolean;
  error: string | null;
  setError: (e: string | null) => void;
}

export default function AccountDetail({ account, entries, others, setPage, onAction, loading, error, setError }: AccountDetailProps) {
  const [actionTab, setActionTab] = useState<ActionTab>("deposit");
  const [amount, setAmount] = useState("");
  const [destAccount, setDestAccount] = useState("");

  async function doAction() {
    if (!amount || Number(amount) <= 0) return;
    if (actionTab === "transfer" && !destAccount) {
      setError("Select a destination account");
      return;
    }
    await onAction(actionTab, Number(amount), destAccount || undefined);
    setAmount("");
  }

  return (
    <div className="page fade-in">
      <button className="back-btn" onClick={() => setPage("accounts")}>&larr; All Accounts</button>

      {/* Balance hero */}
      <div className="detail-hero">
        <div className="dh-avatar">{account.ownerName.charAt(0).toUpperCase()}</div>
        <div className="dh-info">
          <h1 className="dh-name">{account.ownerName}</h1>
          <div className="dh-id">{account.externalId}</div>
        </div>
        <div className="dh-balance">
          <div className="dh-label">Balance</div>
          <div className="dh-amount">$<AnimatedMoney value={account.balance} prefix="" /></div>
          <div className="dh-currency">{account.currency}</div>
        </div>
      </div>

      {/* Actions */}
      <div className="detail-actions">
        <div className="da-tabs">
          {(["deposit", "withdraw", "transfer"] as ActionTab[]).map(t => (
            <button key={t} className={`da-tab ${actionTab === t ? "active" : ""}`}
              onClick={() => { setActionTab(t); setError(null); }}>
              {t === "deposit" ? "Deposit" : t === "withdraw" ? "Withdraw" : "Transfer"}
            </button>
          ))}
        </div>
        <div className="da-form">
          <div className="da-amount">
            <span className="da-prefix">$</span>
            <input type="number" min="0.01" step="0.01" value={amount}
              onChange={e => setAmount(e.target.value)} placeholder="0.00"
              className="da-input" onKeyDown={e => e.key === "Enter" && doAction()} />
          </div>
          {actionTab === "transfer" && (
            <select className="da-select" value={destAccount} onChange={e => setDestAccount(e.target.value)}>
              <option value="">Select destination...</option>
              {others.map(a => <option key={a.externalId} value={a.externalId}>{a.ownerName} - ${money(a.balance)}</option>)}
            </select>
          )}
          <button onClick={doAction} disabled={loading || !amount} className="btn-primary da-submit">
            {loading ? <span className="spinner" /> : actionTab === "deposit" ? "Deposit" : actionTab === "withdraw" ? "Withdraw" : "Transfer"}
          </button>
        </div>
        {actionTab === "transfer" && <div className="da-hint">Auto-generated idempotency key - safe to retry</div>}
      </div>

      {/* Ledger timeline */}
      <div className="detail-ledger">
        <h2 className="dl-title">Journal Entries</h2>
        {entries.length > 0 ? (
          <>
            <div className="timeline">
              {entries.map((e, i) => (
                <div key={`${e.reference}-${i}`} className="tl-item" style={{ animationDelay: `${i * 30}ms` }}>
                  <div className="tl-dot-wrap">
                    <div className={`tl-dot ${e.direction === "CREDIT" ? "credit" : "debit"}`} />
                    {i < entries.length - 1 && <div className="tl-line" />}
                  </div>
                  <div className="tl-content">
                    <div className="tl-top">
                      <span className={`tl-badge ${e.direction === "CREDIT" ? "credit" : "debit"}`}>
                        {e.direction === "CREDIT" ? "↑ Credit" : "↓ Debit"}
                      </span>
                      <span className="tl-time">{timeAgo(e.postedAt)}</span>
                    </div>
                    <div className="tl-amount">${money(e.amount)}</div>
                    <div className="tl-ref">{e.reference}</div>
                  </div>
                </div>
              ))}
            </div>
            <div className="dl-summary">
              {entries.filter(e => e.direction === "CREDIT").length} credits &middot;{" "}
              {entries.filter(e => e.direction === "DEBIT").length} debits &middot;{" "}
              Derived balance: <strong>${money(account.balance)}</strong>
            </div>
          </>
        ) : (
          <div className="empty-state">No journal entries yet. Make a deposit to see the ledger in action.</div>
        )}
      </div>
    </div>
  );
}
