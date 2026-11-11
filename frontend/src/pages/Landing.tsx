import AnimatedMoney from "../components/AnimatedMoney";
import type { Totals } from "../types";
import type { Page } from "../App";

interface LandingProps {
  totals: Totals | null;
  authEmail: string | null;
  goToApp: () => void;
  onLogout: () => void;
  setPage: (p: Page) => void;
}

export default function Landing({ totals, authEmail, goToApp, onLogout, setPage }: LandingProps) {
  function goPipeline() {
    authEmail ? setPage("pipeline") : setPage("login");
  }

  return (
    <div className="landing">
      {/* Nav */}
      <nav className="l-nav">
        <div className="l-logo">FinLedger</div>
        <div className="l-links">
          <button className="l-link" onClick={goToApp}>Dashboard</button>
          <button className="l-link" onClick={goPipeline}>Pipeline</button>
          {authEmail
            ? <button className="l-cta" onClick={onLogout}>{authEmail.split("@")[0]}</button>
            : <button className="l-cta" onClick={() => setPage("login")}>Sign In</button>
          }
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
            idempotent transfers, transactional outbox, and reconciliation -
            all running on real infrastructure.
          </p>
          <div className="l-actions">
            <button className="l-primary" onClick={goToApp}>
              Explore Dashboard
            </button>
            <button className="l-secondary" onClick={goPipeline}>
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
            <p>No row is ever updated or deleted. Corrections happen through compensating reversals - a full audit trail forever.</p>
            <div className="l-card-eq">
              <div className="eq-line"><span className="eq-label">Rule</span><code>{"INSERT only - no UPDATE, no DELETE"}</code></div>
              <div className="eq-line"><span className="eq-label">Reversal</span><code>{"reverse(tx) → new compensating entry"}</code></div>
            </div>
          </div>
          <div className="l-card">
            <div className="l-card-icon">
              <svg width="24" height="24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><polyline points="17 1 21 5 17 9"/><path d="M3 11V9a4 4 0 014-4h14"/><polyline points="7 23 3 19 7 15"/><path d="M21 13v2a4 4 0 01-4 4H3"/></svg>
            </div>
            <h3>Idempotent Transfers</h3>
            <p>Every transfer carries a unique key. Retry the same request 100 times - money moves exactly once. No double-spend.</p>
            <div className="l-card-eq">
              <div className="eq-line"><span className="eq-label">Property</span><code>{"f(x) = f(f(x))  - exactly-once"}</code></div>
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
            <p>Pessimistic locking with ordered acquisition prevents deadlocks. 20 concurrent transfers - zero overdrafts.</p>
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
          Every fintech - Wealthsimple, Groww, Stripe, your bank - runs a double-entry ledger
          underneath. Here's what really happens when you move money.
        </p>

        <div className="l-examples">
          {/* Example 1: Deposit */}
          <div className="l-example">
            <div className="l-ex-header">
              <span className="l-ex-icon dep">$</span>
              <div>
                <div className="l-ex-title">Aditi deposits $500 on Wealthsimple</div>
                <div className="l-ex-sub">Cash enters the system - two entries are created atomically</div>
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
                <div className="l-ex-sub">Money moves between wallets - cash pool unchanged, four entries total</div>
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
                <div className="l-ex-sub">Same pattern - cash moves out, securities move in</div>
              </div>
            </div>
            <div className="l-ex-ledger">
              <div className="l-ex-row">
                <span className="l-ex-account">Securities Holding</span>
                <span className="l-ex-dir debit">DEBIT</span>
                <span className="l-ex-amt">$1,500.00</span>
                <span className="l-ex-meaning">10 shares @ $150 acquired</span>
              </div>
              <div className="l-ex-row">
                <span className="l-ex-account">Cash Account</span>
                <span className="l-ex-dir credit">CREDIT</span>
                <span className="l-ex-amt">$1,500.00</span>
                <span className="l-ex-meaning">Cash leaves the account</span>
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
          creates real double-entry journal entries - the same way production fintech systems do.
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
}
