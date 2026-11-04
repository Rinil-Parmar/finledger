import { useEffect, useState } from "react";
import { api } from "./api/client";
import type { Account, Totals } from "./types";

function money(n: number): string {
  return n.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

export default function App() {
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [totals, setTotals] = useState<Totals | null>(null);
  const [ownerName, setOwnerName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

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

  useEffect(() => {
    refresh();
  }, []);

  async function create() {
    if (!ownerName.trim()) return;
    setLoading(true);
    setError(null);
    try {
      await api.createAccount(ownerName.trim());
      setOwnerName("");
      await refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="app">
      <header className="header">
        <h1>FinLedger</h1>
        <span className="subtitle">double-entry Book of Record</span>
      </header>

      {totals && (
        <div className={`invariant ${totals.balanced ? "ok" : "bad"}`}>
          <div className="invariant-side">
            <div className="invariant-label">CASH pool (asset)</div>
            <div className="invariant-value">${money(totals.cashPool)}</div>
          </div>
          <div className="invariant-eq">{totals.balanced ? "=" : "≠"}</div>
          <div className="invariant-side">
            <div className="invariant-label">&Sigma; customer wallets (liabilities)</div>
            <div className="invariant-value">${money(totals.totalLiabilities)}</div>
          </div>
          <div className="invariant-badge">{totals.balanced ? "balanced ✓" : "OUT OF BALANCE"}</div>
        </div>
      )}

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

        {error && <div className="error">{error}</div>}

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
                <tr key={a.externalId}>
                  <td className="mono">{a.externalId}</td>
                  <td>{a.ownerName}</td>
                  <td className="num">${money(a.balance)} {a.currency}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      <footer className="footer">FE-0 &middot; live data from the backend</footer>
    </div>
  );
}
