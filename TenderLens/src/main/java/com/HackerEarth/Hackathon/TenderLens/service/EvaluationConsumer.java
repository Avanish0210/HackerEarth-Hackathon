package com.HackerEarth.Hackathon.TenderLens.service;


import com.HackerEarth.Hackathon.TenderLens.dto.EvaluationJobMessage;
import com.HackerEarth.Hackathon.TenderLens.dto.EvaluationResult;
import com.HackerEarth.Hackathon.TenderLens.entity.Bidder;
import com.HackerEarth.Hackathon.TenderLens.entity.Criterion;
import com.HackerEarth.Hackathon.TenderLens.entity.Tender;
import com.HackerEarth.Hackathon.TenderLens.repository.BidderRepository;
import com.HackerEarth.Hackathon.TenderLens.repository.CriterionRepository;
import com.HackerEarth.Hackathon.TenderLens.repository.TenderRepository;
import com.HackerEarth.Hackathon.TenderLens.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Kafka consumer for the evaluation-jobs topic.
 *
 * For each EvaluationJobMessage (one per bidder), runs the full pipeline:
 *   1. Parse bidder document (PDFBox or Tesseract OCR)
 *   2. Extract evidence for each confirmed criterion (LLM call per criterion)
 *   3. Run MatchingEngine to produce verdicts
 *   4. Route NEEDS_REVIEW items to ReviewQueueService
 *   5. Write audit log
 *   6. Update bidder parseStatus → PARSED
 *
 * If a tender's all bidders are PARSED, status is set to COMPLETED.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationConsumer {

    private final TenderRepository tenderRepository;
    private final BidderRepository bidderRepository;
    private final CriterionRepository criterionRepository;
    private final DocumentParserService documentParserService;
    private final BidderParserService bidderParserService;
    private final MatchingEngine matchingEngine;
    private final ReviewQueueService reviewQueueService;
    private final TenderAuditService auditService;

    @KafkaListener(
            topics = "evaluation-jobs",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleEvaluationJob(EvaluationJobMessage message) {
        log.info("Received evaluation job: tender={}, bidder={}",
                message.getTenderRef(), message.getBidderRef());

        Tender tender = tenderRepository.findById(message.getTenderId()).orElse(null);
        Bidder bidder = bidderRepository.findById(message.getBidderId()).orElse(null);

        if (tender == null || bidder == null) {
            log.error("Tender or bidder not found: tenderId={}, bidderId={}",
                    message.getTenderId(), message.getBidderId());
            return;
        }

        // Mark as parsing
        bidder.setParseStatus("PARSING");
        bidderRepository.save(bidder);

        try {
            // Step 1 — Parse bidder document
            DocumentParserService.ParsedDocument parsed =
                    documentParserService.parse(message.getFilePath(), message.isOcrRequired());

            log.info("Document parsed for bidder {}: {} chars, ocrUsed={}",
                    bidder.getBidderRef(), parsed.getText().length(), parsed.isOcrUsed());

            // Step 2 — Get all confirmed criteria for this tender
            List<Criterion> criteria = criterionRepository
                    .findByTenderIdAndConfirmedByOfficerTrue(tender.getId());

            if (criteria.isEmpty()) {
                log.warn("No confirmed criteria found for tender {}. Skipping evaluation.",
                        tender.getTenderRef());
                bidder.setParseStatus("FAILED");
                bidderRepository.save(bidder);
                return;
            }

            // Step 3 — Extract evidence from bidder document for all criteria
            List<BidderParserService.EvidenceResult> evidenceResults =
                    bidderParserService.extractEvidenceForAllCriteria(
                            bidder, criteria, parsed.getText());

            // Step 4 — Run matching engine → produces verdicts + persists to DB
            List<EvaluationResult> evalResults =
                    matchingEngine.evaluate(tender, bidder, criteria, evidenceResults);

            // Step 5 — Route NEEDS_REVIEW items to review queue
            reviewQueueService.routeAllNeedsReview(tender, bidder, criteria, evalResults);

            // Step 6 — Audit
            long eligibleCount = evalResults.stream()
                    .filter(r -> "ELIGIBLE".equals(r.getVerdict())).count();
            long notEligibleCount = evalResults.stream()
                    .filter(r -> "NOT_ELIGIBLE".equals(r.getVerdict())).count();
            long reviewCount = evalResults.stream()
                    .filter(r -> "NEEDS_REVIEW".equals(r.getVerdict())).count();

            // Determine overall verdict (NOT_ELIGIBLE if any mandatory fails)
            boolean mandatoryFailed = evalResults.stream()
                    .anyMatch(r -> "NOT_ELIGIBLE".equals(r.getVerdict()) && r.isMandatory());
            String overallVerdict = mandatoryFailed ? "NOT_ELIGIBLE"
                    : (reviewCount > 0 ? "NEEDS_REVIEW" : "ELIGIBLE");

            auditService.recordVerdictGenerated(tender, bidder, overallVerdict);
            auditService.recordBidderParsed(tender, bidder);

            // Step 7 — Mark bidder as PARSED
            bidder.setParseStatus("PARSED");
            bidderRepository.save(bidder);

            log.info("Evaluation done for bidder {}: verdict={}, eligible={}, notEligible={}, review={}",
                    bidder.getBidderRef(), overallVerdict, eligibleCount, notEligibleCount, reviewCount);

            // Step 8 — If all bidders parsed, mark tender COMPLETED
            checkAndCompleteTender(tender);

        } catch (Exception ex) {
            log.error("Evaluation failed for bidder {}", bidder.getBidderRef(), ex);
            auditService.recordBidderParseFailed(tender, bidder, ex.getMessage());
            bidder.setParseStatus("FAILED");
            bidderRepository.save(bidder);
        }
    }

    private void checkAndCompleteTender(Tender tender) {
        List<Bidder> allBidders = bidderRepository.findByTenderId(tender.getId());
        boolean allDone = allBidders.stream()
                .allMatch(b -> "PARSED".equals(b.getParseStatus())
                        || "FAILED".equals(b.getParseStatus()));

        if (allDone && !allBidders.isEmpty()) {
            tender.setStatus("COMPLETED");
            tenderRepository.save(tender);
            log.info("All bidders evaluated. Tender {} marked COMPLETED.", tender.getTenderRef());
        }
    }
}
