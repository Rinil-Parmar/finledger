import { useState } from "react";
import AnimatedMoney from "../components/AnimatedMoney";
import type { Account, Totals } from "../types";

interface AccountsProps {
  accounts: Account[];
  totals: Totals | null;
  invariantPulse: boolean;
  onRefresh: () => void;
  onOpenAccount: (id: string) => void;
  onCreate: (name: string) => Promise<void>;
  loading: boolean;
}

export default function Accounts({ accounts, totals, invariantPulse, onRefresh, onOpenAccount, onCreate, loading }: AccountsProps) {
  return (
    <div className="page fade-in">
      <div className="page-header">
        <div>
          <h1 className="page-title">Accounts</h1>
          <p className="page-sub">Manage customer wallets and the cash pool</p>
        </div>
        <button onClick={onRefresh} className="btn-outline">Refresh</button>
      </div>

      {/* Invariant card */}
      {totals && (
        <div className={`inv-card ${invariantPulse ? "pulse" : ""}`}>
          <div className="inv-left">
            <div className="inv-label">Cash Pool (Asset)</div>
            <div className="inv-big"><AnimatedMoney value={totals.cashPool} /></div>
          </div>
          <div className="inv-center">
            <div className="inv-eq">=</div>
          </div>
          <div className="inv-right">
            <div className="inv-label">&Sigma; Wallets (Liabilities)</div>
            <div className="inv-big"><AnimatedMoney value={totals.totalLiabilities} /></div>
          </div>
          <span className={`inv-badge ${totals.balanced ? "" : "unbalanced"}`}>
            {totals.balanced ? "balanced" : "UNBALANCED"}
          </span>
        </div>
      )}

      {/* Create */}
      <CreateAccount onCreate={onCreate} loading={loading} />

      {/* Account cards */}
      <div className="account-grid">
        {accounts.map((a, i) => (
          <div key={a.externalId} className="account-card" onClick={() => onOpenAccount(a.externalId)}
            style={{ animationDelay: `${i * 50}ms` }}>
            <div className="ac-top">
              <div className="ac-avatar">{a.ownerName.charAt(0).toUpperCase()}</div>
              <div className="ac-info">
                <div className="ac-name">{a.ownerName}</div>
                <div className="ac-id">{a.externalId}</div>
              </div>
            </div>
            <div className="ac-balance">
              $<AnimatedMoney value={a.balance} prefix="" />
              <span className="ac-currency">{a.currency}</span>
            </div>
            <div className="ac-footer">
              <span className={`ac-status ${a.status === "ACTIVE" ? "active" : ""}`}>{a.status}</span>
              <span className="ac-arrow">&rarr;</span>
            </div>
          </div>
        ))}
      </div>
      {accounts.length === 0 && <div className="empty-state">No accounts yet. Create one above to get started.</div>}
    </div>
  );
}

function CreateAccount({ onCreate, loading }: { onCreate: (name: string) => Promise<void>; loading: boolean }) {
  const [ownerName, setOwnerName] = useState("");

  async function create() {
    if (!ownerName.trim()) return;
    await onCreate(ownerName.trim());
    setOwnerName("");
  }

  return (
    <div className="create-section">
      <input value={ownerName} onChange={e => setOwnerName(e.target.value)}
        placeholder="New account owner name" className="create-input"
        onKeyDown={e => e.key === "Enter" && create()} />
      <button onClick={create} disabled={loading} className="btn-primary">Create Account</button>
    </div>
  );
}
