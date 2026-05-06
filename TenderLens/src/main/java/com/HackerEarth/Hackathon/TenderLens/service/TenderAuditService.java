package com.HackerEarth.Hackathon.TenderLens.service;

import com.HackerEarth.Hackathon.TenderLens.entity.TenderAuditLog;
import com.HackerEarth.Hackathon.TenderLens.entity.Tender;
import com.HackerEarth.Hackathon.TenderLens.entity.Bidder;
import com.HackerEarth.Hackathon.TenderLens.repository.TenderAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenderAuditService {

    private final TenderAuditLogRepository auditLogRepository;

    /**
     * REQUIRES_NEW ensures audit is written even if the calling transaction rolls back.
     * This is the same pattern used in UBID Bridge's AuditService.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long tenderId, String action, String actorId, String detail) {
        try {
            TenderAuditLog log = TenderAuditLog.builder()
                    .tenderId(tenderId)
                    .action(action)
                    .performedBy(actorId)
                    .detail(detail)
                    .createdAt(OffsetDateTime.now())
                    .build();
            auditLogRepository.save(log);
        } catch (Exception ex) {
            // Audit must never crash the main flow
            log.error("Failed to write audit log: action={}, tender={}", action, tenderId, ex);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordCriterionExtracted(Tender tender, int criteriaCount, String actorId) {
        record(tender.getId(), "CRITERION_EXTRACTED",
                actorId,
                criteriaCount + " criteria extracted from tender " + tender.getTenderRef());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordCriteriaConfirmed(Tender tender, String officerId) {
        record(tender.getId(), "CRITERIA_CONFIRMED",
                officerId,
                "Officer " + officerId + " confirmed criteria for tender " + tender.getTenderRef());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordBidderParsed(Tender tender, Bidder bidder) {
        record(tender.getId(), "BIDDER_PARSED",
                "SYSTEM",
                "Bidder " + bidder.getBidderRef() + " (" + bidder.getCompanyName() + ") parsed successfully");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordBidderParseFailed(Tender tender, Bidder bidder, String reason) {
        record(tender.getId(), "BIDDER_PARSE_FAILED",
                "SYSTEM",
                "Bidder " + bidder.getBidderRef() + " parse failed: " + reason);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordVerdictGenerated(Tender tender, Bidder bidder, String verdict) {
        record(tender.getId(), "VERDICT_GENERATED",
                "SYSTEM",
                "Bidder " + bidder.getBidderRef() + " verdict=" + verdict);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordReviewRouted(Tender tender, Bidder bidder, String reason) {
        record(tender.getId(), "REVIEW_ROUTED",
                "SYSTEM",
                "Bidder " + bidder.getBidderRef() + " routed to review: " + reason);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordReviewResolved(Tender tender, Long bidderId, String officerId, String verdict) {
        record(tender.getId(), "REVIEW_RESOLVED",
                officerId,
                "Officer " + officerId + " resolved bidder " + bidderId + " as " + verdict);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordReportGenerated(Tender tender, String reportPath) {
        record(tender.getId(), "REPORT_GENERATED",
                "SYSTEM",
                "Report generated at: " + reportPath);
    }
}