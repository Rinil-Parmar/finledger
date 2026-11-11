import { useEffect, useRef, useState } from "react";
import { api } from "./api/client";
import type { Account, Totals } from "./types";

function money(n: number): string {
  return n.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function AnimatedMoney({ value, prefix = "$" }: { value: number; prefix?: string }) {
  const [display, setDisplay] = useState(value);
  const prev = useRef(value);

  useEffect(() => {
    if (prev.current === value) return;
    const from = prev.current;
    const to = value;
    prev.current = to;
    const duration = 500;
    const start = performance.now();
    let raf: number;
    function tick(now: number) {
      const t = Math.min((now - start) / duration, 1);
      const ease = 1 - Math.pow(1 - t, 3);
      setDisplay(from + (to - from) * ease);
      if (t < 1) raf = requestAnimationFrame(tick);
    }
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [value]);

  return <>{prefix}{money(display)}</>;
}

type ActionTab = "deposit" | "withdraw" | "transfer";

export default function App() {
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [totals, setTotals] = useState<Totals | null>(null);
  const [ownerName, setOwnerName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [selected, setSelected] = useState<string | null>(null);
  const [actionTab, setActionTab] = useState<ActionTab>("deposit");
  const [amount, setAmount] = useState("");
  const [destAccount, setDestAccount] = useState("");
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [flashRows, setFlashRows] = useState<Set<string>>(new Set());
  const [invariantPulse, setInvariantPulse] = useState(false);

  async function refresh() {
    setError(null);
    try {
      const [accs, tot] = await Promise.all([api.listAccounts(), api.getTotals()]);
      setAccounts(accs);
      setTotals(tot);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }

  useEffect(() => { refresh(); }, []);

  function flashAccount(...ids: string[]) {
    setFlashRows(new Set(ids));
    setTimeout(() => setFlashRows(new Set()), 800);
  }

  function pulseInvariant() {
    setInvariantPulse(true);
    setTimeout(() => setInvariantPulse(false), 600);
  }

  function showSuccess(msg: string) {
    setSuccessMsg(msg);
    setTimeout(() => setSuccessMsg(null), 3000);
  }

  async function create() {
    if (!ownerName.trim()) return;
    setLoading(true);
    setError(null);
    try {
      const res = await api.createAccount(ownerName.trim());
      setOwnerName("");
      await refresh();
      flashAccount(res.externalId);
      showSuccess(`Account created for ${ownerName.trim()}`);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }

  async function doAction() {
    if (!selected || !amount || Number(amount) <= 0) return;
    setLoading(true);
    setError(null);
    try {
      const amt = Number(amount);
      if (actionTab === "deposit") {
        await api.deposit(selected, amt);
        await refresh();
        flashAccount(selected);
        pulseInvariant();
        showSuccess(`Deposited $${money(amt)}`);
      } else if (actionTab === "withdraw") {
        await api.withdraw(selected, amt);
        await refresh();
        flashAccount(selected);
        pulseInvariant();
        showSuccess(`Withdrew $${money(amt)}`);
      } else {
        if (!destAccount) { setError("Select a destination account"); return; }
        await api.transfer(selected, destAccount, amt);
        await refresh();
        flashAccount(selected, destAccount);
        pulseInvariant();
        showSuccess(`Transferred $${money(amt)}`);
      }
      setAmount("");
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }

  const selectedAccount = accounts.find((a) => a.externalId === selected);
  const otherAccounts = accounts.filter((a) => a.externalId !== selected);

  return (
    <div className="app">
      <header className="header">
        <h1>FinLedger</h1>
        <span className="subtitle">double-entry Book of Record</span>
      </header>

      {/* Success toast */}
      {successMsg && (
        <div className="toast toast-enter">
          <span className="toast-dot" />
          {successMsg}
        </div>
      )}

      {/* Invariant bar */}
      {totals && (
        <div className={`invariant ${totals.balanced ? "ok" : "bad"} ${invariantPulse ? "pulse" : ""}`}>
          <div className="invariant-side">
            <div className="invariant-label">CASH pool (asset)</div>
            <div className="invariant-value">
              <AnimatedMoney value={totals.cashPool} />
            </div>
          </div>
          <div className="invariant-eq">{totals.balanced ? "=" : "≠"}</div>
          <div className="invariant-side">
            <div className="invariant-label">&Sigma; customer wallets (liabilities)</div>
            <div className="invariant-value">
              <AnimatedMoney value={totals.totalLiabilities} />
            </div>
          </div>
          <div className="invariant-badge">{totals.balanced ? "balanced ✓" : "OUT OF BALANCE"}</div>
        </div>
      )}

      {/* Actions panel — visible when an account is selected */}
      {selectedAccount && (
        <section className="panel action-panel slide-in">
          <div className="panel-head">
            <h2>
              Actions &mdash; <span className="mono">{selectedAccount.ownerName}</span>
              <span className="action-balance">
                $<AnimatedMoney value={selectedAccount.balance} prefix="" /> {selectedAccount.currency}
              </span>
            </h2>
            <button className="ghost" onClick={() => { setSelected(null); setError(null); }}>Close</button>
          </div>

          <div className="tab-row">
            {(["deposit", "withdraw", "transfer"] as ActionTab[]).map((t) => (
              <button
                key={t}
                className={`tab-btn ${actionTab === t ? "active" : ""}`}
                onClick={() => { setActionTab(t); setError(null); }}
              >
                {t === "deposit" ? "Deposit" : t === "withdraw" ? "Withdraw" : "Transfer"}
              </button>
            ))}
          </div>

          <div className="action-form">
            <div className="action-row">
              <span className="action-prefix">$</span>
              <input
                type="number"
                min="0.01"
                step="0.01"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                placeholder="0.00"
                className="amount-input"
                onKeyDown={(e) => e.key === "Enter" && doAction()}
              />
            </div>

            {actionTab === "transfer" && (
              <select
                className="dest-select"
                value={destAccount}
                onChange={(e) => setDestAccount(e.target.value)}
              >
                <option value="">Select destination...</option>
                {otherAccounts.map((a) => (
                  <option key={a.externalId} value={a.externalId}>
                    {a.ownerName} — ${money(a.balance)}
                  </option>
                ))}
              </select>
            )}

            <button onClick={doAction} disabled={loading || !amount} className="action-go">
              {loading ? (
                <span className="spinner" />
              ) : (
                actionTab === "deposit" ? "Deposit" :
                actionTab === "withdraw" ? "Withdraw" : "Transfer"
              )}
            </button>
          </div>

          {actionTab === "transfer" && (
            <div className="action-hint">
              Auto idempotency key — safe to retry
            </div>
          )}
        </section>
      )}

      {/* Error */}
      {error && <div className="error">{error}</div>}

      {/* Accounts panel */}
      <section className="panel">
        <div className="panel-head">
          <h2>Accounts</h2>
          <button onClick={refresh} className="ghost">Refresh</button>
        </div>

        <div className="create-row">
          <input
            value={ownerName}
            onChange={(e) => setOwnerName(e.target.value)}
            placeholder="Owner name (e.g. Rinil)"
            onKeyDown={(e) => e.key === "Enter" && create()}
          />
          <button onClick={create} disabled={loading}>Create account</button>
        </div>

        {accounts.length === 0 ? (
          <div className="empty">No accounts yet &mdash; create one above.</div>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Account</th>
                <th>Owner</th>
                <th className="num">Balance</th>
              </tr>
            </thead>
            <tbody>
              {accounts.map((a) => (
                <tr
                  key={a.externalId}
                  className={
                    (selected === a.externalId ? "row-selected " : "") +
                    (flashRows.has(a.externalId) ? "row-flash" : "")
                  }
                  onClick={() => {
                    setSelected(selected === a.externalId ? null : a.externalId);
                    setError(null);
                    setAmount("");
                    setDestAccount("");
                  }}
                >
                  <td className="mono">{a.externalId}</td>
                  <td>{a.ownerName}</td>
                  <td className="num">
                    $<AnimatedMoney value={a.balance} prefix="" /> {a.currency}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      <footer className="footer">FE-1 &middot; select an account to deposit, withdraw, or transfer</footer>
    </div>
  );
}
