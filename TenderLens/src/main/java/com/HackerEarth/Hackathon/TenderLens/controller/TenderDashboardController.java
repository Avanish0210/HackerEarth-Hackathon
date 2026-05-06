package com.HackerEarth.Hackathon.TenderLens.controller;

import com.HackerEarth.Hackathon.TenderLens.entity.Tender;
import com.HackerEarth.Hackathon.TenderLens.entity.TenderAuditLog;
import com.HackerEarth.Hackathon.TenderLens.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * TenderDashboardController — stats and live feed for the React frontend dashboard.
 *
 *  GET /dashboard/stats         → summary counts (tenders, bidders, evals, review queue)
 *  GET /dashboard/audit/feed    → latest audit events across all tenders (live feed)
 *  GET /dashboard/tenders/list  → compact list of all tenders for sidebar
 */
@Slf4j
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class TenderDashboardController {

    private final TenderRepository tenderRepository;
    private final BidderRepository bidderRepository;
    private final EvaluationRepository evaluationRepository;
    private final ReviewQueueRepository reviewQueueRepository;
    private final TenderAuditLogRepository auditLogRepository;
    private final CriterionRepository criterionRepository;

    // ── Summary stats ─────────────────────────────────────────────────────────

    /**
     * Top-level stats for the React dashboard cards.
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        List<Tender> allTenders = tenderRepository.findAllByOrderByCreatedAtDesc();

        long totalTenders     = allTenders.size();
        long activeTenders    = allTenders.stream()
                .filter(t -> "EVALUATION_RUNNING".equals(t.getStatus())
                        || "CRITERIA_CONFIRMED".equals(t.getStatus()))
                .count();
        long completedTenders = allTenders.stream()
                .filter(t -> "COMPLETED".equals(t.getStatus()))
                .count();

        long totalBidders     = bidderRepository.count();
        long parsedBidders    = bidderRepository.findAll().stream()
                .filter(b -> "PARSED".equals(b.getParseStatus())).count();
        long pendingBidders   = bidderRepository.findAll().stream()
                .filter(b -> "PENDING".equals(b.getParseStatus())
                        || "PARSING".equals(b.getParseStatus())).count();

        long totalEvaluations = evaluationRepository.count();
        long eligibleCount    = evaluationRepository.findAll().stream()
                .filter(e -> "ELIGIBLE".equals(e.getVerdict())).count();
        long notEligibleCount = evaluationRepository.findAll().stream()
                .filter(e -> "NOT_ELIGIBLE".equals(e.getVerdict())).count();
        long needsReviewCount = evaluationRepository.findAll().stream()
                .filter(e -> "NEEDS_REVIEW".equals(e.getVerdict())).count();

        long pendingReviews   = reviewQueueRepository.countByReviewedFalse();

        return ResponseEntity.ok(Map.of(
                "tenders", Map.of(
                        "total", totalTenders,
                        "active", activeTenders,
                        "completed", completedTenders
                ),
                "bidders", Map.of(
                        "total", totalBidders,
                        "parsed", parsedBidders,
                        "pending", pendingBidders
                ),
                "evaluations", Map.of(
                        "total", totalEvaluations,
                        "eligible", eligibleCount,
                        "notEligible", notEligibleCount,
                        "needsReview", needsReviewCount
                ),
                "reviewQueue", Map.of(
                        "pending", pendingReviews
                )
        ));
    }

    // ── Audit event feed ──────────────────────────────────────────────────────

    /**
     * Latest audit events across all tenders — used for the live feed panel
     * in the React dashboard. Returns most recent 50 events.
     */
    @GetMapping("/audit/feed")
    public ResponseEntity<?> getAuditFeed(
            @RequestParam(value = "limit", defaultValue = "50") int limit) {

        // Fetch all audit logs, sorted desc by createdAt, limit client-side
        List<TenderAuditLog> allLogs = auditLogRepository
                .findByActionOrderByCreatedAtDesc("CRITERION_EXTRACTED");

        // For a proper feed, we just get all and sort — no pagination needed for demo
        List<TenderAuditLog> feed = auditLogRepository.findAll()
                .stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(Math.min(limit, 100))
                .toList();

        return ResponseEntity.ok(Map.of(
                "count", feed.size(),
                "events", feed.stream().map(this::toFeedEntry).toList()
        ));
    }

    // ── Compact tender list for sidebar ──────────────────────────────────────

    /**
     * Compact list of all tenders with status — for the React sidebar/dropdown.
     */
    @GetMapping("/tenders/list")
    public ResponseEntity<?> getTenderList() {
        List<Tender> tenders = tenderRepository.findAllByOrderByCreatedAtDesc();

        List<Map<String, Object>> list = tenders.stream().map(t -> {
            long bidderCount   = bidderRepository.countByTenderId(t.getId());
            long criteriaCount = criterionRepository.countByTenderId(t.getId());
            long pendingReview = reviewQueueRepository.findPendingByTenderId(t.getId()).size();

            return Map.<String, Object>of(
                    "id", t.getId(),
                    "tenderRef", t.getTenderRef(),
                    "title", t.getTitle(),
                    "status", t.getStatus(),
                    "uploadedBy", t.getUploadedBy(),
                    "createdAt", t.getCreatedAt(),
                    "bidderCount", bidderCount,
                    "criteriaCount", criteriaCount,
                    "pendingReviews", pendingReview
            );
        }).toList();

        return ResponseEntity.ok(Map.of(
                "total", tenders.size(),
                "tenders", list
        ));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private Map<String, Object> toFeedEntry(TenderAuditLog log) {
        return Map.of(
                "id", log.getId(),
                "tenderId", log.getTenderId(),
                "action", log.getAction(),
                "detail", log.getDetail() != null ? log.getDetail() : "",
                "performedBy", log.getPerformedBy() != null ? log.getPerformedBy() : "SYSTEM",
                "createdAt", log.getCreatedAt()
        );
    }
}
