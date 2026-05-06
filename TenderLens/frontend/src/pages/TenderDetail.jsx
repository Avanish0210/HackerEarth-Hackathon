import { useEffect, useState } from "react";
import { tenderlensApi } from "../api/tenderlens.js";
import Icon from "../components/Icons.jsx";
import StatusBadge from "../components/StatusBadge.jsx";
import TenderList from "./TenderList.jsx";

const steps = ["UPLOADED", "CRITERIA_EXTRACTED", "CRITERIA_CONFIRMED", "EVALUATION_RUNNING", "COMPLETED"];

export default function TenderDetail({ tenderId, selectedTender, tenders, refreshShell, notify, navigate }) {
  const [tender, setTender] = useState(selectedTender);
  const [summary, setSummary] = useState(null);

  useEffect(() => {
    if (!tenderId) return;
    Promise.all([tenderlensApi.tender(tenderId), tenderlensApi.evaluationSummary(tenderId).catch(() => null)])
      .then(([tenderBody, summaryBody]) => {
        setTender(tenderBody);
        setSummary(summaryBody);
      })
      .catch((error) => notify(error.message));
  }, [tenderId, notify]);

  async function triggerEvaluation() {
    try {
      const result = await tenderlensApi.triggerEvaluation(tenderId, "OFFICER");
      notify(result.message || "Evaluation started.");
      await refreshShell();
      navigate("matrix");
    } catch (error) {
      notify(error.message);
    }
  }

  if (!tenderId) return <TenderList tenders={tenders} refreshShell={refreshShell} notify={notify} />;

  const activeIndex = Math.max(0, steps.indexOf(tender?.status));

  return (
    <div className="detail-page">
      <section className="panel wide">
        <div className="detail-header">
          <div>
            <span className="eyebrow">Tender detail</span>
            <h2>{tender?.title || "Tender"}</h2>
            <p>{tender?.tenderRef}</p>
          </div>
          <StatusBadge status={tender?.status} />
        </div>

        <div className="meta-grid">
          <div><span>Uploaded by</span><strong>{tender?.uploadedBy || "-"}</strong></div>
          <div><span>Created</span><strong>{formatDate(tender?.createdAt)}</strong></div>
          <div><span>OCR</span><strong>{tender?.ocrRequired ? "Required" : "Not required"}</strong></div>
          <div><span>File</span><strong>{tender?.fileName || "-"}</strong></div>
        </div>

        <div className="pipeline detailed">
          {steps.map((step, index) => (
            <div className={`pipeline-step ${index <= activeIndex ? "done" : ""}`} key={step}>
              <StatusBadge status={step} compact />
            </div>
          ))}
        </div>

        <div className="action-row">
          <button className="button" type="button" onClick={() => navigate("criteria")}>
            <Icon name="brain" /> Review criteria
          </button>
          <button className="button" type="button" onClick={() => navigate("bidders")}>
            <Icon name="upload" /> Upload bidders
          </button>
          <button className="button primary" type="button" onClick={triggerEvaluation}>
            <Icon name="play" /> Trigger evaluation
          </button>
          <button className="button" type="button" onClick={() => navigate("report")}>
            <Icon name="report" /> Generate report
          </button>
        </div>
      </section>

      <section className="stat-grid">
        <article className="mini-card"><span>Total bidders</span><strong>{summary?.totalBidders || 0}</strong></article>
        <article className="mini-card"><span>Total criteria</span><strong>{summary?.totalCriteria || 0}</strong></article>
        <article className="mini-card"><span>Tender status</span><strong>{summary?.tenderStatus || tender?.status || "-"}</strong></article>
      </section>
    </div>
  );
}

function formatDate(value) {
  return value ? new Date(value).toLocaleString() : "-";
}
