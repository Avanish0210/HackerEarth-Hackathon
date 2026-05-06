package com.HackerEarth.Hackathon.TenderLens.controller;

import com.HackerEarth.Hackathon.TenderLens.dto.ReviewResolutionRequest;
import com.HackerEarth.Hackathon.TenderLens.entity.Evaluation;
import com.HackerEarth.Hackathon.TenderLens.entity.ReviewQueue;
import com.HackerEarth.Hackathon.TenderLens.repository.ReviewQueueRepository;
import com.HackerEarth.Hackathon.TenderLens.service.ReviewQueueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ReviewController — officer human-review queue for low-confidence evaluations.
 *
 * These are items the MatchingEngine could not confidently decide:
 *   - confidence below threshold (0.75 default)
 *   - OCR quality flagged as poor
 *   - Evidence not found (optional criteria)
 *   - Ambiguous value that couldn't be parsed numerically
 *
 * The officer is shown the verbatim excerpt + extracted value and makes the final call.
 *
 *  GET  /review/pending                 → all pending items across all tenders
 *  GET  /review/pending/tender/{id}     → pending items for one tender
 *  GET  /review/{reviewId}              → detail of one review item
 *  POST /review/resolve                 → officer submits verdict override
 *  GET  /review/stats                   → count of pending items
 */
