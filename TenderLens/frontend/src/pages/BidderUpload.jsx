import { useEffect, useState } from "react";
import { tenderlensApi } from "../api/tenderlens.js";
import Icon from "../components/Icons.jsx";
import StatusBadge from "../components/StatusBadge.jsx";

export default function BidderUpload({ tenderId, selectedTender, notify, navigate }) {
  const [bidders, setBidders] = useState([]);
  const [rows, setRows] = useState([{ bidderRef: "", companyName: "", file: null, ocrRequired: false }]);
  const [busy, setBusy] = useState(false);

  async function load() {
    const data = await tenderlensApi.bidders(tenderId);
    setBidders(data.bidders || []);
  }

  useEffect(() => {
    if (!tenderId) return;
    load().catch((error) => notify(error.message));
    const timer = window.setInterval(() => load().catch(() => {}), 3000);
    return () => window.clearInterval(timer);
  }, [tenderId]);

  function updateRow(index, patch) {
    setRows((items) => items.map((row, rowIndex) => (rowIndex === index ? { ...row, ...patch } : row)));
  }

  async function uploadAll(event) {
    event.preventDefault();
    setBusy(true);
    try {
      for (const row of rows) {
        if (row.file && row.bidderRef && row.companyName) {
          await tenderlensApi.uploadBidder(tenderId, row);
        }
      }
      notify("Bidder uploads queued.");
      setRows([{ bidderRef: "", companyName: "", file: null, ocrRequired: false }]);
      await load();
    } catch (error) {
      notify(error.message);
    } finally {
      setBusy(false);
    }
  }

  async function triggerEvaluation() {
    try {
      const result = await tenderlensApi.triggerEvaluation(tenderId, "OFFICER");
      notify(result.message || "Evaluation started.");
      navigate("matrix");
    } catch (error) {
      notify(error.message);
    }
  }

  return (
    <div className="split-page">
      <section className="panel upload-panel">
        <div className="section-title">
          <div>
            <span className="eyebrow">{selectedTender?.tenderRef}</span>
            <h2>Bidder PDF upload</h2>
          </div>
          <button className="icon-button" type="button" onClick={() => setRows([...rows, { bidderRef: "", companyName: "", file: null, ocrRequired: false }])} title="Add bidder">
            +
          </button>
        </div>

        {selectedTender?.status !== "CRITERIA_CONFIRMED" && selectedTender?.status !== "EVALUATION_RUNNING" ? (
          <div className="warning-box">
            Criteria must be confirmed before bidder uploads are accepted by the backend.
          </div>
        ) : null}

        <form className="upload-queue" onSubmit={uploadAll}>
          {rows.map((row, index) => (
            <div className="bidder-form-row" key={index}>
              <input placeholder="Bidder ref" value={row.bidderRef} onChange={(e) => updateRow(index, { bidderRef: e.target.value })} />
              <input placeholder="Company name" value={row.companyName} onChange={(e) => updateRow(index, { companyName: e.target.value })} />
              <label className="file-button">
                <Icon name="upload" />
                {row.file ? row.file.name : "PDF"}
                <input type="file" accept="application/pdf,.pdf" onChange={(e) => updateRow(index, { file: e.target.files?.[0] || null })} />
              </label>
              <label className="toggle-row compact">
                <input type="checkbox" checked={row.ocrRequired} onChange={(e) => updateRow(index, { ocrRequired: e.target.checked })} />
                OCR
              </label>
            </div>
          ))}
          <button className="button primary" disabled={busy} type="submit">{busy ? "Uploading..." : "Upload bidders"}</button>
        </form>
      </section>

      <section className="panel">
        <div className="section-title">
          <div>
            <span className="eyebrow">Live queue</span>
            <h2>Parse status</h2>
          </div>
          <button className="button" type="button" onClick={triggerEvaluation}>Start evaluation</button>
        </div>
        <div className="queue-list">
          {bidders.length ? bidders.map((bidder) => (
            <article key={bidder.id}>
              <div>
                <strong>{bidder.companyName}</strong>
                <span>{bidder.bidderRef}</span>
              </div>
              <StatusBadge status={bidder.parseStatus} />
              {bidder.parseStatus === "FAILED" ? <button className="button danger" type="button">Retry</button> : null}
            </article>
          )) : <div className="empty-state small">No bidders uploaded yet.</div>}
        </div>
      </section>
    </div>
  );
}
