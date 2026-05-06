import { useEffect, useMemo, useRef, useState } from "react";

const API_BASE = import.meta.env.VITE_UBID_API_BASE || "http://localhost:8080/api/ubid";

const departments = [
  { id: "DEPT_A", name: "Revenue Department", tone: "green", recordPath: "/mock/dept-a/record" },
  { id: "DEPT_B", name: "Municipal Corporation", tone: "blue", recordPath: "/mock/dept-b/record" },
  { id: "DEPT_C", name: "Utility Department", tone: "amber", recordPath: "/mock/dept-c/record" },
  { id: "SWS", name: "Single Window System", tone: "slate", recordPath: "/mock/sws/record" },
];

const navItems = [
  { id: "dashboard", label: "Dashboard", short: "Dash" },
  { id: "router", label: "Event Router", short: "Route" },
  { id: "conflicts", label: "Conflict Queue", short: "Conflicts" },
  { id: "audit", label: "Audit Trail", short: "Audit" },
  { id: "state", label: "Mock Dept State", short: "State" },
];

const demoFields = {
  street: "42 MG Road",
  city: "Varanasi",
  state: "Uttar Pradesh",
  pincode: "221001",
  district: "Varanasi",
};

const emptyStats = {
  totalEvents: 0,
  successCount: 0,
  failedCount: 0,
  skippedCount: 0,
  pendingConflicts: 0,
  activeDepartments: 0,
  registeredTranslators: [],
};

function api(path, options = {}) {
  const headers = options.body
    ? { "Content-Type": "application/json", ...(options.headers || {}) }
    : options.headers;

  return fetch(`${API_BASE}${path}`, { ...options, headers }).then(async (response) => {
    const text = await response.text();
    const data = text ? JSON.parse(text) : null;
    if (!response.ok) {
      const message = data?.message || data?.error || `${response.status} ${response.statusText}`;
      throw new Error(message);
    }
    return data;
  });
}

