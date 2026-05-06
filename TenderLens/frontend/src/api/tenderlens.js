const API_BASE =
  import.meta.env.VITE_TENDERLENS_API_BASE || "http://localhost:8081/api/tenderlens";

async function request(path, options = {}) {
  const headers = {
    ...(options.headers || {})
  };
  if (options.body && !(options.body instanceof FormData)) {
    headers["Content-Type"] = "application/json";
  }

  let response;
  try {
    response = await fetch(`${API_BASE}${path}`, {
      credentials: "include",
      ...options,
      headers
    });
  } catch (error) {
    throw new Error(`Cannot reach backend at ${API_BASE}. Spring Boot answered in terminal checks, so if this appears in the browser, check CORS origin and restart the backend after config changes.`);
  }

  if (!response.ok) {
    let message = `${response.status} ${response.statusText}`;
    try {
      const body = await response.json();
      message = body.error || body.message || message;
    } catch {
      // Keep the HTTP status text when the backend returns no JSON body.
    }
    throw new Error(message);
  }

  if (response.status === 204) return null;
  const contentType = response.headers.get("content-type") || "";
  return contentType.includes("application/json") ? response.json() : response.blob();
}

function postForm(path, fields) {
  const formData = new FormData();
  Object.entries(fields).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") formData.append(key, value);
  });
  return request(path, { method: "POST", body: formData });
}

async function backendHealth() {
  const startedAt = performance.now();
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), 2500);
  const healthUrl = `${API_BASE}/actuator/health`;

  try {
    const response = await fetch(healthUrl, {
      credentials: "include",
      signal: controller.signal
    });
    const latencyMs = Math.round(performance.now() - startedAt);
    let statusText = response.ok ? "UP" : `HTTP ${response.status}`;
    try {
      const body = await response.json();
      statusText = body.status || statusText;
    } catch {
      // Actuator may be unavailable or return no body; the HTTP response still proves reachability.
    }
    return {
      reachable: true,
      healthy: response.ok,
      statusText,
      latencyMs,
      checkedAt: new Date()
    };
  } catch (error) {
    if (error.name !== "AbortError") {
      try {
        await fetch(healthUrl, {
          mode: "no-cors",
          signal: controller.signal
        });
        return {
          reachable: true,
          healthy: false,
          statusText: "CORS BLOCKED",
          latencyMs: Math.round(performance.now() - startedAt),
          checkedAt: new Date(),
          message: `Backend responded at ${API_BASE}, but the browser blocked the readable response. Check CORS origin.`
        };
      } catch {
        // Fall through to the offline result.
      }
    }
    return {
      reachable: false,
      healthy: false,
      statusText: error.name === "AbortError" ? "TIMEOUT" : "OFFLINE",
      latencyMs: null,
      checkedAt: new Date(),
      message: `No response from ${API_BASE}`
    };
  } finally {
    window.clearTimeout(timeout);
  }
}

export const tenderlensApi = {
  baseUrl: API_BASE,
  backendHealth,
  dashboardStats: () => request("/dashboard/stats"),
  auditFeed: (limit = 30) => request(`/dashboard/audit/feed?limit=${limit}`),
  sidebarTenders: () => request("/dashboard/tenders/list"),
  tenders: () => request("/tenders"),
  tender: (id) => request(`/tenders/${id}`),
  uploadTender: ({ file, tenderRef, title, uploadedBy, ocrRequired }) =>
    postForm("/tenders/upload", { file, tenderRef, title, uploadedBy, ocrRequired }),
  criteria: (id) => request(`/tenders/${id}/criteria`),
  confirmCriteria: (id, payload) =>
    request(`/tenders/${id}/criteria/confirm`, {
      method: "POST",
      body: JSON.stringify(payload)
    }),
  bidders: (id) => request(`/tenders/${id}/bidders`),
  uploadBidder: (id, { file, bidderRef, companyName, ocrRequired }) =>
    postForm(`/tenders/${id}/bidders/upload`, { file, bidderRef, companyName, ocrRequired }),
  triggerEvaluation: (id, requestedBy = "OFFICER") =>
    request(`/tenders/${id}/evaluate?requestedBy=${encodeURIComponent(requestedBy)}`, {
      method: "POST"
    }),
  evaluationSummary: (id) => request(`/tenders/${id}/evaluation/summary`),
  evaluationBidder: (id, bidderId) => request(`/tenders/${id}/evaluation/bidder/${bidderId}`),
  evaluationMatrix: (id) => request(`/tenders/${id}/evaluation/matrix`),
  reviewStats: () => request("/review/stats"),
  pendingReviews: (id) => request(id ? `/review/pending/tender/${id}` : "/review/pending"),
  reviewDetail: (reviewId) => request(`/review/${reviewId}`),
  resolveReview: (payload) =>
    request("/review/resolve", {
      method: "POST",
      body: JSON.stringify(payload)
    }),
  generateReport: (id, generatedBy = "OFFICER") =>
    request(`/tenders/${id}/report/generate?generatedBy=${encodeURIComponent(generatedBy)}`, {
      method: "POST"
    }),
  reportDownloadUrl: (id) => `${API_BASE}/tenders/${id}/report/download`,
  auditLog: (id) => request(`/tenders/${id}/audit`)
};
