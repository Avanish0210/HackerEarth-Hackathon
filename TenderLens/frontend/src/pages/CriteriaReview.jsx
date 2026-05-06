import { useEffect, useState } from "react";
import { tenderlensApi } from "../api/tenderlens.js";
import ConfidenceBar from "../components/ConfidenceBar.jsx";
import StatusBadge from "../components/StatusBadge.jsx";

export default function CriteriaReview({ tenderId, selectedTender, refreshShell, notify, navigate }) {
  const [body, setBody] = useState(null);
  const [criteria, setCriteria] = useState([]);
  const [confirmedBy, setConfirmedBy] = useState("OFFICER");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!tenderId) return;
    tenderlensApi
      .criteria(tenderId)
      .then((data) => {
        setBody(data);
        setCriteria(data.criteria || []);
      })
      .catch((error) => notify(error.message));
  }, [tenderId, notify]);

  function updateCriterion(id, patch) {
    setCriteria((items) => items.map((item) => (item.id === id ? { ...item, ...patch } : item)));
  }

  async function confirmAll() {
    setBusy(true);
    try {
      const edits = criteria.map((criterion) => ({
        criterionId: criterion.id,
        description: criterion.description,
        thresholdValue: criterion.thresholdValue || "",
        thresholdOperator: criterion.thresholdOperator || "",
        mandatory: Boolean(criterion.mandatory)
      }));
      const result = await tenderlensApi.confirmCriteria(tenderId, {
        tenderId: Number(tenderId),
        confirmedBy,
        edits
      });
      notify(result.message || "Criteria confirmed.");
      await refreshShell();
      navigate("bidders");
    } catch (error) {
      notify(error.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="criteria-page">
      <section className="panel wide">
        <div className="section-title">
          <div>
            <span className="eyebrow">{selectedTender?.tenderRef}</span>
            <h2>{body?.confirmed || 0} of {body?.total || 0} confirmed</h2>
          </div>
          <div className="confirm-strip">
            <input value={confirmedBy} onChange={(e) => setConfirmedBy(e.target.value)} aria-label="Confirmed by" />
            <button className="button primary" type="button" disabled={busy || !criteria.length} onClick={confirmAll}>
              {busy ? "Confirming..." : "Confirm all"}
            </button>
          </div>
        </div>
      </section>

      <section className="criteria-grid">
        {criteria.map((criterion) => (
          <article className={`criterion-card ${criterion.confirmedByOfficer ? "confirmed" : "unconfirmed"}`} key={criterion.id}>
            <div className="criterion-head">
              <strong>{criterion.criterionRef}</strong>
              <StatusBadge status={criterion.confirmedByOfficer ? "ELIGIBLE" : "NEEDS_REVIEW"} compact />
            </div>
            <span className="type-badge">{criterion.criterionType}</span>
            <label>
              Description
              <textarea value={criterion.description || ""} onChange={(e) => updateCriterion(criterion.id, { description: e.target.value })} />
            </label>
            <div className="criterion-fields">
              <label>
                Operator
                <select value={criterion.thresholdOperator || ""} onChange={(e) => updateCriterion(criterion.id, { thresholdOperator: e.target.value })}>
                  <option value="">None</option>
                  <option value="GTE">GTE</option>
                  <option value="LTE">LTE</option>
                  <option value="EQ">EQ</option>
                  <option value="CONTAINS">CONTAINS</option>
                </select>
              </label>
              <label>
                Threshold
                <input value={criterion.thresholdValue || ""} onChange={(e) => updateCriterion(criterion.id, { thresholdValue: e.target.value })} />
              </label>
            </div>
            <label className="toggle-row">
              <input type="checkbox" checked={Boolean(criterion.mandatory)} onChange={(e) => updateCriterion(criterion.id, { mandatory: e.target.checked })} />
              Mandatory
            </label>
            <ConfidenceBar value={criterion.extractionConfidence || 0} />
          </article>
        ))}
        {!criteria.length ? (
          <div className="empty-state criteria-empty">
            No criteria were returned for this tender. Check the uploaded PDF text quality, Ollama extraction logs, or upload a clearer tender document.
          </div>
        ) : null}
      </section>
    </div>
  );
}
