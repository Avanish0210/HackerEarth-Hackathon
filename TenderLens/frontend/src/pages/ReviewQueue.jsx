import { useEffect, useState } from "react";
import { tenderlensApi } from "../api/tenderlens.js";
import ConfidenceBar from "../components/ConfidenceBar.jsx";
import EvidenceDrawer from "../components/EvidenceDrawer.jsx";

export default function ReviewQueue({ tenderId, selectedTender, refreshShell, notify }) {
  const [items, setItems] = useState([]);
  const [notes, setNotes] = useState({});
  const [reviewer, setReviewer] = useState("OFFICER");
  const [drawerItem, setDrawerItem] = useState(null);
  const [resolving, setResolving] = useState(new Set());

  async function load() {
    const body = await tenderlensApi.pendingReviews(tenderId);
    const summaries = body.items || [];
    const enriched = await Promise.all(
      summaries.map(async (item) => {
        const detail = await tenderlensApi.reviewDetail(item.reviewId).catch(() => null);
        return detail?.evaluation ? { ...item, ...detail.evaluation } : item;
      })
    );
    setItems(enriched);
  }

  useEffect(() => {
    if (!tenderId) return;
    load().catch((error) => notify(error.message));
    const timer = window.setInterval(() => load().catch(() => {}), 3000);
    return () => window.clearInterval(timer);
  }, [tenderId]);

  async function resolve(reviewId, overrideVerdict) {
    setResolving((current) => new Set([...current, reviewId]));
    try {
      await tenderlensApi.resolveReview({
        reviewId,
        overrideVerdict,
        reviewer,
        reviewNote: notes[reviewId] || ""
      });
      setItems((current) => current.filter((item) => item.reviewId !== reviewId));
      notify("Review resolved.");
      refreshShell();
    } catch (error) {
      notify(error.message);
      setResolving((current) => {
        const next = new Set(current);
        next.delete(reviewId);
        return next;
      });
    }
  }

  async function openDetail(reviewId) {
    const detail = await tenderlensApi.reviewDetail(reviewId);
    setDrawerItem({ ...detail.evaluation, reason: detail.reason, reviewId: detail.reviewId });
  }

  return (
    <div className="review-page">
      <section className="panel wide">
        <div className="section-title">
          <div>
            <span className="eyebrow">{selectedTender?.tenderRef}</span>
            <h2>{items.length} pending review items</h2>
          </div>
          <input className="reviewer-input" value={reviewer} onChange={(e) => setReviewer(e.target.value)} aria-label="Reviewer" />
        </div>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Company</th>
                <th>Criterion</th>
                <th>Extracted value</th>
                <th>Excerpt</th>
                <th>Confidence</th>
                <th>Reason</th>
                <th>Note</th>
                <th>Decision</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.reviewId} className={resolving.has(item.reviewId) ? "muted-row" : ""}>
                  <td>
                    <button className="link-button" type="button" onClick={() => openDetail(item.reviewId)}>
                      {item.companyName}
                    </button>
                    <span>{item.bidderRef}</span>
                  </td>
                  <td>
                    <strong>{item.criterionRef}</strong>
                    <span>{item.criterionDescription}</span>
                  </td>
                  <td>{item.extractedValue || "-"}</td>
                  <td className="excerpt-cell">{item.verbatimExcerpt || "-"}</td>
                  <td><ConfidenceBar value={item.confidenceScore} /></td>
                  <td>{item.reason}</td>
                  <td>
                    <input value={notes[item.reviewId] || ""} onChange={(e) => setNotes({ ...notes, [item.reviewId]: e.target.value })} placeholder="Optional note" />
                  </td>
                  <td>
                    <div className="decision-buttons">
                      <button className="button success" type="button" onClick={() => resolve(item.reviewId, "ELIGIBLE")}>Eligible</button>
                      <button className="button danger" type="button" onClick={() => resolve(item.reviewId, "NOT_ELIGIBLE")}>Not eligible</button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {!items.length ? <div className="empty-state small">No pending review items for this tender.</div> : null}
        </div>
      </section>
      <EvidenceDrawer open={Boolean(drawerItem)} item={drawerItem} onClose={() => setDrawerItem(null)} />
    </div>
  );
}
