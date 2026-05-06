export default function ConfidenceBar({ value = 0 }) {
  const numeric = Number(value || 0);
  const pct = Math.round(Math.max(0, Math.min(numeric, 1)) * 100);
  return (
    <div className="confidence">
      <div className="confidence-track">
        <span style={{ width: `${pct}%` }} />
      </div>
      <strong>{pct}%</strong>
    </div>
  );
}
