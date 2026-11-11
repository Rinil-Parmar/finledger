import { useEffect, useRef, useState, useCallback } from "react";
import { api } from "./api/client";
import type { Account, Totals, LedgerEntry, OutboxEvent } from "./types";

/* ── helpers ── */

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
    const dur = 600, start = performance.now();
    let raf: number;
    const tick = (now: number) => {
      const t = Math.min((now - start) / dur, 1);
      setDisplay(from + (value - from) * (1 - Math.pow(1 - t, 3)));
      if (t < 1) raf = requestAnimationFrame(tick);
    };
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

type Page = "landing" | "accounts" | "account-detail" | "pipeline";
type ActionTab = "deposit" | "withdraw" | "transfer";

export default function App() {
  const [page, setPage] = useState<Page>("landing");
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [totals, setTotals] = useState<Totals | null>(null);
  const [selected, setSelected] = useState<string | null>(null);
  const [ownerName, setOwnerName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [actionTab, setActionTab] = useState<ActionTab>("deposit");
  const [amount, setAmount] = useState("");
  const [destAccount, setDestAccount] = useState("");
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [invariantPulse, setInvariantPulse] = useState(false);
  const [entries, setEntries] = useState<LedgerEntry[]>([]);
  const [outboxEvents, setOutboxEvents] = useState<OutboxEvent[]>([]);
  const [consumedEvents, setConsumedEvents] = useState<string[]>([]);

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

  useEffect(() => {
    if (page !== "pipeline") return;
    const poll = () => {
      Promise.all([api.getOutboxEvents(), api.getConsumedEvents()])
        .then(([ev, con]) => { setOutboxEvents(ev); setConsumedEvents(con); })
        .catch(() => {});
    };
    poll();
    const id = setInterval(poll, 2000);
    return () => clearInterval(id);
  }, [page]);

  function showSuccess(msg: string) { setSuccessMsg(msg); setTimeout(() => setSuccessMsg(null), 3000); }
  function pulseInvariant() { setInvariantPulse(true); setTimeout(() => setInvariantPulse(false), 600); }

  function openAccount(id: string) {
    setSelected(id); setActionTab("deposit"); setAmount(""); setDestAccount(""); setError(null);
    setPage("account-detail");
  }

  async function create() {
    if (!ownerName.trim()) return;
    setLoading(true); setError(null);
    try {
      await api.createAccount(ownerName.trim());
      setOwnerName("");
      await refresh();
      showSuccess(`Account created`);
    } catch (e) { setError(e instanceof Error ? e.message : String(e)); }
    finally { setLoading(false); }
  }

  async function doAction() {
    if (!selected || !amount || Number(amount) <= 0) return;
    setLoading(true); setError(null);
    try {
      const amt = Number(amount);
      if (actionTab === "deposit") { await api.deposit(selected, amt); showSuccess(`Deposited $${money(amt)}`); }
      else if (actionTab === "withdraw") { await api.withdraw(selected, amt); showSuccess(`Withdrew $${money(amt)}`); }
      else {
        if (!destAccount) { setError("Select a destination account"); setLoading(false); return; }
        await api.transfer(selected, destAccount, amt); showSuccess(`Transferred $${money(amt)}`);
      }
      await refresh(); pulseInvariant(); setAmount("");
    } catch (e) { setError(e instanceof Error ? e.message : String(e)); }
    finally { setLoading(false); }
  }

  const sel = accounts.find(a => a.externalId === selected);
  const others = accounts.filter(a => a.externalId !== selected);
  const consumedSet = new Set(consumedEvents);

  /* ═══════════════════════════════════════════
     LANDING PAGE
     ═══════════════════════════════════════════ */
  if (page === "landing") return (
    <div className="landing">
      {/* Nav */}
      <nav className="l-nav">
        <div className="l-logo">FinLedger</div>
        <div className="l-links">
          <button className="l-link" onClick={() => { setPage("accounts"); refresh(); }}>Dashboard</button>
          <button className="l-link" onClick={() => setPage("pipeline")}>Pipeline</button>
          <button className="l-cta" onClick={() => { setPage("accounts"); refresh(); }}>Open App</button>
        </div>
      </nav>

      {/* Hero */}
      <section className="l-hero">
        <div className="l-glow" />
        <div className="l-hero-content">
          <div className="l-eyebrow">Production-grade financial infrastructure</div>
          <h1 className="l-title">
            The Book of Record<br />
            <span className="l-gradient">every dollar accounted for.</span>
          </h1>
          <p className="l-desc">
            A double-entry ledger system built with the same patterns used at
            Wealthsimple, Stripe, and every serious fintech. Append-only journals,
            idempotent transfers, transactional outbox, and reconciliation &mdash;
            all running on real infrastructure.
          </p>
          <div className="l-actions">
            <button className="l-primary" onClick={() => { setPage("accounts"); refresh(); }}>
              Explore Dashboard
            </button>
            <button className="l-secondary" onClick={() => setPage("pipeline")}>
              View Pipeline
            </button>
          </div>
        </div>
      </section>

      {/* Live invariant proof */}
      {totals && (
        <section className="l-proof">
          <div className="l-proof-label">Live system invariant</div>
          <div className="l-proof-bar">
            <div className="l-proof-side">
              <span className="l-proof-tag">ASSET</span>
              <span className="l-proof-val"><AnimatedMoney value={totals.cashPool} /></span>
            </div>
            <span className="l-proof-eq">=</span>
            <div className="l-proof-side">
              <span className="l-proof-tag">LIABILITIES</span>
              <span className="l-proof-val"><AnimatedMoney value={totals.totalLiabilities} /></span>
            </div>
            <span className="l-proof-badge">balanced</span>
          </div>
        </section>
      )}

      {/* Features */}
      <section className="l-features">
        <div className="l-section-label">Core Guarantees</div>
        <div className="l-grid">
          <div className="l-card">
            <div className="l-card-icon">
              <svg width="24" height="24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><rect x="3" y="3" width="18" height="18" rx="3"/><line x1="3" y1="9" x2="21" y2="9"/><line x1="9" y1="21" x2="9" y2="9"/></svg>
            </div>
            <h3>Double-Entry Ledger</h3>
            <p>Every transaction creates balanced journal entries. Debits always equal credits. Balance is derived, never stored.</p>
            <div className="l-card-eq">
              <div className="eq-line"><span className="eq-label">Invariant</span><code>{"Σ debits = Σ credits  ∀ journal"}</code></div>
              <div className="eq-line"><span className="eq-label">Balance</span><code>{"balance(a) = Σ credits(a) − Σ debits(a)"}</code></div>
            </div>
          </div>
          <div className="l-card">
            <div className="l-card-icon">
              <svg width="24" height="24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
            </div>
            <h3>Append-Only Immutability</h3>
            <p>No row is ever updated or deleted. Corrections happen through compensating reversals &mdash; a full audit trail forever.</p>
            <div className="l-card-eq">
              <div className="eq-line"><span className="eq-label">Rule</span><code>{"INSERT only — no UPDATE, no DELETE"}</code></div>
              <div className="eq-line"><span className="eq-label">Reversal</span><code>{"reverse(tx) → new compensating entry"}</code></div>
            </div>
          </div>
          <div className="l-card">
            <div className="l-card-icon">
              <svg width="24" height="24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><polyline points="17 1 21 5 17 9"/><path d="M3 11V9a4 4 0 014-4h14"/><polyline points="7 23 3 19 7 15"/><path d="M21 13v2a4 4 0 01-4 4H3"/></svg>
            </div>
            <h3>Idempotent Transfers</h3>
            <p>Every transfer carries a unique key. Retry the same request 100 times &mdash; money moves exactly once. No double-spend.</p>
            <div className="l-card-eq">
              <div className="eq-line"><span className="eq-label">Property</span><code>{"f(x) = f(f(x))  — exactly-once"}</code></div>
              <div className="eq-line"><span className="eq-label">SQL</span><code>{"INSERT … ON CONFLICT (key) DO NOTHING"}</code></div>
            </div>
          </div>
          <div className="l-card">
            <div className="l-card-icon">
              <svg width="24" height="24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><path d="M22 12h-4l-3 9L9 3l-3 9H2"/></svg>
            </div>
            <h3>Transactional Outbox</h3>
            <p>Events are written in the same database transaction as the business change, then published to Kafka. Zero lost writes.</p>
            <div className="l-card-eq">
              <div className="eq-line"><span className="eq-label">Guarantee</span><code>{"BEGIN → write(ledger) + write(outbox) → COMMIT"}</code></div>
              <div className="eq-line"><span className="eq-label">Delivery</span><code>{"at-least-once → idempotent consumer"}</code></div>
            </div>
          </div>
          <div className="l-card">
            <div className="l-card-icon">
              <svg width="24" height="24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><circle cx="12" cy="12" r="10"/><line x1="2" y1="12" x2="22" y2="12"/><path d="M12 2a15.3 15.3 0 014 10 15.3 15.3 0 01-4 10 15.3 15.3 0 01-4-10 15.3 15.3 0 014-10z"/></svg>
            </div>
            <h3>Reconciliation Engine</h3>
            <p>Compare internal ledger against external settlement feeds. Detects missing, mismatched, and directional breaks automatically.</p>
            <div className="l-card-eq">
              <div className="eq-line"><span className="eq-label">Match</span><code>{"internal ∩ external → {matched, break}"}</code></div>
              <div className="eq-line"><span className="eq-label">Breaks</span><code>{"missing | amount_mismatch | direction_mismatch"}</code></div>
            </div>
          </div>
          <div className="l-card">
            <div className="l-card-icon">
              <svg width="24" height="24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><rect x="2" y="3" width="20" height="14" rx="2"/><line x1="8" y1="21" x2="16" y2="21"/><line x1="12" y1="17" x2="12" y2="21"/></svg>
            </div>
            <h3>Concurrency Safe</h3>
            <p>Pessimistic locking with ordered acquisition prevents deadlocks. 20 concurrent transfers &mdash; zero overdrafts.</p>
            <div className="l-card-eq">
              <div className="eq-line"><span className="eq-label">Lock</span><code>{"SELECT … FOR UPDATE ORDER BY id"}</code></div>
              <div className="eq-line"><span className="eq-label">Safety</span><code>{"∀ concurrent tx: balance ≥ 0"}</code></div>
            </div>
          </div>
        </div>
      </section>

      {/* How Banking Works */}
      <section className="l-banking">
        <div className="l-section-label">How Banks &amp; Fintechs Actually Work</div>
        <p className="l-banking-intro">
          Every fintech &mdash; Wealthsimple, Groww, Stripe, your bank &mdash; runs a double-entry ledger
          underneath. Here's what really happens when you move money.
        </p>

        <div className="l-examples">
          {/* Example 1: Deposit */}
          <div className="l-example">
            <div className="l-ex-header">
              <span className="l-ex-icon dep">$</span>
              <div>
                <div className="l-ex-title">Aditi deposits $500 on Wealthsimple</div>
                <div className="l-ex-sub">Cash enters the system &mdash; two entries are created atomically</div>
              </div>
            </div>
            <div className="l-ex-ledger">
              <div className="l-ex-row">
                <span className="l-ex-account">Cash Pool</span>
                <span className="l-ex-dir debit">DEBIT</span>
                <span className="l-ex-amt">$500.00</span>
                <span className="l-ex-meaning">Bank's assets increase</span>
              </div>
              <div className="l-ex-row">
                <span className="l-ex-account">Aditi's Wallet</span>
                <span className="l-ex-dir credit">CREDIT</span>
                <span className="l-ex-amt">$500.00</span>
                <span className="l-ex-meaning">Bank now owes Aditi $500</span>
              </div>
              <div className="l-ex-invariant">
                <code>Assets ($500) = Liabilities ($500)</code>
                <span className="l-ex-check">&check; Balanced</span>
              </div>
            </div>
          </div>

          {/* Example 2: Transfer */}
          <div className="l-example">
            <div className="l-ex-header">
              <span className="l-ex-icon txfr">&harr;</span>
              <div>
                <div className="l-ex-title">Aditi sends $200 to Rinil via UPI / Interac</div>
                <div className="l-ex-sub">Money moves between wallets &mdash; cash pool unchanged, four entries total</div>
              </div>
            </div>
            <div className="l-ex-ledger">
              <div className="l-ex-row">
                <span className="l-ex-account">Aditi's Wallet</span>
                <span className="l-ex-dir debit">DEBIT</span>
                <span className="l-ex-amt">$200.00</span>
                <span className="l-ex-meaning">Aditi's liability decreases</span>
              </div>
              <div className="l-ex-row">
                <span className="l-ex-account">Rinil's Wallet</span>
                <span className="l-ex-dir credit">CREDIT</span>
                <span className="l-ex-amt">$200.00</span>
                <span className="l-ex-meaning">Rinil's liability increases</span>
              </div>
              <div className="l-ex-invariant">
                <code>Σ Wallets ($500) = Cash Pool ($500)</code>
                <span className="l-ex-check">&check; Still balanced</span>
              </div>
            </div>
          </div>

          {/* Example 3: Groww stock buy */}
          <div className="l-example">
            <div className="l-ex-header">
              <span className="l-ex-icon stock">&#9650;</span>
              <div>
                <div className="l-ex-title">Buying 10 shares on Groww / Wealthsimple Trade</div>
                <div className="l-ex-sub">Same pattern &mdash; cash moves out, securities move in</div>
              </div>
            </div>
            <div className="l-ex-ledger">
              <div className="l-ex-row">
                <span className="l-ex-account">Cash Account</span>
                <span className="l-ex-dir debit">DEBIT</span>
                <span className="l-ex-amt">$1,500.00</span>
                <span className="l-ex-meaning">Cash leaves the account</span>
              </div>
              <div className="l-ex-row">
                <span className="l-ex-account">Securities Holding</span>
                <span className="l-ex-dir credit">CREDIT</span>
                <span className="l-ex-amt">$1,500.00</span>
                <span className="l-ex-meaning">10 shares @ $150 acquired</span>
              </div>
              <div className="l-ex-invariant">
                <code>Cash + Securities = Total Holdings</code>
                <span className="l-ex-check">&check; Always balanced</span>
              </div>
            </div>
          </div>
        </div>

        <div className="l-banking-footer">
          <strong>FinLedger implements this exact pattern.</strong> Every operation you perform in the dashboard
          creates real double-entry journal entries &mdash; the same way production fintech systems do.
        </div>
      </section>

      {/* Architecture */}
      <section className="l-arch">
        <div className="l-section-label">Architecture</div>
        <div className="l-arch-flow">
          <div className="l-arch-node"><div className="l-arch-box">REST API</div><div className="l-arch-sub">Spring Boot</div></div>
          <div className="l-arch-arrow" />
          <div className="l-arch-node"><div className="l-arch-box">Ledger Service</div><div className="l-arch-sub">Double-entry core</div></div>
          <div className="l-arch-arrow" />
          <div className="l-arch-node"><div className="l-arch-box">PostgreSQL</div><div className="l-arch-sub">Flyway migrations</div></div>
          <div className="l-arch-arrow" />
          <div className="l-arch-node"><div className="l-arch-box">Outbox</div><div className="l-arch-sub">Same-tx writes</div></div>
          <div className="l-arch-arrow" />
          <div className="l-arch-node"><div className="l-arch-box">Kafka</div><div className="l-arch-sub">Event streaming</div></div>
        </div>
      </section>

      {/* Tech */}
      <section className="l-tech">
        <div className="l-tech-items">
          <span className="l-tech-item">Java 17</span>
          <span className="l-tech-dot" />
          <span className="l-tech-item">Spring Boot 3.4</span>
          <span className="l-tech-dot" />
          <span className="l-tech-item">PostgreSQL 16</span>
          <span className="l-tech-dot" />
          <span className="l-tech-item">Apache Kafka</span>
          <span className="l-tech-dot" />
          <span className="l-tech-item">Flyway</span>
          <span className="l-tech-dot" />
          <span className="l-tech-item">Testcontainers</span>
          <span className="l-tech-dot" />
          <span className="l-tech-item">React + TypeScript</span>
        </div>
      </section>

      <footer className="l-footer">
        Built by <strong>Rinil Parmar</strong> &middot; 28 integration tests &middot; 8 Flyway migrations &middot; 10 ADRs
      </footer>
    </div>
  );

  /* ═══════════════════════════════════════════
     APP SHELL (accounts, detail, pipeline)
     ═══════════════════════════════════════════ */
  return (
    <div className="shell">
      {/* Top bar */}
      <nav className="topbar">
        <button className="topbar-logo" onClick={() => setPage("landing")}>FinLedger</button>
        <div className="topbar-links">
          <button className={`topbar-link ${page === "accounts" || page === "account-detail" ? "active" : ""}`}
            onClick={() => setPage("accounts")}>Accounts</button>
          <button className={`topbar-link ${page === "pipeline" ? "active" : ""}`}
            onClick={() => setPage("pipeline")}>Pipeline</button>
        </div>
        {totals && (
          <div className={`topbar-inv ${invariantPulse ? "pulse" : ""}`}>
            <AnimatedMoney value={totals.cashPool} /> = <AnimatedMoney value={totals.totalLiabilities} />
            <span className="topbar-badge">{totals.balanced ? "balanced" : "UNBALANCED"}</span>
          </div>
        )}
      </nav>

      {/* Toast */}
      {successMsg && (
        <div className="toast toast-enter"><span className="toast-dot" />{successMsg}</div>
      )}

      {error && <div className="app-error">{error}</div>}

      <main className="main">

        {/* ─── ACCOUNTS PAGE ─── */}
        {page === "accounts" && (
          <div className="page fade-in">
            <div className="page-header">
              <div>
                <h1 className="page-title">Accounts</h1>
                <p className="page-sub">Manage customer wallets and the cash pool</p>
              </div>
              <button onClick={refresh} className="btn-outline">Refresh</button>
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
            <div className="create-section">
              <input value={ownerName} onChange={e => setOwnerName(e.target.value)}
                placeholder="New account owner name" className="create-input"
                onKeyDown={e => e.key === "Enter" && create()} />
              <button onClick={create} disabled={loading} className="btn-primary">Create Account</button>
            </div>

            {/* Account cards */}
            <div className="account-grid">
              {accounts.map((a, i) => (
                <div key={a.externalId} className="account-card" onClick={() => openAccount(a.externalId)}
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
        )}

        {/* ─── ACCOUNT DETAIL PAGE ─── */}
        {page === "account-detail" && sel && (
          <div className="page fade-in">
            <button className="back-btn" onClick={() => setPage("accounts")}>&larr; All Accounts</button>

            {/* Balance hero */}
            <div className="detail-hero">
              <div className="dh-avatar">{sel.ownerName.charAt(0).toUpperCase()}</div>
              <div className="dh-info">
                <h1 className="dh-name">{sel.ownerName}</h1>
                <div className="dh-id">{sel.externalId}</div>
              </div>
              <div className="dh-balance">
                <div className="dh-label">Balance</div>
                <div className="dh-amount">$<AnimatedMoney value={sel.balance} prefix="" /></div>
                <div className="dh-currency">{sel.currency}</div>
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
                    {others.map(a => <option key={a.externalId} value={a.externalId}>{a.ownerName} &mdash; ${money(a.balance)}</option>)}
                  </select>
                )}
                <button onClick={doAction} disabled={loading || !amount} className="btn-primary da-submit">
                  {loading ? <span className="spinner" /> : actionTab === "deposit" ? "Deposit" : actionTab === "withdraw" ? "Withdraw" : "Transfer"}
                </button>
              </div>
              {actionTab === "transfer" && <div className="da-hint">Auto-generated idempotency key &mdash; safe to retry</div>}
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
                    Derived balance: <strong>${money(sel.balance)}</strong>
                  </div>
                </>
              ) : (
                <div className="empty-state">No journal entries yet. Make a deposit to see the ledger in action.</div>
              )}
            </div>
          </div>
        )}

        {/* ─── PIPELINE PAGE ─── */}
        {page === "pipeline" && (
          <div className="page fade-in">
            <div className="page-header">
              <div>
                <h1 className="page-title">Event Pipeline <span className="live-indicator"><span className="live-ring" /><span className="live-core" /> Live</span></h1>
                <p className="page-sub">Transactional outbox pattern &rarr; Kafka &rarr; Idempotent consumer</p>
              </div>
            </div>

            {/* Flow */}
            <div className="pipe-flow">
              {[
                { label: "Business Transaction", sub: "Writes event atomically", icon: "Tx" },
                { label: "Outbox Table", sub: "PENDING in same DB tx", icon: "DB" },
                { label: "Publisher", sub: "Polls & sends to Kafka", icon: "Pub" },
                { label: "Kafka Topic", sub: "At-least-once delivery", icon: "K" },
                { label: "Consumer", sub: "Idempotent processing", icon: "Rx" },
              ].map((s, i) => (
                <div key={i} className="pipe-step-wrap">
                  {i > 0 && (
                    <div className="pipe-conn">
                      <div className="pipe-conn-line" />
                      <div className="pipe-conn-dot" style={{ animationDelay: `${i * 0.4}s` }} />
                    </div>
                  )}
                  <div className="pipe-step">
                    <div className="pipe-step-icon">{s.icon}</div>
                    <div className="pipe-step-label">{s.label}</div>
                    <div className="pipe-step-sub">{s.sub}</div>
                  </div>
                </div>
              ))}
            </div>

            {/* Stats */}
            <div className="stat-row">
              {[
                { val: outboxEvents.length, label: "Total Events", cls: "" },
                { val: outboxEvents.filter(e => e.status === "PUBLISHED").length, label: "Published", cls: "ok" },
                { val: outboxEvents.filter(e => e.status === "PENDING").length, label: "Pending", cls: "warn" },
                { val: consumedEvents.length, label: "Consumed", cls: "brand" },
              ].map((s, i) => (
                <div key={i} className="stat-box">
                  <div className={`stat-num ${s.cls}`}>{s.val}</div>
                  <div className="stat-lbl">{s.label}</div>
                </div>
              ))}
            </div>

            {/* Event table */}
            {outboxEvents.length > 0 ? (
              <div className="event-panel">
                <h2 className="ep-title">Recent Events</h2>
                <div className="event-list">
                  {outboxEvents.slice(0, 25).map((ev, i) => (
                    <div key={ev.id} className="ev-row" style={{ animationDelay: `${i * 30}ms` }}>
                      <div className="ev-type">{ev.eventType}</div>
                      <div className="ev-agg">{ev.aggregateId.slice(0, 16)}...</div>
                      <div className={`ev-status ${ev.status === "PUBLISHED" ? "published" : "pending"}`}>
                        <span className="ev-status-dot" />{ev.status}
                      </div>
                      <div className="ev-consumed">
                        {consumedSet.has(ev.id) ? <span className="ev-check">&check;</span> : <span className="ev-dash">&mdash;</span>}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            ) : (
              <div className="empty-state">No events yet. Perform operations on the Accounts page to see events flow through the pipeline.</div>
            )}
          </div>
        )}
      </main>
    </div>
  );
}
