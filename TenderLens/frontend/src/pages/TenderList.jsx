import { useState } from "react";
import { tenderlensApi } from "../api/tenderlens.js";
import Icon from "../components/Icons.jsx";
import StatusBadge from "../components/StatusBadge.jsx";

export default function TenderList({
  tenders,
  selectedTenderId,
  selectedTender,
  selectTender,
  refreshShell,
  notify,
  navigate
}) {
  const [form, setForm] = useState({ tenderRef: "", title: "", uploadedBy: "", ocrRequired: false });
  const [file, setFile] = useState(null);
  const [busy, setBusy] = useState(false);

  async function uploadTender(event) {
    event.preventDefault();
    if (!file) return notify("Attach a tender PDF first.");
    setBusy(true);
    try {
      const result = await tenderlensApi.uploadTender({ ...form, file });
      notify(result.message || "Tender uploaded.");
      setForm({ tenderRef: "", title: "", uploadedBy: "", ocrRequired: false });
      setFile(null);
      const uploadedTenderId = result.tenderId ? String(result.tenderId) : "";
      if (uploadedTenderId) selectTender(uploadedTenderId, "tenders");
      await refreshShell(uploadedTenderId);
    } catch (error) {
      notify(error.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="tender-management">
      {selectedTender ? (
        <section className="panel wide active-tender-banner">
          <div>
            <span className="eyebrow">Active tender</span>
            <h2>{selectedTender.tenderRef} - {selectedTender.title}</h2>
          </div>
          <div className="action-row">
            <StatusBadge status={selectedTender.status} />
            <button className="button" type="button" onClick={() => navigate("criteria")}>Criteria</button>
            <button className="button" type="button" onClick={() => navigate("bidders")}>Bidders</button>
            <button className="button" type="button" onClick={() => navigate("matrix")}>Matrix</button>
          </div>
        </section>
      ) : null}

    <div className="split-page">
      <section className="panel">
        <div className="section-title">
          <div>
            <span className="eyebrow">Tender list</span>
            <h2>All tenders</h2>
          </div>
        </div>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Reference</th>
                <th>Title</th>
                <th>Status</th>
                <th>Bidders</th>
                <th>Criteria</th>
                <th>Reviews</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {tenders.map((tender) => (
                <tr key={tender.id} className={String(tender.id) === String(selectedTenderId) ? "selected-row" : ""}>
                  <td><strong>{tender.tenderRef}</strong></td>
                  <td>{tender.title}</td>
                  <td><StatusBadge status={tender.status} compact /></td>
                  <td>{tender.bidderCount || 0}</td>
                  <td>{tender.criteriaCount || 0}</td>
                  <td><span className="pill">{tender.pendingReviews || 0}</span></td>
                  <td>
                    <button className="button" type="button" onClick={() => selectTender(tender.id, "tenders")}>
                      {String(tender.id) === String(selectedTenderId) ? "Active" : "Use tender"}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {!tenders.length ? <div className="empty-state small">No tenders uploaded yet.</div> : null}
        </div>
      </section>

      <section className="panel upload-panel">
        <div className="section-title">
          <div>
            <span className="eyebrow">Upload tender</span>
            <h2>New PDF intake</h2>
          </div>
        </div>
        <form className="form-grid" onSubmit={uploadTender}>
          <label>
            Tender ref
            <input value={form.tenderRef} onChange={(e) => setForm({ ...form, tenderRef: e.target.value })} required />
          </label>
          <label>
            Title
            <input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} required />
          </label>
          <label>
            Uploaded by
            <input value={form.uploadedBy} onChange={(e) => setForm({ ...form, uploadedBy: e.target.value })} required />
          </label>
          <label className="toggle-row">
            <input type="checkbox" checked={form.ocrRequired} onChange={(e) => setForm({ ...form, ocrRequired: e.target.checked })} />
            OCR required
          </label>
          <label className="dropzone">
            <Icon name="upload" size={24} />
            <span>{file ? file.name : "Drop or choose tender PDF"}</span>
            <input type="file" accept="application/pdf,.pdf" onChange={(e) => setFile(e.target.files?.[0] || null)} />
          </label>
          <button className="button primary" disabled={busy} type="submit">
            {busy ? "Uploading..." : "Upload and extract criteria"}
          </button>
        </form>
      </section>
    </div>
    </div>
  );
}
