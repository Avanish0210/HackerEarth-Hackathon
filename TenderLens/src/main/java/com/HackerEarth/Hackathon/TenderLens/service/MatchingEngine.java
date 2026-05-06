package com.HackerEarth.Hackathon.TenderLens.service;

import com.HackerEarth.Hackathon.TenderLens.config.TenderLensProperties;
import com.HackerEarth.Hackathon.TenderLens.dto.EvaluationResult;
import com.HackerEarth.Hackathon.TenderLens.entity.Bidder;
import com.HackerEarth.Hackathon.TenderLens.entity.Criterion;
import com.HackerEarth.Hackathon.TenderLens.entity.Evaluation;
import com.HackerEarth.Hackathon.TenderLens.entity.Tender;
import com.HackerEarth.Hackathon.TenderLens.repository.EvaluationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Core evaluation engine — decides ELIGIBLE / NOT_ELIGIBLE / NEEDS_REVIEW
 * for each (bidder, criterion) pair.
 *
 * Strategy:
 *   1. If evidence not found → NOT_ELIGIBLE (mandatory) or NEEDS_REVIEW (optional)
 *   2. If FINANCIAL/numeric → deterministic comparison (GTE, LTE, EQ)
 *   3. If TECHNICAL/COMPLIANCE/CERTIFICATION → LLM fuzzy match already done via confidence
 *   4. If confidence < threshold → NEEDS_REVIEW regardless of verdict
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingEngine {

    private final EvaluationRepository evaluationRepository;
    private final TenderLensProperties props;

    // Regex to extract leading numeric value from strings like "7.2 crore", "5000000", "3 years"
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("([\\d,]+\\.?\\d*)");

    // Crore/lakh multipliers for Indian procurement documents
    private static final double CRORE = 1_00_00_000.0;
    private static final double LAKH  = 1_00_000.0;

    @Transactional
    public List<EvaluationResult> evaluate(
            Tender tender,
            Bidder bidder,
            List<Criterion> criteria,
            List<BidderParserService.EvidenceResult> evidenceResults) {

        List<EvaluationResult> results = new ArrayList<>();

        for (Criterion criterion : criteria) {
            BidderParserService.EvidenceResult evidence = findEvidence(evidenceResults, criterion.getId());
            EvaluationResult result = evaluateOneCriterion(tender, bidder, criterion, evidence);
            results.add(result);

            // Persist to DB
            persistEvaluation(tender, bidder, criterion, result);
        }

        log.info("Evaluation complete for bidder {}: {}/{} eligible",
                bidder.getBidderRef(),
                results.stream().filter(r -> "ELIGIBLE".equals(r.getVerdict())).count(),
                criteria.size());

        return results;
    }

    // ── Per-criterion evaluation ──────────────────────────────────────────────

    private EvaluationResult evaluateOneCriterion(
            Tender tender,
            Bidder bidder,
            Criterion criterion,
            BidderParserService.EvidenceResult evidence) {

        double confidenceThreshold = props.getLlm().getConfidenceThreshold();

        // Case 1: Evidence not found at all
        if (evidence == null || !evidence.isFound()) {
            String verdict = criterion.isMandatory() ? "NOT_ELIGIBLE" : "NEEDS_REVIEW";
            String reviewReason = "NOT_FOUND";
            return buildResult(criterion, evidence, verdict,
                    BigDecimal.ZERO, false, reviewReason);
        }

        // Case 2: Low confidence extraction → always route to review
        if (evidence.getConfidence() < confidenceThreshold) {
            return buildResult(criterion, evidence, "NEEDS_REVIEW",
                    toBigDecimal(evidence.getConfidence()), false, "LOW_CONFIDENCE");
        }

        // Case 3: Deterministic numeric match for FINANCIAL criteria
        if ("FINANCIAL".equals(criterion.getCriterionType()) && criterion.getThresholdValue() != null) {
            return evaluateNumeric(criterion, evidence);
        }

        // Case 4: Fuzzy match for TECHNICAL / COMPLIANCE / CERTIFICATION
        // The LLM already extracted the value and gave a confidence score.
        // If confidence >= threshold, trust the LLM's "found" signal.
        return evaluateFuzzy(criterion, evidence, confidenceThreshold);
    }

    // ── Numeric evaluation ────────────────────────────────────────────────────

    private EvaluationResult evaluateNumeric(
            Criterion criterion,
            BidderParserService.EvidenceResult evidence) {

        try {
            double threshold = parseNumericValue(criterion.getThresholdValue());
            double extracted = parseNumericValue(evidence.getExtractedValue());

            boolean passes = switch (criterion.getThresholdOperator()) {
                case "GTE" -> extracted >= threshold;
                case "LTE" -> extracted <= threshold;
                case "EQ"  -> Math.abs(extracted - threshold) < 0.01;
                default    -> true; // Unknown operator → don't penalize
            };

            String verdict = passes ? "ELIGIBLE" : "NOT_ELIGIBLE";
            BigDecimal confidence = toBigDecimal(evidence.getConfidence());

            // Even if numeric match passes, low confidence means manual review
            if (evidence.getConfidence() < props.getLlm().getConfidenceThreshold()) {
                return buildResult(criterion, evidence, "NEEDS_REVIEW", confidence, false, "LOW_CONFIDENCE");
            }

            return buildResult(criterion, evidence, verdict, confidence, false, null);

        } catch (NumberFormatException ex) {
            log.warn("Could not parse numeric value for criterion {}: extracted='{}', threshold='{}'",
                    criterion.getCriterionRef(), evidence.getExtractedValue(), criterion.getThresholdValue());
            // Cannot determine → route to review
            return buildResult(criterion, evidence, "NEEDS_REVIEW",
                    toBigDecimal(evidence.getConfidence()), false, "AMBIGUOUS_VALUE");
        }
    }

    // ── Fuzzy evaluation ──────────────────────────────────────────────────────

    private EvaluationResult evaluateFuzzy(
            Criterion criterion,
            BidderParserService.EvidenceResult evidence,
            double confidenceThreshold) {

        // For CONTAINS operator — check if extracted value contains threshold text
        if ("CONTAINS".equals(criterion.getThresholdOperator()) && criterion.getThresholdValue() != null) {
            boolean contains = evidence.getExtractedValue() != null &&
                    evidence.getExtractedValue().toLowerCase()
                            .contains(criterion.getThresholdValue().toLowerCase());

            if (!contains && evidence.getConfidence() >= confidenceThreshold) {
                return buildResult(criterion, evidence, "NOT_ELIGIBLE",
                        toBigDecimal(evidence.getConfidence()), false, null);
            }
        }

        // For EXISTS operator — finding the evidence is sufficient
        if ("EXISTS".equals(criterion.getThresholdOperator())) {
            return buildResult(criterion, evidence, "ELIGIBLE",
                    toBigDecimal(evidence.getConfidence()), false, null);
        }

        // Default: trust LLM found + confidence
        String verdict = evidence.getConfidence() >= confidenceThreshold ? "ELIGIBLE" : "NEEDS_REVIEW";
        String reviewReason = "ELIGIBLE".equals(verdict) ? null : "LOW_CONFIDENCE";

        return buildResult(criterion, evidence, verdict,
                toBigDecimal(evidence.getConfidence()), false, reviewReason);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private EvaluationResult buildResult(
            Criterion criterion,
            BidderParserService.EvidenceResult evidence,
            String verdict,
            BigDecimal confidence,
            boolean ocrQualityFlag,
            String reviewReason) {

        return EvaluationResult.builder()
                .criterionId(criterion.getId())
                .criterionRef(criterion.getCriterionRef())
                .criterionDescription(criterion.getDescription())
                .criterionType(criterion.getCriterionType())
                .mandatory(criterion.isMandatory())
                .thresholdValue(criterion.getThresholdValue())
                .extractedValue(evidence != null ? evidence.getExtractedValue() : null)
                .verbatimExcerpt(evidence != null ? evidence.getVerbatimExcerpt() : null)
                .sourcePage(evidence != null ? evidence.getSourcePage() : null)
                .verdict(verdict)
                .confidenceScore(confidence)
                .ocrQualityFlag(ocrQualityFlag)
                .reviewReason(reviewReason)
                .build();
    }

    private BidderParserService.EvidenceResult findEvidence(
            List<BidderParserService.EvidenceResult> evidenceResults,
            Long criterionId) {
        return evidenceResults.stream()
                .filter(e -> criterionId.equals(e.getCriterionId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Parses numeric value from strings like:
     *   "7.2 crore" → 72000000
     *   "50 lakh"   → 5000000
     *   "1,50,000"  → 150000
     *   "3 years"   → 3
     */
    private double parseNumericValue(String raw) {
        if (raw == null || raw.isBlank()) throw new NumberFormatException("null/blank input");

        String lower = raw.toLowerCase().trim();
        Matcher matcher = NUMERIC_PATTERN.matcher(lower);

        if (!matcher.find()) throw new NumberFormatException("No numeric value in: " + raw);

        String numStr = matcher.group(1).replace(",", "");
        double value = Double.parseDouble(numStr);

        // Apply Indian unit multipliers
        if (lower.contains("crore"))  value *= CRORE;
        else if (lower.contains("lakh")) value *= LAKH;

        return value;
    }

    private BigDecimal toBigDecimal(double d) {
        return BigDecimal.valueOf(d).setScale(3, RoundingMode.HALF_UP);
    }

    // ── Persist evaluation to DB ──────────────────────────────────────────────

    @Transactional
    private void persistEvaluation(
            Tender tender,
            Bidder bidder,
            Criterion criterion,
            EvaluationResult result) {

        // Upsert — re-evaluation should overwrite previous result
        evaluationRepository.findByTenderIdAndBidderIdAndCriterionId(
                        tender.getId(), bidder.getId(), criterion.getId())
                .ifPresentOrElse(
                        existing -> {
                            existing.setVerdict(result.getVerdict());
                            existing.setExtractedValue(result.getExtractedValue());
                            existing.setVerbatimExcerpt(result.getVerbatimExcerpt());
                            existing.setSourcePage(result.getSourcePage());
                            existing.setConfidenceScore(result.getConfidenceScore());
                            existing.setOcrQualityFlag(result.isOcrQualityFlag());
                            existing.setReviewReason(result.getReviewReason());
                            evaluationRepository.save(existing);
                        },
                        () -> {
                            Evaluation evaluation = Evaluation.builder()
                                    .tender(tender)
                                    .bidder(bidder)
                                    .criterion(criterion)
                                    .verdict(result.getVerdict())
                                    .extractedValue(result.getExtractedValue())
                                    .verbatimExcerpt(result.getVerbatimExcerpt())
                                    .sourcePage(result.getSourcePage())
                                    .confidenceScore(result.getConfidenceScore())
                                    .ocrQualityFlag(result.isOcrQualityFlag())
                                    .reviewReason(result.getReviewReason())
                                    .build();
                            evaluationRepository.save(evaluation);
                        }
                );
    }
}