import Icon from "./Icons.jsx";
import StatusBadge from "./StatusBadge.jsx";

const navItems = [
  { id: "dashboard", label: "Dashboard", icon: "dashboard" },
  { id: "tenders", label: "Tenders", icon: "folder" },
  { id: "criteria", label: "Criteria", icon: "brain" },
  { id: "bidders", label: "Bidders", icon: "upload" },
  { id: "matrix", label: "Matrix", icon: "matrix" },
  { id: "reviews", label: "Review", icon: "review" },
  { id: "report", label: "Report", icon: "report" },
  { id: "audit", label: "Audit", icon: "audit" }
];

export default function Sidebar({
  activePage,
  onNavigate,
  tenders,
  selectedTenderId,
  onTenderChange,
  pendingReviews
}) {
  return (
    <aside className="sidebar">
      <div className="brand">
        <div className="brand-mark">TL</div>
        <div>
          <strong>TenderLens</strong>
          <span>Officer Console</span>
        </div>
      </div>

      <label className="tender-selector">
        <span>Active tender</span>
        <select value={selectedTenderId || ""} onChange={(event) => onTenderChange(event.target.value)}>
          <option value="">Select tender</option>
          {tenders.map((tender) => (
            <option key={tender.id} value={tender.id}>
              {tender.tenderRef} - {tender.title}
            </option>
          ))}
        </select>
      </label>

      {selectedTenderId ? (
        <div className="selected-summary">
          {tenders
            .filter((tender) => String(tender.id) === String(selectedTenderId))
            .map((tender) => (
              <div key={tender.id}>
                <strong>{tender.tenderRef}</strong>
                <p>{tender.title}</p>
                <StatusBadge status={tender.status} compact />
              </div>
            ))}
        </div>
      ) : null}

      <nav className="nav">
        {navItems.map((item) => (
          <button
            key={item.id}
            className={activePage === item.id ? "active" : ""}
            type="button"
            onClick={() => onNavigate(item.id)}
            title={item.label}
          >
            <Icon name={item.icon} />
            <span>{item.label}</span>
            {item.id === "reviews" && pendingReviews ? <em>{pendingReviews}</em> : null}
          </button>
        ))}
      </nav>
    </aside>
  );
}