function makeEventId() {
  if (crypto.randomUUID) {
    return crypto.randomUUID();
  }
  return `evt-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function formatDate(value) {
  if (!value) return "Not recorded";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat(undefined, {
    month: "short",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  }).format(date);
}

function statusClass(status) {
  return `pill ${String(status || "UNKNOWN").toLowerCase()}`;
}

function JsonBlock({ value, compact = false }) {
  return (
    <pre className={compact ? "json compact" : "json"}>
      {JSON.stringify(value ?? {}, null, compact ? 0 : 2)}
    </pre>
  );
}

function EmptyState({ title, detail }) {
  return (
    <div className="empty-state">
      <div className="empty-mark">i</div>
      <h3>{title}</h3>
      <p>{detail}</p>
    </div>
  );
}

function Toast({ toast, onClose }) {
  if (!toast) return null;
  return (
    <button className={`toast ${toast.type || "info"}`} onClick={onClose} type="button">
      <strong>{toast.title}</strong>
      <span>{toast.message}</span>
    </button>
  );
}

function Header({ activePage, setActivePage, online }) {
  return (
    <aside className="shell-sidebar">
      <div className="brand">
        <div className="brand-mark">UB</div>
        <div>
          <h1>UBID Bridge</h1>
          <p>Inter-department sync console</p>
        </div>
      </div>

      <nav className="nav-list" aria-label="Primary">
        {navItems.map((item) => (
          <button
            className={activePage === item.id ? "nav-item active" : "nav-item"}
            key={item.id}
            onClick={() => setActivePage(item.id)}
            type="button"
          >
            <span className="nav-dot" />
            <span className="nav-label">{item.label}</span>
            <span className="nav-short">{item.short}</span>
          </button>
        ))}
      </nav>

      <div className="connection-card">
        <span className={online ? "pulse online" : "pulse offline"} />
        <div>
          <strong>{online ? "Backend reachable" : "Backend offline"}</strong>
          <p>{API_BASE}</p>
        </div>
      </div>
    </aside>
  );
}

function Dashboard({ stats, feed, conflicts, loading, onRefresh }) {
  const statCards = [
    { label: "Total events", value: stats.totalEvents, hint: "Audit records", tone: "blue" },
    { label: "Successful writes", value: stats.successCount, hint: "Department updates", tone: "green" },
    { label: "Failed writes", value: stats.failedCount, hint: "Needs retry attention", tone: "red" },
    { label: "Skipped duplicates", value: stats.skippedCount, hint: "Idempotency catches", tone: "amber" },
    { label: "Pending conflicts", value: stats.pendingConflicts, hint: "Manual queue", tone: "purple" },
    { label: "Active departments", value: stats.activeDepartments, hint: "Registered records", tone: "slate" },
  ];

  return (
    <div className="page">
      <PageTitle
        eyebrow="Operations overview"
        title="Dashboard"
        detail="Live bridge health, propagation outcomes, and the latest audit activity."
        action={<button className="secondary-btn" onClick={onRefresh} type="button">Refresh</button>}
      />

      <section className="stats-grid">
        {statCards.map((card) => (
          <article className={`stat-card ${card.tone}`} key={card.label}>
            <span>{card.label}</span>
            <strong>{loading ? "..." : card.value ?? 0}</strong>
            <small>{card.hint}</small>
          </article>
        ))}
      </section>

      <section className="split-grid">
        <Panel title="Live Audit Feed" subtitle="Most recent 50 audit rows">
          <AuditTable rows={feed} limit={10} />
        </Panel>
        <Panel title="Conflict Watch" subtitle="Unresolved manual review items">
          {conflicts.length ? (
            <div className="conflict-mini-list">
              {conflicts.slice(0, 5).map((item) => (
                <div className="conflict-mini" key={item.id}>
                  <div>
                    <strong>{item.ubid}</strong>
                    <span>{item.fieldName}</span>
                  </div>
                  <span className="pill conflict">{item.resolutionPolicy}</span>
                </div>
              ))}
            </div>
          ) : (
            <EmptyState title="No pending conflicts" detail="The queue is clear for now." />
          )}
        </Panel>
      </section>
    </div>
  );
}

function EventRouterPage({ onChanged, notify }) {
  const [eventType, setEventType] = useState("ADDRESS_CHANGE");
  const [ubid, setUbid] = useState("UBID-DEMO-001");
  const [initiatedBy, setInitiatedBy] = useState("DEMO_OFFICER");
  const [fieldsText, setFieldsText] = useState(JSON.stringify(demoFields, null, 2));
  const [busyAction, setBusyAction] = useState("");
  const [lastResponse, setLastResponse] = useState(null);

  async function runAction(label, fn) {
    setBusyAction(label);
    try {
      const result = await fn();
      setLastResponse(result);
      notify("Event accepted", result.message || `${label} completed`, "success");
      onChanged();
    } catch (error) {
      notify("Event failed", error.message, "error");
    } finally {
      setBusyAction("");
    }
  }

  function submitCustom() {
    runAction("custom", async () => {
      const fields = JSON.parse(fieldsText);
      return api("/events/sws", {
        method: "POST",
        body: JSON.stringify({
          eventId: makeEventId(),
          ubid,
          eventType,
          fields,
          occurredAt: new Date().toISOString(),
          initiatedBy,
        }),
      });
    });
  }

  return (
    <div className="page">
      <PageTitle
        eyebrow="Direction 1"
        title="Event Router"
        detail="Fire SWS events into the bridge and fan them out to registered departments."
      />

      <section className="router-layout">
        <Panel title="Demo Events" subtitle="One-click flows for the hackathon demo">
          <div className="demo-actions">
            <button
              className="primary-btn"
              disabled={!!busyAction}
              onClick={() => runAction("demo", () => api("/events/demo", { method: "POST" }))}
              type="button"
            >
              Fire address demo
            </button>
            <button
              className="warning-btn"
              disabled={!!busyAction}
              onClick={() => runAction("conflict", () => api("/events/demo/conflict", { method: "POST" }))}
              type="button"
            >
              Fire conflict demo
            </button>
          </div>
          <div className="route-map">
            {departments.slice(0, 3).map((department) => (
              <div className={`department-chip ${department.tone}`} key={department.id}>
                <span>{department.id}</span>
                <strong>{department.name}</strong>
              </div>
            ))}
          </div>
          {lastResponse && <JsonBlock value={lastResponse} />}
        </Panel>

        <Panel title="Custom SWS Event" subtitle="Build a canonical payload and submit it to /events/sws">
          <div className="form-grid">
            <label>
              UBID
              <input value={ubid} onChange={(event) => setUbid(event.target.value)} />
            </label>
            <label>
              Event type
              <select value={eventType} onChange={(event) => setEventType(event.target.value)}>
                <option>ADDRESS_CHANGE</option>
                <option>NAME_CHANGE</option>
                <option>MOBILE_CHANGE</option>
                <option>DOCUMENT_UPDATE</option>
              </select>
            </label>
            <label>
              Initiated by
              <input value={initiatedBy} onChange={(event) => setInitiatedBy(event.target.value)} />
            </label>
          </div>
          <label className="stacked">
            Fields JSON
            <textarea value={fieldsText} onChange={(event) => setFieldsText(event.target.value)} rows={10} />
          </label>
          <button className="primary-btn" disabled={!!busyAction} onClick={submitCustom} type="button">
            Submit event
          </button>
        </Panel>
      </section>
    </div>
  );
}

function ConflictQueuePage({ conflicts, onChanged, notify }) {
  const [selected, setSelected] = useState(null);
  const [resolvedBy, setResolvedBy] = useState("BRIDGE_OFFICER");
  const [resolvedValue, setResolvedValue] = useState("");
  const [resolutionNote, setResolutionNote] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!selected && conflicts.length) {
      setSelected(conflicts[0]);
      setResolvedValue(conflicts[0].valueA || "");
    }
  }, [conflicts, selected]);

  function chooseConflict(item) {
    setSelected(item);
    setResolvedValue(item.valueA || "");
    setResolutionNote("");
  }

  async function resolveConflict() {
    if (!selected) return;
    setBusy(true);
    try {
      const result = await api("/conflicts/resolve", {
        method: "POST",
        body: JSON.stringify({
          conflictId: selected.id,
          resolvedValue,
          resolvedBy,
          resolutionNote,
        }),
      });
      notify("Conflict resolved", result.message, "success");
      setSelected(null);
      onChanged();
    } catch (error) {
      notify("Resolution failed", error.message, "error");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="page">
      <PageTitle
        eyebrow="Manual review"
        title="Conflict Queue"
        detail="Compare competing values, choose the accepted value, and write the resolution audit record."
      />

      <section className="conflict-layout">
        <Panel title={`Pending Conflicts (${conflicts.length})`} subtitle="Unresolved queue entries">
          {conflicts.length ? (
            <div className="queue-list">
              {conflicts.map((item) => (
                <button
                  className={selected?.id === item.id ? "queue-row active" : "queue-row"}
                  key={item.id}
                  onClick={() => chooseConflict(item)}
                  type="button"
                >
                  <strong>{item.ubid}</strong>
                  <span>{item.fieldName}</span>
                  <small>{formatDate(item.createdAt)}</small>
                </button>
              ))}
            </div>
          ) : (
            <EmptyState title="Nothing waiting" detail="Manual escalation items will appear here." />
          )}
        </Panel>

        <Panel title="Resolve Conflict" subtitle="Select the final value to keep">
          {selected ? (
            <div className="resolution-card">
              <div className="comparison-grid">
                <button className="value-choice" onClick={() => setResolvedValue(selected.valueA)} type="button">
                  <span>{selected.sourceA}</span>
                  <strong>{selected.valueA}</strong>
                </button>
                <button className="value-choice" onClick={() => setResolvedValue(selected.valueB)} type="button">
                  <span>{selected.sourceB}</span>
                  <strong>{selected.valueB}</strong>
                </button>
              </div>
              <div className="form-grid">
                <label>
                  Conflict ID
                  <input readOnly value={selected.id} />
                </label>
                <label>
                  Resolved by
                  <input value={resolvedBy} onChange={(event) => setResolvedBy(event.target.value)} />
                </label>
              </div>
              <label className="stacked">
                Accepted value
                <input value={resolvedValue} onChange={(event) => setResolvedValue(event.target.value)} />
              </label>
              <label className="stacked">
                Resolution note
                <textarea
                  value={resolutionNote}
                  onChange={(event) => setResolutionNote(event.target.value)}
                  rows={4}
                  placeholder="Verified against citizen source record"
                />
              </label>
              <button className="primary-btn" disabled={busy} onClick={resolveConflict} type="button">
                Mark resolved
              </button>
            </div>
          ) : (
            <EmptyState title="Select a conflict" detail="Choose a queue item to compare source values." />
          )}
        </Panel>
      </section>
    </div>
  );
}

function AuditTrailPage({ feed, notify }) {
  const [mode, setMode] = useState("ubid");
  const [query, setQuery] = useState("UBID-DEMO-001");
  const [results, setResults] = useState(null);
  const [busy, setBusy] = useState(false);

  async function search() {
    if (!query.trim()) return;
    setBusy(true);
    try {
      const path = mode === "ubid" ? `/audit/ubid/${encodeURIComponent(query)}` : `/audit/event/${encodeURIComponent(query)}`;
      const result = await api(path);
      setResults(result.history || result.records || []);
      notify("Audit loaded", `${result.count ?? 0} records found`, "success");
    } catch (error) {
      notify("Search failed", error.message, "error");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="page">
      <PageTitle
        eyebrow="Traceability"
        title="Audit Trail"
        detail="Search complete propagation history by UBID or event ID."
      />

      <Panel title="Audit Search" subtitle="Query append-only audit records">
        <div className="search-row">
          <div className="segmented">
            <button className={mode === "ubid" ? "active" : ""} onClick={() => setMode("ubid")} type="button">
              UBID
            </button>
            <button className={mode === "event" ? "active" : ""} onClick={() => setMode("event")} type="button">
              Event ID
            </button>
          </div>
          <input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            onKeyDown={(event) => event.key === "Enter" && search()}
            placeholder={mode === "ubid" ? "UBID-DEMO-001" : "event UUID"}
          />
          <button className="primary-btn" disabled={busy} onClick={search} type="button">
            Search
          </button>
        </div>
      </Panel>

      <Panel title={results ? "Search Results" : "Recent Feed"} subtitle={results ? `${results.length} matching records` : "Latest audit rows"}>
        <AuditTable rows={results || feed} />
      </Panel>
    </div>
  );
}

function MockStatePage({ notify }) {
  const [state, setState] = useState({});
  const [recordUbid, setRecordUbid] = useState("UBID-DEMO-001");
  const [records, setRecords] = useState({});
  const [loading, setLoading] = useState(false);

  async function loadState() {
    setLoading(true);
    try {
      setState(await api("/mock/state"));
    } catch (error) {
      notify("State load failed", error.message, "error");
    } finally {
      setLoading(false);
    }
  }

  async function loadRecords() {
    setLoading(true);
    try {
      const pairs = await Promise.all(
        departments.map(async (department) => [
          department.id,
          await api(`${department.recordPath}/${encodeURIComponent(recordUbid)}`),
        ]),
      );
      setRecords(Object.fromEntries(pairs));
      notify("Records loaded", `Fetched ${recordUbid} from all mock systems`, "success");
    } catch (error) {
      notify("Record lookup failed", error.message, "error");
    } finally {
      setLoading(false);
    }
  }

  async function resetState() {
    setLoading(true);
    try {
      const result = await api("/mock/reset", { method: "DELETE" });
      notify("Mock state reset", result.message, "success");
      setRecords({});
      await loadState();
    } catch (error) {
      notify("Reset failed", error.message, "error");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadState();
  }, []);

  return (
    <div className="page">
      <PageTitle
        eyebrow="Demo systems"
        title="Mock Dept State"
        detail="Inspect what each mock department and SWS store has received."
        action={<button className="secondary-btn" onClick={loadState} type="button">Refresh state</button>}
      />

      <Panel title="Record Lookup" subtitle="Read one UBID from all mock stores">
        <div className="search-row">
          <input value={recordUbid} onChange={(event) => setRecordUbid(event.target.value)} />
          <button className="primary-btn" disabled={loading} onClick={loadRecords} type="button">
            Lookup UBID
          </button>
          <button className="danger-btn" disabled={loading} onClick={resetState} type="button">
            Reset stores
          </button>
        </div>
      </Panel>

      {Object.keys(records).length > 0 && (
        <section className="department-grid">
          {departments.map((department) => (
            <Panel key={department.id} title={department.name} subtitle={department.id}>
              <JsonBlock value={records[department.id]} />
            </Panel>
          ))}
        </section>
      )}

      <Panel title="Full Mock State" subtitle="Current in-memory store grouped by system">
        {Object.keys(state || {}).length ? (
          <div className="state-grid">
            {departments.map((department) => (
              <div className="state-column" key={department.id}>
                <div className={`department-chip ${department.tone}`}>
                  <span>{department.id}</span>
                  <strong>{department.name}</strong>
                </div>
                <JsonBlock value={state[department.id] || {}} compact />
              </div>
            ))}
          </div>
        ) : (
          <EmptyState title="No state yet" detail="Fire a demo event, then refresh this page." />
        )}
      </Panel>
    </div>
  );
}

function AuditTable({ rows, limit }) {
  const visibleRows = limit ? rows.slice(0, limit) : rows;

  if (!visibleRows.length) {
    return <EmptyState title="No audit entries" detail="Audit records will appear after department writes complete." />;
  }

  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Status</th>
            <th>UBID</th>
            <th>Route</th>
            <th>Event</th>
            <th>Field</th>
            <th>Time</th>
          </tr>
        </thead>
        <tbody>
          {visibleRows.map((row) => (
            <tr key={`${row.id}-${row.eventId}-${row.targetSystem}`}>
              <td><span className={statusClass(row.status)}>{row.status || "UNKNOWN"}</span></td>
              <td><strong>{row.ubid}</strong></td>
              <td>{row.sourceSystem} &rarr; {row.targetSystem}</td>
              <td>
                <span>{row.eventType}</span>
                <small>{row.eventId}</small>
              </td>
              <td>
                <span>{row.fieldChanged || "All fields"}</span>
                {row.retryCount > 0 && <small>Retry {row.retryCount}</small>}
              </td>
              <td>{formatDate(row.createdAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function Panel({ title, subtitle, children }) {
  return (
    <section className="panel">
      <div className="panel-head">
        <div>
          <h2>{title}</h2>
          {subtitle && <p>{subtitle}</p>}
        </div>
      </div>
      {children}
    </section>
  );
}

function PageTitle({ eyebrow, title, detail, action }) {
  return (
    <header className="page-title">
      <div>
        <span>{eyebrow}</span>
        <h2>{title}</h2>
        <p>{detail}</p>
      </div>
      {action}
    </header>
  );
}

export default function App() {
  const [activePage, setActivePage] = useState("dashboard");
  const [stats, setStats] = useState(emptyStats);
  const [feed, setFeed] = useState([]);
  const [conflicts, setConflicts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [online, setOnline] = useState(false);
  const [toast, setToast] = useState(null);
  const toastTimer = useRef(null);

  const notify = (title, message, type = "info") => {
    setToast({ title, message, type });
    window.clearTimeout(toastTimer.current);
    toastTimer.current = window.setTimeout(() => setToast(null), 4800);
  };

  async function refreshAll(showErrors = false) {
    setLoading(true);
    try {
      const [nextStats, nextFeed, nextConflicts] = await Promise.all([
        api("/dashboard/stats"),
        api("/audit/feed"),
        api("/conflicts/pending"),
      ]);
      setStats({ ...emptyStats, ...nextStats });
      setFeed(Array.isArray(nextFeed) ? nextFeed : []);
      setConflicts(nextConflicts?.conflicts || []);
      setOnline(true);
    } catch (error) {
      setOnline(false);
      if (showErrors) {
        notify("Backend unavailable", error.message, "error");
      }
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    refreshAll(false);
    const timer = window.setInterval(() => refreshAll(false), 5000);
    return () => window.clearInterval(timer);
  }, []);

  const page = useMemo(() => {
    const props = {
      stats,
      feed,
      conflicts,
      loading,
      notify,
      onChanged: () => refreshAll(true),
      onRefresh: () => refreshAll(true),
    };
    if (activePage === "router") return <EventRouterPage {...props} />;
    if (activePage === "conflicts") return <ConflictQueuePage {...props} />;
    if (activePage === "audit") return <AuditTrailPage {...props} />;
    if (activePage === "state") return <MockStatePage {...props} />;
    return <Dashboard {...props} />;
  }, [activePage, stats, feed, conflicts, loading]);

  return (
    <div className="app-shell">
      <Header activePage={activePage} setActivePage={setActivePage} online={online} />
      <main className="content-shell">{page}</main>
      <Toast toast={toast} onClose={() => setToast(null)} />
    </div>
  );
}
