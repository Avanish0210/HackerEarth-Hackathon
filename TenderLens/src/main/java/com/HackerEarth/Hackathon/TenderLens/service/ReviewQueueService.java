package com.HackerEarth.Hackathon.TenderLens.service;

import com.HackerEarth.Hackathon.TenderLens.dto.EvaluationResult;
import com.HackerEarth.Hackathon.TenderLens.dto.ReviewResolutionRequest;
import com.HackerEarth.Hackathon.TenderLens.entity.*;
import com.HackerEarth.Hackathon.TenderLens.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Manages the officer review queue for low-confidence / ambiguous evaluations.
 *
 * Flow:
 *   1. MatchingEngine produces NEEDS_REVIEW verdicts
 *   2. This service creates ReviewQueue entries for each
 *   3. Officer views pending items via ReviewController
 *   4. Officer submits override verdict → this service updates Evaluation + closes ReviewQueue entry
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewQueueService {

    private final ReviewQueueRepository reviewQueueRepository;
    private final EvaluationRepository evaluationRepository;
    private final TenderRepository tenderRepository;
    private final BidderRepository bidderRepository;
    private final CriterionRepository criterionRepository;
    private final TenderAuditService auditService;

    // ── Route to review ───────────────────────────────────────────────────────

    @Transactional
    public void routeToReview(
            Tender tender,
            Bidder bidder,
            Criterion criterion,
            EvaluationResult evalResult) {

        Evaluation evaluation = evaluationRepository
                .findByTenderIdAndBidderIdAndCriterionId(tender.getId(), bidder.getId(), criterion.getId())
                .orElse(null);

        if (evaluation == null) {
            log.warn("Cannot route to review: evaluation not found for tender={}, bidder={}, criterion={}",
                    tender.getId(), bidder.getId(), criterion.getId());
            return;
        }

        // Avoid duplicate review entries
        boolean alreadyQueued = reviewQueueRepository.existsByEvaluationId(evaluation.getId());

        if (alreadyQueued) {
            log.debug("Already queued for review: tender={}, bidder={}, criterion={}",
                    tender.getId(), bidder.getId(), criterion.getId());
            return;
        }

        ReviewQueue entry = ReviewQueue.builder()
                .evaluation(evaluation)
                .reason(evalResult.getReviewReason() != null ? evalResult.getReviewReason() : "LOW_CONFIDENCE")
                .build();

        reviewQueueRepository.save(entry);

        log.info("Routed to review: tender={}, bidder={}, criterion={}, reason={}",
                tender.getTenderRef(), bidder.getBidderRef(),
                criterion.getCriterionRef(), entry.getReason());
    }

    @Transactional
    public void routeAllNeedsReview(
            Tender tender,
            Bidder bidder,
            List<Criterion> criteria,
            List<EvaluationResult> evalResults) {

        for (EvaluationResult result : evalResults) {
            if ("NEEDS_REVIEW".equals(result.getVerdict())) {
                Criterion criterion = criteria.stream()
                        .filter(c -> c.getId().equals(result.getCriterionId()))
                        .findFirst()
                        .orElse(null);

                if (criterion != null) {
                    routeToReview(tender, bidder, criterion, result);
                    auditService.recordReviewRouted(tender, bidder, result.getReviewReason());
                }
            }
        }
    }

    // ── Officer resolves a review item ────────────────────────────────────────

    @Transactional
    public ReviewQueue resolve(ReviewResolutionRequest request) {
        ReviewQueue entry = reviewQueueRepository.findById(request.getReviewId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Review entry not found: " + request.getReviewId()));

        if (entry.isReviewed()) {
            throw new IllegalStateException("Review entry already resolved: " + request.getReviewId());
        }

        Evaluation evaluation = entry.getEvaluation();

        evaluation.setVerdict(request.getOverrideVerdict());
        evaluation.setOfficerOverride(true);
        evaluation.setReviewNote(request.getReviewNote());
        evaluation.setReviewedBy(request.getReviewer());
        evaluation.setReviewedAt(OffsetDateTime.now());
        evaluationRepository.save(evaluation);

        // Close the review entry
        entry.setReviewed(true);
        entry.setOverrideVerdict(request.getOverrideVerdict());
        entry.setReviewer(request.getReviewer());
        entry.setReviewNote(request.getReviewNote());
        entry.setReviewedAt(OffsetDateTime.now());
        reviewQueueRepository.save(entry);

        // Audit
        auditService.recordReviewResolved(
                evaluation.getTender(),
                evaluation.getBidder().getId(),
                request.getReviewer(),
                request.getOverrideVerdict());

        log.info("Review resolved: id={}, verdict={}, officer={}",
                request.getReviewId(), request.getOverrideVerdict(), request.getReviewer());

        return entry;
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public List<ReviewQueue> getPendingByTender(Long tenderId) {
        return reviewQueueRepository.findPendingByTenderId(tenderId);
    }

    public List<ReviewQueue> getAllPending() {
        return reviewQueueRepository.findByReviewedFalseOrderByCreatedAtDesc();
    }

    public long countPending(Long tenderId) {
        return reviewQueueRepository.findPendingByTenderId(tenderId).size();
    }

    public boolean hasPendingReviews(Long tenderId) {
        return !reviewQueueRepository.findPendingByTenderId(tenderId).isEmpty();
    }
}