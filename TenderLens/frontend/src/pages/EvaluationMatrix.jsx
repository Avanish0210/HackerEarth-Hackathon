import { useEffect, useMemo, useState } from "react";
import { tenderlensApi } from "../api/tenderlens.js";
import EvidenceDrawer from "../components/EvidenceDrawer.jsx";
import StatusBadge from "../components/StatusBadge.jsx";

export default function EvaluationMatrix({ tenderId, selectedTender, notify }) {
  const [matrix, setMatrix] = useState({ criteria: [], bidders: [] });
  const [summary, setSummary] = useState(null);
  const [filter, setFilter] = useState("ALL");
  const [sort, setSort] = useState("companyName");
  const [drawerItem, setDrawerItem] = useState(null);

  async function load() {
    const [matrixBody, summaryBody] = await Promise.all([
      tenderlensApi.evaluationMatrix(tenderId),
      tenderlensApi.evaluationSummary(tenderId)
    ]);
    setMatrix(matrixBody);
    setSummary(summaryBody);
  }

  useEffect(() => {
    if (!tenderId) return;
    load().catch((error) => notify(error.message));
    const timer = window.setInterval(() => load().catch(() => {}), 3000);
    return () => window.clearInterval(timer);
  }, [tenderId]);

  const summariesByBidder = useMemo(() => {
    const map = new Map();
    (summary?.summaries || []).forEach((item) => map.set(item.bidderId, item));
    return map;
  }, [summary]);

  const bidders = useMemo(() => {
    const rows = [...(matrix.bidders || [])].map((bidder) => ({
      ...bidder,
      overallVerdict: summariesByBidder.get(bidder.bidderId)?.overallVerdict || "PENDING"
    }));
    return rows
      .filter((row) => filter === "ALL" || row.overallVerdict === filter)
      .sort((a, b) => String(a[sort] || "").localeCompare(String(b[sort] || "")));
  }, [matrix.bidders, summariesByBidder, filter, sort]);

  async function openCell(bidder, criterion, cell) {
    const detail = await tenderlensApi.evaluationBidder(tenderId, bidder.bidderId).catch(() => null);
    const result = detail?.results?.find((item) => item.criterionId === criterion.id);
    setDrawerItem({
      companyName: bidder.companyName,
      ...criterion,
      ...cell,
      ...(result || {})
    });
  }

  return (
    <div className="matrix-page">
      <section className="panel wide">
        <div className="section-title">
          <div>
            <span className="eyebrow">{selectedTender?.tenderRef}</span>
            <h2>{matrix.criteria?.length || 0} criteria x {matrix.bidders?.length || 0} bidders</h2>
          </div>
          <div className="toolbar">
            <select value={filter} onChange={(e) => setFilter(e.target.value)}>
              <option value="ALL">All verdicts</option>
              <option value="ELIGIBLE">Eligible</option>
              <option value="NOT_ELIGIBLE">Not eligible</option>
              <option value="NEEDS_REVIEW">Needs review</option>
              <option value="PENDING">Pending</option>
            </select>
            <select value={sort} onChange={(e) => setSort(e.target.value)}>
              <option value="companyName">Company</option>
              <option value="bidderRef">Bidder ref</option>
            </select>
          </div>
        </div>
        <div className="matrix-wrap">
          {matrix.criteria?.length && bidders.length ? (
          <table className="matrix-table">
            <thead>
              <tr>
                <th>Bidder</th>
                {matrix.criteria?.map((criterion) => <th key={criterion.id} title={criterion.description}>{criterion.ref}</th>)}
                <th>Overall</th>
              </tr>
            </thead>
            <tbody>
              {bidders.map((bidder) => (
                <tr key={bidder.bidderId}>
                  <td>
                    <strong>{bidder.companyName}</strong>
                    <span>{bidder.bidderRef}</span>
                  </td>
                  {matrix.criteria?.map((criterion) => {
                    const cell = bidder.cells?.[criterion.id] || { verdict: "PENDING", confidence: 0 };
                    return (
                      <td key={criterion.id}>
                        <button className={`matrix-cell verdict-${(cell.verdict || "PENDING").toLowerCase()}`} type="button" onClick={() => openCell(bidder, criterion, cell)} title="Open evidence">
                          {cell.verdict === "ELIGIBLE" ? "OK" : cell.verdict === "NOT_ELIGIBLE" ? "NO" : cell.verdict === "NEEDS_REVIEW" ? "RV" : "--"}
                        </button>
                      </td>
                    );
                  })}
                  <td><StatusBadge status={bidder.overallVerdict} compact /></td>
                </tr>
              ))}
            </tbody>
          </table>
          ) : (
            <div className="empty-state small">
              Matrix data is not available yet. Confirm criteria, upload bidders, then trigger evaluation.
            </div>
          )}
        </div>
      </section>
      <EvidenceDrawer open={Boolean(drawerItem)} item={drawerItem} onClose={() => setDrawerItem(null)} />
    </div>
  );
}
