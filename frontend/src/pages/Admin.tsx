import { useEffect, useState } from "react";
import { api } from "../api/client";
import type { AuditEntry } from "../api/client";
import type { Totals } from "../types";
import AnimatedMoney from "../components/AnimatedMoney";

export default function Admin() {
  const [audit, setAudit] = useState<AuditEntry[]>([]);
  const [totals, setTotals] = useState<Totals | null>(null);
  const [accountCount, setAccountCount] = useState(0);
  const [eventCount, setEventCount] = useState(0);
  const [consumedCount, setConsumedCount] = useState(0);

  useEffect(() => {
    const poll = () => {
      Promise.all([
        api.getAuditLog(),
        api.getTotals(),
        api.listAccounts(),
        api.getOutboxEvents(),
        api.getConsumedEvents(),
      ]).then(([a, t, accs, evts, cons]) => {
        setAudit(a);
        setTotals(t);
        setAccountCount(accs.length);
        setEventCount(evts.length);
        setConsumedCount(cons.length);
      }).catch(() => {});
    };
    poll();
    const id = setInterval(poll, 5000);
    return () => clearInterval(id);
  }, []);

  const journalCount = audit.length;
  const deposits = audit.filter(a => a.type === "DEPOSIT").length;
  const withdrawals = audit.filter(a => a.type === "WITHDRAWAL").length;
  const transfers = audit.filter(a => a.type === "TRANSFER").length;
  const reversals = audit.filter(a => a.type === "REVERSAL").length;

  return (
    <div className="page fade-in">
      <div className="page-header">
        <div>
          <h1 className="page-title">Admin Dashboard</h1>
          <p className="page-sub">System health, audit trail, operational metrics</p>
        </div>
      </div>

      {/* System health */}
      <div className="admin-health">
        <div className="admin-health-title">System Health</div>
        <div className="stat-row-6">
          <div className="stat-box">
            <div className={`stat-num ${totals?.balanced ? "ok" : "warn"}`}>
              {totals?.balanced ? "OK" : "BREAK"}
            </div>
            <div className="stat-lbl">Invariant</div>
          </div>
          <div className="stat-box">
            <div className="stat-num">{accountCount}</div>
            <div className="stat-lbl">Accounts</div>
          </div>
          <div className="stat-box">
            <div className="stat-num">{journalCount}</div>
            <div className="stat-lbl">Journals</div>
          </div>
          <div className="stat-box">
            <div className="stat-num">{eventCount}</div>
            <div className="stat-lbl">Events</div>
          </div>
          <div className="stat-box">
            <div className="stat-num brand">{consumedCount}</div>
            <div className="stat-lbl">Consumed</div>
          </div>
          <div className="stat-box">
            <div className={`stat-num ${eventCount > 0 && consumedCount === eventCount ? "ok" : eventCount > 0 ? "warn" : ""}`}>
              {eventCount > 0 ? Math.round((consumedCount / eventCount) * 100) : 0}%
            </div>
            <div className="stat-lbl">Delivery</div>
          </div>
        </div>
      </div>

      {/* Balance invariant */}
      {totals && (
        <div className="admin-invariant">
          <div className="admin-inv-row">
            <div className="admin-inv-side">
              <div className="admin-inv-label">Assets (Cash Pool)</div>
              <div className="admin-inv-val"><AnimatedMoney value={totals.cashPool} /></div>
            </div>
            <div className="admin-inv-eq">=</div>
            <div className="admin-inv-side">
              <div className="admin-inv-label">Liabilities (Wallets)</div>
              <div className="admin-inv-val"><AnimatedMoney value={totals.totalLiabilities} /></div>
            </div>
            <span className={`inv-badge ${totals.balanced ? "" : "unbalanced"}`}>
              {totals.balanced ? "balanced" : "UNBALANCED"}
            </span>
          </div>
        </div>
      )}

      {/* Transaction breakdown */}
      <div className="admin-breakdown">
        <div className="admin-breakdown-title">Transaction Breakdown (last 50)</div>
        <div className="admin-breakdown-grid">
          <div className="admin-break-item">
            <div className="admin-break-num dep">{deposits}</div>
            <div className="admin-break-label">Deposits</div>
          </div>
          <div className="admin-break-item">
            <div className="admin-break-num wd">{withdrawals}</div>
            <div className="admin-break-label">Withdrawals</div>
          </div>
          <div className="admin-break-item">
            <div className="admin-break-num txfr">{transfers}</div>
            <div className="admin-break-label">Transfers</div>
          </div>
          <div className="admin-break-item">
            <div className="admin-break-num rev">{reversals}</div>
            <div className="admin-break-label">Reversals</div>
          </div>
        </div>
      </div>

      {/* Audit log */}
      <div className="admin-audit">
        <div className="admin-audit-header">
          <h2 className="admin-audit-title">Audit Trail</h2>
          <div className="admin-audit-meta">Last 50 journals - who did what</div>
        </div>
        {audit.length > 0 ? (
          <>
            <div className="audit-table-header">
              <span>Reference</span>
              <span>Type</span>
              <span>Description</span>
              <span>Performed By</span>
            </div>
            <div className="audit-list">
              {audit.map((a, i) => (
                <div key={a.reference} className="audit-row" style={{ animationDelay: `${i * 20}ms` }}>
                  <div className="audit-ref">{a.reference}</div>
                  <div className={`audit-type ${a.type.toLowerCase()}`}>{a.type}</div>
                  <div className="audit-desc">{a.description}</div>
                  <div className="audit-actor">
                    {a.performedBy
                      ? <span className="audit-actor-name">{a.performedBy}</span>
                      : <span className="audit-actor-system">system</span>
                    }
                  </div>
                </div>
              ))}
            </div>
          </>
        ) : (
          <div className="empty-state">No journal entries yet.</div>
        )}
      </div>
    </div>
  );
}
