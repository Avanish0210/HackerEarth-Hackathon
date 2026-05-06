import { useEffect, useState } from "react";
import { tenderlensApi } from "../api/tenderlens.js";

export default function BackendStatusBar() {
  const [status, setStatus] = useState({
    reachable: false,
    healthy: false,
    statusText: "CHECKING",
    latencyMs: null,
    checkedAt: null
  });

  useEffect(() => {
    let alive = true;

    async function check() {
      const next = await tenderlensApi.backendHealth();
      if (alive) setStatus(next);
    }

    check();
    const timer = window.setInterval(check, 3000);
    return () => {
      alive = false;
      window.clearInterval(timer);
    };
  }, []);

  const tone = status.healthy ? "online" : status.reachable ? "degraded" : "offline";
  const checked = status.checkedAt ? status.checkedAt.toLocaleTimeString() : "checking";

  return (
    <div className={`backend-status ${tone}`} role="status" aria-live="polite">
      <div>
        <span className="backend-pulse" />
        <strong>Backend {status.healthy ? "running" : status.reachable ? "reachable" : "not reachable"}</strong>
      </div>
      <span>{tenderlensApi.baseUrl}</span>
      <span>Status: {status.statusText}</span>
      <span>{status.latencyMs !== null ? `${status.latencyMs} ms` : "No response"}</span>
      <span>Checked {checked}</span>
    </div>
  );
}
