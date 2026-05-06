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

    @GetMapping("/pending")
    public ResponseEntity<?> getAllPending() {
        List<ReviewQueue> pending = reviewQueueRepository.findByReviewedFalseOrderByCreatedAtDesc();
        return ResponseEntity.ok(Map.of(
                "totalPending", pending.size(),
                "items", pending.stream().map(this::toSummary).toList()
        ));
    }

    @GetMapping("/pending/tender/{tenderId}")
    public ResponseEntity<?> getPendingByTender(@PathVariable Long tenderId) {
        List<ReviewQueue> pending = reviewQueueRepository.findPendingByTenderId(tenderId);
        return ResponseEntity.ok(Map.of(
                "tenderId", tenderId,
                "totalPending", pending.size(),
                "items", pending.stream().map(this::toSummary).toList()
        ));
    }

    @GetMapping("/{reviewId}")
    public ResponseEntity<?> getReviewDetail(@PathVariable Long reviewId) {
        ReviewQueue item = reviewQueueRepository.findById(reviewId).orElse(null);
        if (item == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(toDetail(item));
    }

    @PostMapping("/resolve")
    public ResponseEntity<?> resolveReview(@Valid @RequestBody ReviewResolutionRequest request) {
        try {
            ReviewQueue resolved = reviewQueueService.resolve(request);
            return ResponseEntity.ok(Map.of(
                    "reviewId",        resolved.getId(),
                    "reviewed",        resolved.isReviewed(),
                    "overrideVerdict", resolved.getOverrideVerdict(),
                    "reviewer",        resolved.getReviewer(),
                    "reviewedAt",      resolved.getReviewedAt() != null ? resolved.getReviewedAt().toString() : "",
                    "message",         "Review resolved. Evaluation updated with officer verdict."
            ));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        long totalPending = reviewQueueRepository.countByReviewedFalse();
        return ResponseEntity.ok(Map.of("totalPendingReviews", totalPending));
    }

    // ── Builders — ReviewQueue.evaluation is the nested Evaluation object ─────

    private Map<String, Object> toSummary(ReviewQueue item) {
        Evaluation eval = item.getEvaluation();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("reviewId",             item.getId());
        map.put("reason",               item.getReason());
        map.put("reviewed",             item.isReviewed());
        map.put("createdAt",            item.getCreatedAt());
        map.put("tenderId",             eval.getTender().getId());
        map.put("tenderRef",            eval.getTender().getTenderRef());
        map.put("bidderId",             eval.getBidder().getId());
        map.put("bidderRef",            eval.getBidder().getBidderRef());
        map.put("companyName",          eval.getBidder().getCompanyName());
        map.put("criterionRef",         eval.getCriterion().getCriterionRef());
        map.put("criterionDescription", eval.getCriterion().getDescription());
        map.put("mandatory",            eval.getCriterion().isMandatory());
        map.put("confidenceScore",      eval.getConfidenceScore() != null ? eval.getConfidenceScore() : 0);
        return map;
    }

    private Map<String, Object> toDetail(ReviewQueue item) {
        Evaluation eval = item.getEvaluation();

        Map<String, Object> evalMap = new LinkedHashMap<>();
        evalMap.put("evaluationId",         eval.getId());
        evalMap.put("tenderId",             eval.getTender().getId());
        evalMap.put("tenderRef",            eval.getTender().getTenderRef());
        evalMap.put("bidderId",             eval.getBidder().getId());
        evalMap.put("bidderRef",            eval.getBidder().getBidderRef());
        evalMap.put("companyName",          eval.getBidder().getCompanyName());
        evalMap.put("criterionRef",         eval.getCriterion().getCriterionRef());
        evalMap.put("criterionDescription", eval.getCriterion().getDescription());
        evalMap.put("criterionType",        eval.getCriterion().getCriterionType());
        evalMap.put("mandatory",            eval.getCriterion().isMandatory());
        evalMap.put("thresholdValue",       eval.getCriterion().getThresholdValue() != null ? eval.getCriterion().getThresholdValue() : "");
        evalMap.put("thresholdOperator",    eval.getCriterion().getThresholdOperator() != null ? eval.getCriterion().getThresholdOperator() : "");
        evalMap.put("extractedValue",       eval.getExtractedValue() != null ? eval.getExtractedValue() : "");
        evalMap.put("verbatimExcerpt",      eval.getVerbatimExcerpt() != null ? eval.getVerbatimExcerpt() : "");
        evalMap.put("sourcePage",           eval.getSourcePage() != null ? eval.getSourcePage() : 0);
        evalMap.put("confidenceScore",      eval.getConfidenceScore() != null ? eval.getConfidenceScore() : 0);
        evalMap.put("currentVerdict",       eval.getVerdict() != null ? eval.getVerdict() : "");
        evalMap.put("officerOverride",      eval.isOfficerOverride());

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("reviewId",        item.getId());
        map.put("reason",          item.getReason());
        map.put("reviewed",        item.isReviewed());
        map.put("createdAt",       item.getCreatedAt());
        map.put("reviewedAt",      item.getReviewedAt() != null ? item.getReviewedAt().toString() : "");
        map.put("overrideVerdict", item.getOverrideVerdict() != null ? item.getOverrideVerdict() : "");
        map.put("reviewer",        item.getReviewer() != null ? item.getReviewer() : "");
        map.put("reviewNote",      item.getReviewNote() != null ? item.getReviewNote() : "");
        map.put("evaluation",      evalMap);
        return map;
    }
}