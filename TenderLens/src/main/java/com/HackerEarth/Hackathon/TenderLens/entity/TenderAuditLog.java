package com.HackerEarth.Hackathon.TenderLens.entity;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(schema = "tenderlens", name = "audit_log")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenderAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tender_id", nullable = false)
    private Long tenderId;

    @Column(name = "bidder_id")
    private Long bidderId;

    @Column(name = "criterion_id")
    private Long criterionId;

    @Column(name = "action", nullable = false, length = 50)
    private String action;
    // CRITERION_EXTRACTED | BIDDER_PARSED | VERDICT_GENERATED
    // REVIEW_ROUTED | REPORT_GENERATED | CRITERIA_CONFIRMED

    @Column(name = "detail", columnDefinition = "TEXT")
    private String detail;              // JSON string with full context

    @Column(name = "llm_call_id", length = 100)
    private String llmCallId;

    @Column(name = "performed_by", length = 100)
    private String performedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = OffsetDateTime.now();
    }
}
