import { useEffect, useState, useCallback } from "react";
import { api, setToken, getToken, clearToken } from "./api/client";
import { money } from "./utils/helpers";
import type { Account, Totals, LedgerEntry } from "./types";

import Topbar from "./components/Topbar";
import Toast from "./components/Toast";
import Landing from "./pages/Landing";
import Login from "./pages/Login";
import Accounts from "./pages/Accounts";
import AccountDetail from "./pages/AccountDetail";
import Pipeline from "./pages/Pipeline";
import Admin from "./pages/Admin";

export type Page = "landing" | "accounts" | "account-detail" | "pipeline" | "admin" | "login";

export default function App() {
  const [page, setPage] = useState<Page>("landing");
  const [authEmail, setAuthEmail] = useState<string | null>(null);
  const [authRole, setAuthRole] = useState<string | null>(null);

  const [accounts, setAccounts] = useState<Account[]>([]);
  const [totals, setTotals] = useState<Totals | null>(null);
  const [selected, setSelected] = useState<string | null>(null);
  const [entries, setEntries] = useState<LedgerEntry[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [invariantPulse, setInvariantPulse] = useState(false);

  useEffect(() => {
    const token = getToken();
    if (token) {
      try {
        const payload = JSON.parse(atob(token.split(".")[1]));
        if (payload.exp * 1000 > Date.now()) {
          setAuthEmail(payload.sub);
          setAuthRole(payload.role);
        } else {
          clearToken();
        }
      } catch { clearToken(); }
    }
  }, []);

  const refresh = useCallback(async () => {
    setError(null);
    try {
      const [accs, tot] = await Promise.all([api.listAccounts(), api.getTotals()]);
      setAccounts(accs);
      setTotals(tot);
    } catch (e) { setError(e instanceof Error ? e.message : String(e)); }
  }, []);

  useEffect(() => { refresh(); }, [refresh]);

  useEffect(() => {
    if (selected) api.getLedgerEntries(selected).then(setEntries).catch(() => {});
    else setEntries([]);
  }, [selected, totals]);

  function showSuccess(msg: string) { setSuccessMsg(msg); setTimeout(() => setSuccessMsg(null), 3000); }
  function pulseInvariant() { setInvariantPulse(true); setTimeout(() => setInvariantPulse(false), 600); }

  function handleLogout() {
    clearToken();
    setAuthEmail(null);
    setAuthRole(null);
    setPage("landing");
  }

  function goToApp() {
    if (authEmail) { setPage("accounts"); refresh(); }
    else { setPage("login"); }
  }

  function openAccount(id: string) {
    setSelected(id);
    setError(null);
    setPage("account-detail");
  }

  async function createAccount(name: string) {
    setLoading(true); setError(null);
    try {
      await api.createAccount(name);
      await refresh();
      showSuccess("Account created");
    } catch (e) { setError(e instanceof Error ? e.message : String(e)); }
    finally { setLoading(false); }
  }

  async function handleAccountAction(tab: string, amount: number, destAccount?: string) {
    if (!selected) return;
    setLoading(true); setError(null);
    try {
      if (tab === "deposit") { await api.deposit(selected, amount); showSuccess(`Deposited $${money(amount)}`); }
      else if (tab === "withdraw") { await api.withdraw(selected, amount); showSuccess(`Withdrew $${money(amount)}`); }
      else {
        if (!destAccount) { setError("Select a destination account"); setLoading(false); return; }
        await api.transfer(selected, destAccount, amount); showSuccess(`Transferred $${money(amount)}`);
      }
      await refresh(); pulseInvariant();
    } catch (e) { setError(e instanceof Error ? e.message : String(e)); }
    finally { setLoading(false); }
  }

  if (page === "login") return (
    <Login
      setPage={setPage}
      onAuth={(email, role) => { setAuthEmail(email); setAuthRole(role); }}
      onRefresh={refresh}
      showSuccess={showSuccess}
    />
  );

  if (page === "landing") return (
    <Landing
      totals={totals}
      authEmail={authEmail}
      goToApp={goToApp}
      onLogout={handleLogout}
      setPage={setPage}
    />
  );

  const sel = accounts.find(a => a.externalId === selected);
  const others = accounts.filter(a => a.externalId !== selected);

  return (
    <div className="shell">
      <Topbar
        page={page}
        setPage={setPage}
        totals={totals}
        invariantPulse={invariantPulse}
        authEmail={authEmail}
        authRole={authRole}
        onLogout={handleLogout}
      />

      {successMsg && <Toast message={successMsg} />}
      {error && <div className="app-error">{error}</div>}

      <main className="main">
        {page === "accounts" && (
          <Accounts
            accounts={accounts}
            totals={totals}
            invariantPulse={invariantPulse}
            onRefresh={refresh}
            onOpenAccount={openAccount}
            onCreate={createAccount}
            loading={loading}
          />
        )}

        {page === "account-detail" && sel && (
          <AccountDetail
            account={sel}
            entries={entries}
            others={others}
            setPage={setPage}
            onAction={handleAccountAction}
            loading={loading}
            error={error}
            setError={setError}
          />
        )}

        {page === "pipeline" && <Pipeline />}
        {page === "admin" && <Admin />}
      </main>
    </div>
  );
}
