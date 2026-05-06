package com.HackerEarth.Hackathon.TenderLens.entity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(schema = "tenderlens", name = "evaluation")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Evaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tender_id", nullable = false)
    private Tender tender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bidder_id", nullable = false)
    private Bidder bidder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criterion_id", nullable = false)
    private Criterion criterion;

    @Column(name = "extracted_value", columnDefinition = "TEXT")
    private String extractedValue;      // What was found in the bidder doc

    @Column(name = "source_document", length = 300)
    private String sourceDocument;

    @Column(name = "source_page")
    private Integer sourcePage;

    @Column(name = "verbatim_excerpt", columnDefinition = "TEXT")
    private String verbatimExcerpt;     // Exact text extracted from doc

    @Column(name = "verdict", length = 20)
    private String verdict;             // ELIGIBLE | NOT_ELIGIBLE | NEEDS_REVIEW

    @Column(name = "confidence_score", precision = 4, scale = 3)
    private BigDecimal confidenceScore; // 0.000 to 1.000

    @Column(name = "ocr_quality_flag", nullable = false)
    private boolean ocrQualityFlag;

    @Column(name = "llm_call_id", length = 100)
    private String llmCallId;           // Trace ID for the LLM call

    @Column(name = "review_reason", length = 100)
    private String reviewReason;        // Why routed to review: LOW_CONFIDENCE, AMBIGUOUS_VALUE, NOT_FOUND, etc.

    @Column(name = "officer_override", nullable = false)
    private boolean officerOverride;    // Officer has manually overridden the AI verdict

    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;          // Officer's notes during review

    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;          // Officer ID who reviewed

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;  // When officer reviewed

    @Column(name = "evaluated_at", nullable = false, updatable = false)
    private OffsetDateTime evaluatedAt;

    @PrePersist
    public void prePersist() {
        this.evaluatedAt = OffsetDateTime.now();
        this.ocrQualityFlag = false;
        this.officerOverride = false;
    }

}
