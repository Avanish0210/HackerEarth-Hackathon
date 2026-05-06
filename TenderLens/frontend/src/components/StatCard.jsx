import Icon from "./Icons.jsx";

export default function StatCard({ icon, label, value, sublabel, tone = "neutral" }) {
  return (
    <article className={`stat-card tone-${tone}`}>
      <div className="stat-icon">
        <Icon name={icon} />
      </div>
      <div>
        <span>{label}</span>
        <strong>{value ?? 0}</strong>
        {sublabel ? <small>{sublabel}</small> : null}
      </div>
    </article>
  );
}
