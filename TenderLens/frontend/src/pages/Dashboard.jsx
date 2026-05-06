import { useEffect, useMemo, useState } from "react";
import { tenderlensApi } from "../api/tenderlens.js";
import StatCard from "../components/StatCard.jsx";
import StatusBadge from "../components/StatusBadge.jsx";

const pipeline = ["UPLOADED", "CRITERIA_EXTRACTED", "CRITERIA_CONFIRMED", "EVALUATION_RUNNING", "COMPLETED"];

export default function Dashboard({ tenders, navigate, notify }) {
  const [stats, setStats] = useState(null);
  const [feed, setFeed] = useState([]);

  useEffect(() => {
    let alive = true;
    async function load() {
      try {
        const [statsBody, feedBody] = await Promise.all([
          tenderlensApi.dashboardStats(),
          tenderlensApi.auditFeed(20)
        ]);
        if (alive) {
          setStats(statsBody);
          setFeed(feedBody.events || []);
        }
      } catch (error) {
        notify(error.message);
      }
    }
    load();
    const timer = window.setInterval(load, 3000);
    return () => {
      alive = false;
      window.clearInterval(timer);
    };
  }, [notify]);

  const verdictTotals = stats?.evaluations || {};
  const totalVerdicts =
    (verdictTotals.eligible || 0) + (verdictTotals.notEligible || 0) + (verdictTotals.needsReview || 0);
  const donut = useMemo(() => {
    const eligible = totalVerdicts ? ((verdictTotals.eligible || 0) / totalVerdicts) * 100 : 0;
    const review = totalVerdicts ? ((verdictTotals.needsReview || 0) / totalVerdicts) * 100 : 0;
    return {
      background: `conic-gradient(var(--green) 0 ${eligible}%, var(--yellow) ${eligible}% ${
        eligible + review
      }%, var(--red) ${eligible + review}% 100%)`
    };
  }, [totalVerdicts, verdictTotals]);

  return (
    <div className="page-grid">
      <section className="stat-grid">
        <StatCard icon="folder" label="Total tenders" value={stats?.tenders?.total} sublabel={`${stats?.tenders?.active || 0} active`} tone="blue" />
        <StatCard icon="upload" label="Total bidders" value={stats?.bidders?.total} sublabel={`${stats?.bidders?.pending || 0} pending parse`} tone="green" />
        <StatCard icon="matrix" label="Evaluations run" value={stats?.evaluations?.total} sublabel="criterion checks" tone="yellow" />
        <StatCard icon="review" label="Pending reviews" value={stats?.reviewQueue?.pending} sublabel="officer decisions" tone="red" />
      </section>

      <section className="panel wide">
        <div className="section-title">
          <div>
            <span className="eyebrow">Status pipeline</span>
            <h2>Tender lifecycle</h2>
          </div>
          <button className="button ghost" type="button" onClick={() => navigate("tenders")}>
            Open tenders
          </button>
        </div>
        <div className="pipeline">
          {pipeline.map((status) => {
            const count = tenders.filter((tender) => tender.status === status).length;
            return (
              <div className="pipeline-step" key={status}>
                <StatusBadge status={status} compact />
                <strong>{count}</strong>
              </div>
            );
          })}
        </div>
      </section>

      <section className="panel">
        <div className="section-title">
          <div>
            <span className="eyebrow">Verdicts</span>
            <h2>Eligibility mix</h2>
          </div>
        </div>
        <div className="donut-wrap">
          <div className="donut" style={donut}>
            <strong>{totalVerdicts}</strong>
            <span>checks</span>
          </div>
          <div className="legend">
            <span><i className="green" /> Eligible: {verdictTotals.eligible || 0}</span>
            <span><i className="yellow" /> Needs review: {verdictTotals.needsReview || 0}</span>
            <span><i className="red" /> Not eligible: {verdictTotals.notEligible || 0}</span>
          </div>
        </div>
      </section>

      <section className="panel feed-panel">
        <div className="section-title">
          <div>
            <span className="eyebrow">Live audit</span>
            <h2>Recent events</h2>
          </div>
        </div>
        <div className="audit-feed">
          {feed.length ? (
            feed.map((event) => (
              <article key={event.id}>
                <strong>{event.action}</strong>
                <p>{event.detail || "No detail supplied"}</p>
                <span>{event.performedBy} - {formatDate(event.createdAt)}</span>
              </article>
            ))
          ) : (
            <div className="empty-state small">No audit events yet.</div>
          )}
        </div>
      </section>
    </div>
  );
}

function formatDate(value) {
  return value ? new Date(value).toLocaleString() : "Pending";
}
