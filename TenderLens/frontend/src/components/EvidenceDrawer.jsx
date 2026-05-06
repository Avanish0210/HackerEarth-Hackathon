import ConfidenceBar from "./ConfidenceBar.jsx";
import Icon from "./Icons.jsx";
import StatusBadge from "./StatusBadge.jsx";

export default function EvidenceDrawer({ open, item, onClose, onOverride }) {
  if (!open) return null;

  return (
    <aside className="drawer" aria-label="Evidence detail">
      <div className="drawer-header">
        <div>
          <span className="eyebrow">Evidence chain</span>
          <h2>{item?.companyName || "Evaluation"}</h2>
        </div>
        <button className="icon-button" type="button" onClick={onClose} title="Close">
          <Icon name="close" />
        </button>
      </div>

      <div className="drawer-body">
        <section className="drawer-section">
          <span className="eyebrow">Criterion</span>
          <h3>{item?.criterionRef || item?.ref || "Criterion"}</h3>
          <p>{item?.criterionDescription || item?.description || "No description returned yet."}</p>
          <StatusBadge status={item?.verdict || "PENDING"} />
        </section>

        <section className="drawer-section evidence-grid">
          <div>
            <span>Extracted value</span>
            <strong>{item?.extractedValue || "Not available"}</strong>
          </div>
          <div>
            <span>Source page</span>
            <strong>{item?.sourcePage || "Pending"}</strong>
          </div>
          <div>
            <span>Confidence</span>
            <ConfidenceBar value={item?.confidenceScore || item?.confidence || 0} />
          </div>
        </section>

        <section className="drawer-section">
          <span className="eyebrow">Verbatim excerpt</span>
          <blockquote>{item?.verbatimExcerpt || "Evidence will appear here after evaluation completes."}</blockquote>
        </section>

        {onOverride ? (
          <section className="drawer-actions">
            <button type="button" className="button success" onClick={() => onOverride("ELIGIBLE")}>
              Mark eligible
            </button>
            <button type="button" className="button danger" onClick={() => onOverride("NOT_ELIGIBLE")}>
              Mark not eligible
            </button>
          </section>
        ) : null}
      </div>
    </aside>
  );
}
