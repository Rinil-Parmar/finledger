import { useEffect, useRef, useState } from "react";
import { api } from "./api/client";
import type { Account, Totals, LedgerEntry, OutboxEvent } from "./types";

function money(n: number): string {
  return n.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function AnimatedMoney({ value, prefix = "$" }: { value: number; prefix?: string }) {
  const [display, setDisplay] = useState(value);
  const prev = useRef(value);
  useEffect(() => {
    if (prev.current === value) return;
    const from = prev.current;
    prev.current = value;
    const duration = 600;
    const start = performance.now();
    let raf: number;
    function tick(now: number) {
      const t = Math.min((now - start) / duration, 1);
      const ease = 1 - Math.pow(1 - t, 3);
      setDisplay(from + (value - from) * ease);
      if (t < 1) raf = requestAnimationFrame(tick);
    }
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [value]);
  return <>{prefix}{money(display)}</>;
}

function timeAgo(iso: string): string {
  const s = Math.floor((Date.now() - new Date(iso).getTime()) / 1000);
  if (s < 60) return `${s}s ago`;
  if (s < 3600) return `${Math.floor(s / 60)}m ago`;
  return `${Math.floor(s / 3600)}h ago`;
}

type ActionTab = "deposit" | "withdraw" | "transfer";
type Section = "dashboard" | "ledger" | "pipeline";

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
  const [section, setSection] = useState<Section>("dashboard");

  const [entries, setEntries] = useState<LedgerEntry[]>([]);
  const [outboxEvents, setOutboxEvents] = useState<OutboxEvent[]>([]);
  const [consumedEvents, setConsumedEvents] = useState<string[]>([]);
  const [pipelinePolling, setPipelinePolling] = useState(false);

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

  useEffect(() => {
    if (selected) {
      api.getLedgerEntries(selected).then(setEntries).catch(() => {});
    } else {
      setEntries([]);
    }
  }, [selected, totals]);

  useEffect(() => {
    if (section !== "pipeline") { setPipelinePolling(false); return; }
    setPipelinePolling(true);
    function poll() {
      Promise.all([api.getOutboxEvents(), api.getConsumedEvents()])
        .then(([ev, con]) => { setOutboxEvents(ev); setConsumedEvents(con); })
        .catch(() => {});
    }
    poll();
    const id = setInterval(poll, 2000);
    return () => { clearInterval(id); setPipelinePolling(false); };
  }, [section]);

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
    setLoading(true); setError(null);
    try {
      const res = await api.createAccount(ownerName.trim());
      setOwnerName("");
      await refresh();
      flashAccount(res.externalId);
      showSuccess(`Account created for ${ownerName.trim()}`);
    } catch (e) { setError(e instanceof Error ? e.message : String(e)); }
    finally { setLoading(false); }
  }

  async function doAction() {
    if (!selected || !amount || Number(amount) <= 0) return;
    setLoading(true); setError(null);
    try {
      const amt = Number(amount);
      if (actionTab === "deposit") {
        await api.deposit(selected, amt);
        showSuccess(`Deposited $${money(amt)}`);
      } else if (actionTab === "withdraw") {
        await api.withdraw(selected, amt);
        showSuccess(`Withdrew $${money(amt)}`);
      } else {
        if (!destAccount) { setError("Select a destination account"); setLoading(false); return; }
        await api.transfer(selected, destAccount, amt);
        showSuccess(`Transferred $${money(amt)}`);
      }
      await refresh();
      flashAccount(selected, ...(actionTab === "transfer" && destAccount ? [destAccount] : []));
      pulseInvariant();
      setAmount("");
    } catch (e) { setError(e instanceof Error ? e.message : String(e)); }
    finally { setLoading(false); }
  }

  const selectedAccount = accounts.find((a) => a.externalId === selected);
  const otherAccounts = accounts.filter((a) => a.externalId !== selected);

  const consumedSet = new Set(consumedEvents);

  return (
    <div className="app">
      {/* ─── Hero ─── */}
      <header className="hero">
        <div className="hero-glow" />
        <h1 className="hero-title">FinLedger</h1>
        <p className="hero-sub">
          Production-grade double-entry Book of Record
        </p>
        <div className="hero-chips">
          <span className="chip">Append-only ledger</span>
          <span className="chip">Idempotent transfers</span>
          <span className="chip">Kafka outbox</span>
          <span className="chip">Reconciliation</span>
        </div>
      </header>

      {/* ─── Navigation ─── */}
      <nav className="nav">
        {(["dashboard", "ledger", "pipeline"] as Section[]).map((s) => (
          <button
            key={s}
            className={`nav-btn ${section === s ? "active" : ""}`}
            onClick={() => setSection(s)}
          >
            <span className="nav-icon">
              {s === "dashboard" ? "■" : s === "ledger" ? "≡" : "→"}
            </span>
            {s === "dashboard" ? "Dashboard" : s === "ledger" ? "Ledger" : "Pipeline"}
          </button>
        ))}
      </nav>

      {/* ─── Toast ─── */}
      {successMsg && (
        <div className="toast toast-enter">
          <span className="toast-dot" />
          {successMsg}
        </div>
      )}

      {/* ─── Invariant Bar (always visible) ─── */}
      {totals && (
        <div className={`invariant ${totals.balanced ? "ok" : "bad"} ${invariantPulse ? "pulse" : ""}`}>
          <div className="invariant-side">
            <div className="invariant-label">Cash Pool (Asset)</div>
            <div className="invariant-value"><AnimatedMoney value={totals.cashPool} /></div>
          </div>
          <div className="invariant-eq">{totals.balanced ? "=" : "≠"}</div>
          <div className="invariant-side">
            <div className="invariant-label">&Sigma; Customer Wallets (Liabilities)</div>
            <div className="invariant-value"><AnimatedMoney value={totals.totalLiabilities} /></div>
          </div>
          <div className="invariant-badge">{totals.balanced ? "balanced" : "OUT OF BALANCE"}</div>
        </div>
      )}

      {error && <div className="error">{error}</div>}

      {/* ═══════════ DASHBOARD ═══════════ */}
      {section === "dashboard" && (
        <div className="fade-in">
          {/* Actions panel */}
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
                  <button key={t} className={`tab-btn ${actionTab === t ? "active" : ""}`}
                    onClick={() => { setActionTab(t); setError(null); }}>
                    {t === "deposit" ? "Deposit" : t === "withdraw" ? "Withdraw" : "Transfer"}
                  </button>
                ))}
              </div>
              <div className="action-form">
                <div className="action-row">
                  <span className="action-prefix">$</span>
                  <input type="number" min="0.01" step="0.01" value={amount}
                    onChange={(e) => setAmount(e.target.value)} placeholder="0.00"
                    className="amount-input" onKeyDown={(e) => e.key === "Enter" && doAction()} />
                </div>
                {actionTab === "transfer" && (
                  <select className="dest-select" value={destAccount}
                    onChange={(e) => setDestAccount(e.target.value)}>
                    <option value="">Select destination...</option>
                    {otherAccounts.map((a) => (
                      <option key={a.externalId} value={a.externalId}>
                        {a.ownerName} — ${money(a.balance)}
                      </option>
                    ))}
                  </select>
                )}
                <button onClick={doAction} disabled={loading || !amount} className="action-go">
                  {loading ? <span className="spinner" /> :
                    actionTab === "deposit" ? "Deposit" :
                    actionTab === "withdraw" ? "Withdraw" : "Transfer"}
                </button>
              </div>
              {actionTab === "transfer" && (
                <div className="action-hint">Auto idempotency key — safe to retry</div>
              )}
            </section>
          )}

          {/* Accounts */}
          <section className="panel">
            <div className="panel-head">
              <h2>Accounts</h2>
              <button onClick={refresh} className="ghost">Refresh</button>
            </div>
            <div className="create-row">
              <input value={ownerName} onChange={(e) => setOwnerName(e.target.value)}
                placeholder="Owner name (e.g. Rinil)" onKeyDown={(e) => e.key === "Enter" && create()} />
              <button onClick={create} disabled={loading}>Create account</button>
            </div>
            {accounts.length === 0 ? (
              <div className="empty">No accounts yet &mdash; create one above.</div>
            ) : (
              <table className="table">
                <thead><tr><th>Account</th><th>Owner</th><th className="num">Balance</th></tr></thead>
                <tbody>
                  {accounts.map((a) => (
                    <tr key={a.externalId}
                      className={(selected === a.externalId ? "row-selected " : "") +
                        (flashRows.has(a.externalId) ? "row-flash" : "")}
                      onClick={() => { setSelected(selected === a.externalId ? null : a.externalId); setError(null); setAmount(""); setDestAccount(""); }}>
                      <td className="mono">{a.externalId}</td>
                      <td>{a.ownerName}</td>
                      <td className="num">$<AnimatedMoney value={a.balance} prefix="" /> {a.currency}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </section>
        </div>
      )}

      {/* ═══════════ LEDGER EXPLORER ═══════════ */}
      {section === "ledger" && (
        <div className="fade-in">
          <section className="panel">
            <div className="panel-head">
              <h2>Ledger Explorer</h2>
              <span className="panel-hint">Select an account to view journal entries</span>
            </div>

            {/* account selector chips */}
            <div className="chip-row">
              {accounts.map((a) => (
                <button key={a.externalId}
                  className={`chip-btn ${selected === a.externalId ? "active" : ""}`}
                  onClick={() => setSelected(selected === a.externalId ? null : a.externalId)}>
                  {a.ownerName}
                  <span className="chip-bal">${money(a.balance)}</span>
                </button>
              ))}
            </div>

            {selected && entries.length > 0 && (
              <div className="ledger-entries stagger-in">
                <div className="ledger-header">
                  <span>Journal entries for <strong>{selectedAccount?.ownerName}</strong></span>
                  <span className="mono" style={{ fontSize: 12 }}>{selected}</span>
                </div>
                <table className="table ledger-table">
                  <thead>
                    <tr>
                      <th>Reference</th>
                      <th>Direction</th>
                      <th className="num">Amount</th>
                      <th className="num">Posted</th>
                    </tr>
                  </thead>
                  <tbody>
                    {entries.map((e, i) => (
                      <tr key={`${e.reference}-${i}`} className="ledger-row">
                        <td className="mono">{e.reference}</td>
                        <td>
                          <span className={`dir-badge ${e.direction === "DEBIT" ? "debit" : "credit"}`}>
                            {e.direction === "DEBIT" ? "↓ DEBIT" : "↑ CREDIT"}
                          </span>
                        </td>
                        <td className="num">${money(e.amount)}</td>
                        <td className="num muted">{timeAgo(e.postedAt)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                <div className="ledger-summary">
                  <span>
                    {entries.filter(e => e.direction === "CREDIT").length} credits,{" "}
                    {entries.filter(e => e.direction === "DEBIT").length} debits
                  </span>
                  <span className="ledger-derived">
                    Derived balance: <strong>${money(selectedAccount?.balance ?? 0)}</strong>
                  </span>
                </div>
              </div>
            )}

            {selected && entries.length === 0 && (
              <div className="empty">No journal entries for this account yet.</div>
            )}
            {!selected && (
              <div className="empty">Select an account above to explore its ledger entries.</div>
            )}
          </section>

          {/* How it works */}
          <div className="explain-card fade-in">
            <h3>How double-entry works</h3>
            <div className="flow-visual">
              <div className="flow-node">
                <div className="flow-icon debit-icon">&darr;</div>
                <div className="flow-label">Debit</div>
                <div className="flow-desc">Money leaves an account</div>
              </div>
              <div className="flow-arrow">&harr;</div>
              <div className="flow-node">
                <div className="flow-icon credit-icon">&uarr;</div>
                <div className="flow-label">Credit</div>
                <div className="flow-desc">Money enters an account</div>
              </div>
              <div className="flow-arrow">=</div>
              <div className="flow-node">
                <div className="flow-icon balance-icon">&#x2713;</div>
                <div className="flow-label">Balanced</div>
                <div className="flow-desc">Every journal balances to zero</div>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ═══════════ EVENT PIPELINE ═══════════ */}
      {section === "pipeline" && (
        <div className="fade-in">
          <section className="panel">
            <div className="panel-head">
              <h2>
                Event Pipeline
                {pipelinePolling && <span className="live-dot" />}
              </h2>
              <span className="panel-hint">Transactional outbox &rarr; Kafka &rarr; Consumer</span>
            </div>

            {/* Pipeline flow diagram */}
            <div className="pipeline-flow">
              <div className="pipe-stage">
                <div className="pipe-icon">&#x1F4DD;</div>
                <div className="pipe-name">Business Tx</div>
                <div className="pipe-desc">Writes event in same transaction</div>
              </div>
              <div className="pipe-connector">
                <div className="pipe-line" />
                <div className="pipe-dot moving" />
              </div>
              <div className="pipe-stage">
                <div className="pipe-icon">&#x1F4E4;</div>
                <div className="pipe-name">Outbox</div>
                <div className="pipe-desc">PENDING &rarr; PUBLISHED</div>
              </div>
              <div className="pipe-connector">
                <div className="pipe-line" />
                <div className="pipe-dot moving delay" />
              </div>
              <div className="pipe-stage">
                <div className="pipe-icon">&#x1F4E8;</div>
                <div className="pipe-name">Kafka</div>
                <div className="pipe-desc">At-least-once delivery</div>
              </div>
              <div className="pipe-connector">
                <div className="pipe-line" />
                <div className="pipe-dot moving delay2" />
              </div>
              <div className="pipe-stage">
                <div className="pipe-icon">&#x2705;</div>
                <div className="pipe-name">Consumer</div>
                <div className="pipe-desc">Idempotent processing</div>
              </div>
            </div>

            {/* Stats */}
            <div className="pipeline-stats">
              <div className="stat-card">
                <div className="stat-value">{outboxEvents.length}</div>
                <div className="stat-label">Total Events</div>
              </div>
              <div className="stat-card">
                <div className="stat-value ok-text">
                  {outboxEvents.filter(e => e.status === "PUBLISHED").length}
                </div>
                <div className="stat-label">Published</div>
              </div>
              <div className="stat-card">
                <div className="stat-value warn-text">
                  {outboxEvents.filter(e => e.status === "PENDING").length}
                </div>
                <div className="stat-label">Pending</div>
              </div>
              <div className="stat-card">
                <div className="stat-value brand-text">{consumedEvents.length}</div>
                <div className="stat-label">Consumed</div>
              </div>
            </div>

            {/* Event list */}
            {outboxEvents.length > 0 && (
              <table className="table event-table">
                <thead>
                  <tr>
                    <th>Event Type</th>
                    <th>Aggregate</th>
                    <th>Status</th>
                    <th className="num">Consumed</th>
                  </tr>
                </thead>
                <tbody>
                  {outboxEvents.slice(0, 20).map((ev, i) => (
                    <tr key={ev.id} className="event-row" style={{ animationDelay: `${i * 40}ms` }}>
                      <td>
                        <span className="event-type">{ev.eventType}</span>
                      </td>
                      <td className="mono">{ev.aggregateId.slice(0, 12)}...</td>
                      <td>
                        <span className={`status-pill ${ev.status === "PUBLISHED" ? "published" : "pending"}`}>
                          <span className="status-dot" />
                          {ev.status}
                        </span>
                      </td>
                      <td className="num">
                        {consumedSet.has(ev.id) ? (
                          <span className="consumed-check">&check;</span>
                        ) : (
                          <span className="muted">&mdash;</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
            {outboxEvents.length === 0 && (
              <div className="empty">
                No events yet. Make a deposit, withdrawal or transfer to see events flow through the pipeline.
              </div>
            )}
          </section>
        </div>
      )}

      {/* ─── Feature Footer ─── */}
      <footer className="features">
        <div className="feature">
          <div className="feature-icon">&#x1F512;</div>
          <div className="feature-title">Append-only</div>
          <div className="feature-desc">Immutable journal entries. Corrections via compensating reversals.</div>
        </div>
        <div className="feature">
          <div className="feature-icon">&#x26A1;</div>
          <div className="feature-title">Idempotent</div>
          <div className="feature-desc">Every transfer carries a unique key. Safe to retry, impossible to double-spend.</div>
        </div>
        <div className="feature">
          <div className="feature-icon">&#x1F50D;</div>
          <div className="feature-title">Reconciliation</div>
          <div className="feature-desc">Match internal ledger against external settlement feeds. Four break types.</div>
        </div>
        <div className="feature">
          <div className="feature-icon">&#x1F4E1;</div>
          <div className="feature-title">Outbox + Kafka</div>
          <div className="feature-desc">Transactional outbox guarantees at-least-once event delivery. No lost writes.</div>
        </div>
      </footer>

      <div className="copyright">
        Built by Rinil Parmar &middot; Java 17 &middot; Spring Boot &middot; PostgreSQL &middot; Kafka
      </div>
    </div>
  );
}
