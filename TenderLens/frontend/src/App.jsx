import { useCallback, useEffect, useMemo, useState } from "react";
import { tenderlensApi } from "./api/tenderlens.js";
import BackendStatusBar from "./components/BackendStatusBar.jsx";
import Sidebar from "./components/Sidebar.jsx";
import Dashboard from "./pages/Dashboard.jsx";
import TenderList from "./pages/TenderList.jsx";
import TenderDetail from "./pages/TenderDetail.jsx";
import CriteriaReview from "./pages/CriteriaReview.jsx";
import BidderUpload from "./pages/BidderUpload.jsx";
import EvaluationMatrix from "./pages/EvaluationMatrix.jsx";
import ReviewQueue from "./pages/ReviewQueue.jsx";
import Report from "./pages/Report.jsx";
import AuditTrail from "./pages/AuditTrail.jsx";

const pageTitles = {
  dashboard: "Dashboard",
  tenders: "Tender Management",
  criteria: "Criteria Review",
  bidders: "Bidder Upload",
  matrix: "Evaluation Matrix",
  reviews: "Review Queue",
  report: "Report",
  audit: "Audit Trail"
};

export default function App() {
  const [activePage, setActivePage] = useState("dashboard");
  const [selectedTenderId, setSelectedTenderId] = useState("");
  const [tenders, setTenders] = useState([]);
  const [pendingReviews, setPendingReviews] = useState(0);
  const [toast, setToast] = useState("");
  const [loadingShell, setLoadingShell] = useState(true);

  const selectedTender = useMemo(
    () => tenders.find((tender) => String(tender.id) === String(selectedTenderId)),
    [tenders, selectedTenderId]
  );

  const refreshShell = useCallback(async (preferredTenderId = "") => {
    const [tenderList, reviewStats] = await Promise.all([
      tenderlensApi.sidebarTenders(),
      tenderlensApi.reviewStats()
    ]);
    const list = tenderList.tenders || [];
    setTenders(list);
    setPendingReviews(reviewStats.totalPendingReviews || 0);
    setSelectedTenderId((current) => {
      const preferred = preferredTenderId ? String(preferredTenderId) : "";
      if (preferred && list.some((tender) => String(tender.id) === preferred)) return preferred;
      if (current && list.some((tender) => String(tender.id) === String(current))) return current;
      return list[0]?.id ? String(list[0].id) : "";
    });
    setLoadingShell(false);
  }, []);

  useEffect(() => {
    refreshShell().catch((error) => {
      setToast(error.message);
      setLoadingShell(false);
    });
    const timer = window.setInterval(() => {
      refreshShell().catch(() => {});
    }, 3000);
    return () => window.clearInterval(timer);
  }, [refreshShell]);

  const notify = useCallback((message) => {
    setToast(message);
    window.setTimeout(() => setToast(""), 3500);
  }, []);

  function requireTender(page) {
    if (!selectedTenderId && page !== "dashboard" && page !== "tenders") {
      const fallbackTender = tenders[0]?.id ? String(tenders[0].id) : "";
      if (fallbackTender) {
        setSelectedTenderId(fallbackTender);
        setActivePage(page);
      } else {
        notify("Select or upload a tender first.");
        setActivePage("tenders");
      }
      return;
    }
    setActivePage(page);
  }

  function selectTender(id, nextPage = activePage) {
    setSelectedTenderId(id ? String(id) : "");
    if (id && nextPage) setActivePage(nextPage);
  }

  const pageProps = {
    tenderId: selectedTenderId,
    selectedTender,
    tenders,
    refreshShell,
    notify,
    navigate: requireTender,
    selectedTenderId,
    selectTender
  };

  return (
    <div className="app-shell">
      <Sidebar
        activePage={activePage}
        onNavigate={requireTender}
        tenders={tenders}
        selectedTenderId={selectedTenderId}
        onTenderChange={(id) => {
          setSelectedTenderId(id);
          if (id && activePage === "dashboard") setActivePage("tenders");
        }}
        pendingReviews={pendingReviews}
      />

      <main className="workspace">
        <BackendStatusBar />
        <header className="topbar">
          <div>
            <span className="eyebrow">TenderLens React Frontend</span>
            <h1>{pageTitles[activePage]}</h1>
          </div>
          <div className="api-chip">
            <span />
            {tenderlensApi.baseUrl}
          </div>
        </header>

        {loadingShell ? <div className="empty-state">Connecting to TenderLens backend...</div> : null}
        {!loadingShell && activePage === "dashboard" ? <Dashboard {...pageProps} /> : null}
        {!loadingShell && activePage === "tenders" ? (
          <TenderList {...pageProps} />
        ) : null}
        {!loadingShell && activePage === "criteria" ? <CriteriaReview {...pageProps} /> : null}
        {!loadingShell && activePage === "bidders" ? <BidderUpload {...pageProps} /> : null}
        {!loadingShell && activePage === "matrix" ? <EvaluationMatrix {...pageProps} /> : null}
        {!loadingShell && activePage === "reviews" ? <ReviewQueue {...pageProps} /> : null}
        {!loadingShell && activePage === "report" ? <Report {...pageProps} /> : null}
        {!loadingShell && activePage === "audit" ? <AuditTrail {...pageProps} /> : null}
      </main>

      {toast ? <div className="toast">{toast}</div> : null}
    </div>
  );
}