@Slf4j
@RestController
@RequestMapping("/review")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewQueueService reviewQueueService;
    private final ReviewQueueRepository reviewQueueRepository;

    // ── All pending items ─────────────────────────────────────────────────────

    /**
     * Returns all pending review items across all tenders.
     * Used for the officer's global review dashboard.
     */
    @GetMapping("/pending")
    public ResponseEntity<?> getAllPending() {
        List<ReviewQueue> pending = reviewQueueService.getAllPending();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalPending", pending.size());
        response.put("items", pending.stream().map(this::toSummary).toList());
        return ResponseEntity.ok(response);
    }

    // ── Pending items for one tender ──────────────────────────────────────────

    @GetMapping("/pending/tender/{tenderId}")
    public ResponseEntity<?> getPendingByTender(@PathVariable Long tenderId) {
        List<ReviewQueue> pending = reviewQueueService.getPendingByTender(tenderId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("tenderId", tenderId);
        response.put("totalPending", pending.size());
        response.put("items", pending.stream().map(this::toSummary).toList());
        return ResponseEntity.ok(response);
    }

    // ── Detail of one review item ─────────────────────────────────────────────

    /**
     * Returns full detail — the verbatim excerpt, extracted value, criterion
     * threshold and description — everything the officer needs to make a decision.
     */
    @GetMapping("/{reviewId}")
    public ResponseEntity<?> getReviewDetail(@PathVariable Long reviewId) {
        ReviewQueue item = reviewQueueRepository.findById(reviewId).orElse(null);
        if (item == null) return ResponseEntity.notFound().build();

        return ResponseEntity.ok(toDetail(item));
    }

    // ── Officer resolves a review item ────────────────────────────────────────

    /**
     * Officer submits their verdict override: ELIGIBLE or NOT_ELIGIBLE.
     * This:
     *   1. Updates the Evaluation record with officer's verdict + note
     *   2. Marks ReviewQueue entry as reviewed
     *   3. Writes audit log
     *
     * Required fields: reviewId, overrideVerdict (ELIGIBLE|NOT_ELIGIBLE),
     *                  reviewer (officer ID), reviewNote (optional)
     */
    @PostMapping("/resolve")
    public ResponseEntity<?> resolveReview(@Valid @RequestBody ReviewResolutionRequest request) {
        try {
            ReviewQueue resolved = reviewQueueService.resolve(request);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("reviewId", resolved.getId());
            response.put("resolved", true);
            response.put("overrideVerdict", resolved.getOverrideVerdict());
            response.put("reviewer", resolved.getReviewer());
            response.put("reviewedAt", resolved.getReviewedAt());
            response.put("message", "Review resolved. Evaluation updated with officer verdict.");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        long totalPending = reviewQueueRepository.countByReviewedFalse();
        return ResponseEntity.ok(Map.of(
                "totalPendingReviews", totalPending
        ));
    }

    // ── Response builders ─────────────────────────────────────────────────────

    /**
     * Summary view — just enough for the review queue list.
     */
    private Map<String, Object> toSummary(ReviewQueue item) {
        Evaluation eval = item.getEvaluation();

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("reviewId", item.getId());
        map.put("reason", item.getReason());
        map.put("reviewed", item.isReviewed());
        map.put("createdAt", item.getCreatedAt());
        map.put("tenderId", eval.getTender().getId());
        map.put("tenderRef", eval.getTender().getTenderRef());
        map.put("bidderId", eval.getBidder().getId());
        map.put("bidderRef", eval.getBidder().getBidderRef());
        map.put("companyName", eval.getBidder().getCompanyName());
        map.put("criterionRef", eval.getCriterion().getCriterionRef());
        map.put("criterionDescription", eval.getCriterion().getDescription());
        map.put("mandatory", eval.getCriterion().isMandatory());
        return map;
    }

    /**
     * Full detail view — everything the officer needs to make a decision.
     */
    private Map<String, Object> toDetail(ReviewQueue item) {
        Evaluation eval = item.getEvaluation();

        // Inner evaluation map — 18 fields, LinkedHashMap required
        Map<String, Object> evalMap = new LinkedHashMap<>();
        evalMap.put("evaluationId", eval.getId());
        evalMap.put("tenderId", eval.getTender().getId());
        evalMap.put("tenderRef", eval.getTender().getTenderRef());
        evalMap.put("bidderId", eval.getBidder().getId());
        evalMap.put("bidderRef", eval.getBidder().getBidderRef());
        evalMap.put("companyName", eval.getBidder().getCompanyName());
        evalMap.put("criterionRef", eval.getCriterion().getCriterionRef());
        evalMap.put("criterionDescription", eval.getCriterion().getDescription());
        evalMap.put("criterionType", eval.getCriterion().getCriterionType());
        evalMap.put("mandatory", eval.getCriterion().isMandatory());
        evalMap.put("thresholdValue", eval.getCriterion().getThresholdValue() != null
                ? eval.getCriterion().getThresholdValue() : "");
        evalMap.put("thresholdOperator", eval.getCriterion().getThresholdOperator() != null
                ? eval.getCriterion().getThresholdOperator() : "");
        evalMap.put("extractedValue", eval.getExtractedValue() != null ? eval.getExtractedValue() : "");
        evalMap.put("verbatimExcerpt", eval.getVerbatimExcerpt() != null ? eval.getVerbatimExcerpt() : "");
        evalMap.put("sourcePage", eval.getSourcePage() != null ? eval.getSourcePage() : 0);
        evalMap.put("confidenceScore", eval.getConfidenceScore() != null ? eval.getConfidenceScore() : 0);
        evalMap.put("currentVerdict", eval.getVerdict() != null ? eval.getVerdict() : "");
        evalMap.put("officerOverride", eval.isOfficerOverride());

        // Outer map — 9 fields, LinkedHashMap required
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("reviewId", item.getId());
        map.put("reason", item.getReason());
        map.put("reviewed", item.isReviewed());
        map.put("overrideVerdict", item.getOverrideVerdict() != null ? item.getOverrideVerdict() : "");
        map.put("reviewNote", item.getReviewNote() != null ? item.getReviewNote() : "");
        map.put("reviewer", item.getReviewer() != null ? item.getReviewer() : "");
        map.put("createdAt", item.getCreatedAt());
        map.put("reviewedAt", item.getReviewedAt() != null ? item.getReviewedAt().toString() : "");
        map.put("evaluation", evalMap);
        return map;
    }
}