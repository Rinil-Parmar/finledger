import { useEffect, useState } from "react";
import { api } from "../api/client";
import type { OutboxEvent } from "../types";

export default function Pipeline() {
  const [outboxEvents, setOutboxEvents] = useState<OutboxEvent[]>([]);
  const [consumedEvents, setConsumedEvents] = useState<string[]>([]);

  useEffect(() => {
    const poll = () => {
      Promise.all([api.getOutboxEvents(), api.getConsumedEvents()])
        .then(([ev, con]) => { setOutboxEvents(ev); setConsumedEvents(con); })
        .catch(() => {});
    };
    poll();
    const id = setInterval(poll, 2000);
    return () => clearInterval(id);
  }, []);

  const published = outboxEvents.filter(e => e.status === "PUBLISHED").length;
  const pending = outboxEvents.filter(e => e.status === "PENDING").length;
  const total = outboxEvents.length;
  const consumed = consumedEvents.length;
  const successRate = total > 0 ? Math.round((published / total) * 100) : 0;
  const deliveryRate = published > 0 ? Math.round((consumed / published) * 100) : 0;
  const consumedSet = new Set(consumedEvents);

  return (
    <div className="page fade-in">
      <div className="page-header">
        <div>
          <h1 className="page-title">Event Pipeline <span className="live-indicator"><span className="live-ring" /><span className="live-core" /> Live</span></h1>
          <p className="page-sub">Transactional outbox pattern - Kafka - Idempotent consumer</p>
        </div>
      </div>

      {/* Why this matters */}
      <div className="pipe-why">
        <div className="pipe-why-title">Why the Outbox Pattern?</div>
        <div className="pipe-why-grid">
          <div className="pipe-why-card">
            <div className="pipe-why-icon bad">&#10007;</div>
            <div className="pipe-why-label">The naive approach</div>
            <div className="pipe-why-desc">Write to DB, then publish to Kafka. If Kafka is down or the app crashes between the two, the event is lost forever.</div>
            <code className="pipe-why-code">{"save(ledger); publish(kafka); // crash here = lost event"}</code>
          </div>
          <div className="pipe-why-card">
            <div className="pipe-why-icon ok">&#10003;</div>
            <div className="pipe-why-label">The outbox approach</div>
            <div className="pipe-why-desc">Write both the ledger entry and the event in a single database transaction. A background publisher picks up events and sends them to Kafka.</div>
            <code className="pipe-why-code">{"BEGIN; save(ledger); save(outbox); COMMIT; // atomic"}</code>
          </div>
        </div>
      </div>

      {/* Flow diagram - detailed */}
      <div className="pipe-flow-detailed">
        <div className="pipe-flow-title">Event Lifecycle</div>
        <div className="pipe-flow">
          {[
            { label: "API Request", sub: "POST /deposits, /transfers", icon: "API", detail: "REST endpoint receives the request" },
            { label: "DB Transaction", sub: "Single atomic commit", icon: "Tx", detail: "Ledger + outbox written together" },
            { label: "Outbox Table", sub: "Status: PENDING", icon: "DB", detail: "Event stored with business data" },
            { label: "Publisher", sub: "Polls every 500ms", icon: "Pub", detail: "Reads PENDING, sends to Kafka" },
            { label: "Kafka Topic", sub: "finledger.events", icon: "K", detail: "Durable, partitioned message log" },
            { label: "Consumer", sub: "Idempotent processing", icon: "Rx", detail: "Deduplicates by event ID" },
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
                <div className="pipe-step-detail">{s.detail}</div>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Stats - richer */}
      <div className="stat-row-6">
        <div className="stat-box">
          <div className="stat-num">{total}</div>
          <div className="stat-lbl">Total Events</div>
        </div>
        <div className="stat-box">
          <div className="stat-num ok">{published}</div>
          <div className="stat-lbl">Published</div>
        </div>
        <div className="stat-box">
          <div className="stat-num warn">{pending}</div>
          <div className="stat-lbl">Pending</div>
        </div>
        <div className="stat-box">
          <div className="stat-num brand">{consumed}</div>
          <div className="stat-lbl">Consumed</div>
        </div>
        <div className="stat-box">
          <div className={`stat-num ${successRate === 100 ? "ok" : successRate > 0 ? "warn" : ""}`}>{successRate}%</div>
          <div className="stat-lbl">Publish Rate</div>
        </div>
        <div className="stat-box">
          <div className={`stat-num ${deliveryRate === 100 ? "ok" : deliveryRate > 0 ? "warn" : ""}`}>{deliveryRate}%</div>
          <div className="stat-lbl">E2E Delivery</div>
        </div>
      </div>

      {/* Guarantees */}
      <div className="pipe-guarantees">
        <div className="pipe-g-title">Delivery Guarantees</div>
        <div className="pipe-g-grid">
          <div className="pipe-g-card">
            <div className="pipe-g-label">Producer</div>
            <div className="pipe-g-value">At-least-once</div>
            <div className="pipe-g-desc">Publisher retries until Kafka ACKs. Events may be sent more than once but never lost.</div>
            <code className="pipe-g-code">{"outbox.status = PENDING → poll → send → ACK → PUBLISHED"}</code>
          </div>
          <div className="pipe-g-card">
            <div className="pipe-g-label">Consumer</div>
            <div className="pipe-g-value">Exactly-once (effective)</div>
            <div className="pipe-g-desc">Consumer tracks processed event IDs. Duplicate deliveries are detected and skipped.</div>
            <code className="pipe-g-code">{"if (processed.contains(eventId)) skip(); else process();"}</code>
          </div>
          <div className="pipe-g-card">
            <div className="pipe-g-label">Ordering</div>
            <div className="pipe-g-value">Per-aggregate</div>
            <div className="pipe-g-desc">Events for the same account are ordered. Events across accounts may arrive out of order.</div>
            <code className="pipe-g-code">{"partition_key = aggregate_id → ordered within partition"}</code>
          </div>
        </div>
      </div>

      {/* Event table - detailed */}
      {outboxEvents.length > 0 ? (
        <div className="event-panel">
          <div className="ep-header">
            <h2 className="ep-title">Event Log</h2>
            <div className="ep-meta">{total} events - polling every 2s</div>
          </div>
          <div className="ev-table-header">
            <span>Event Type</span>
            <span>Aggregate</span>
            <span>Outbox Status</span>
            <span>Attempts</span>
            <span>Consumed</span>
          </div>
          <div className="event-list">
            {outboxEvents.slice(0, 25).map((ev, i) => (
              <div key={ev.id} className="ev-row" style={{ animationDelay: `${i * 30}ms` }}>
                <div className="ev-type">{ev.eventType}</div>
                <div className="ev-agg">{ev.aggregateId.slice(0, 20)}...</div>
                <div className={`ev-status ${ev.status === "PUBLISHED" ? "published" : "pending"}`}>
                  <span className="ev-status-dot" />{ev.status}
                </div>
                <div className="ev-attempts">
                  <span className={`ev-attempt-badge ${ev.attempts > 1 ? "retried" : ""}`}>{ev.attempts}</span>
                </div>
                <div className="ev-consumed">
                  {consumedSet.has(ev.id)
                    ? <span className="ev-consumed-yes"><span className="ev-check">&check;</span> Delivered</span>
                    : <span className="ev-consumed-no">Pending</span>
                  }
                </div>
              </div>
            ))}
          </div>
        </div>
      ) : (
        <div className="empty-state">
          <div className="empty-state-icon">
            <svg width="48" height="48" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round"><path d="M22 12h-4l-3 9L9 3l-3 9H2" transform="translate(12, 12)"/></svg>
          </div>
          <div className="empty-state-title">No events in the pipeline</div>
          <div className="empty-state-sub">Perform deposits, withdrawals, or transfers on the Accounts page to generate events and watch them flow through the outbox to Kafka.</div>
        </div>
      )}
    </div>
  );
}
