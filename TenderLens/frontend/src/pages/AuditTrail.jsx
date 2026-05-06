import { useEffect, useMemo, useState } from "react";
import { tenderlensApi } from "../api/tenderlens.js";

export default function AuditTrail({ tenderId, selectedTender, notify }) {
  const [events, setEvents] = useState([]);
  const [filter, setFilter] = useState("ALL");

  useEffect(() => {
    if (!tenderId) return;
    tenderlensApi.auditLog(tenderId).then(setEvents).catch((error) => notify(error.message));
    const timer = window.setInterval(() => {
      tenderlensApi.auditLog(tenderId).then(setEvents).catch(() => {});
    }, 3000);
    return () => window.clearInterval(timer);
  }, [tenderId, notify]);

  const actions = useMemo(() => ["ALL", ...new Set(events.map((event) => event.action))], [events]);
  const visible = filter === "ALL" ? events : events.filter((event) => event.action === filter);

  return (
    <section className="panel wide">
      <div className="section-title">
        <div>
          <span className="eyebrow">{selectedTender?.tenderRef}</span>
          <h2>{visible.length} audit events</h2>
        </div>
        <select value={filter} onChange={(e) => setFilter(e.target.value)}>
          {actions.map((action) => <option key={action} value={action}>{action}</option>)}
        </select>
      </div>

      <div className="timeline">
        {visible.map((event) => (
          <article key={event.id}>
            <div className="timeline-dot" />
            <div>
              <strong>{event.action}</strong>
              <p>{event.detail || "No detail supplied."}</p>
              <span>{event.performedBy || "SYSTEM"} - {event.createdAt ? new Date(event.createdAt).toLocaleString() : "-"}</span>
            </div>
          </article>
        ))}
        {!visible.length ? <div className="empty-state small">No audit events match this filter.</div> : null}
      </div>
    </section>
  );
}
