const labels = {
  ELIGIBLE: "Eligible",
  NOT_ELIGIBLE: "Not eligible",
  NEEDS_REVIEW: "Needs review",
  PENDING: "Pending",
  PARSING: "Parsing",
  PARSED: "Parsed",
  FAILED: "Failed",
  IN_PROGRESS: "In progress",
  UPLOADED: "Uploaded",
  CRITERIA_EXTRACTED: "Criteria extracted",
  CRITERIA_CONFIRMED: "Criteria confirmed",
  EVALUATION_RUNNING: "Evaluating",
  COMPLETED: "Completed"
};

export default function StatusBadge({ status, compact = false }) {
  const normalized = status || "PENDING";
  return (
    <span className={`status-badge status-${normalized.toLowerCase()} ${compact ? "compact" : ""}`}>
      <span className="status-dot" />
      {labels[normalized] || normalized.replaceAll("_", " ")}
    </span>
  );
}
