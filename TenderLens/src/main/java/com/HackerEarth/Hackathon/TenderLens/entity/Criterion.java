package com.HackerEarth.Hackathon.TenderLens.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(schema = "tenderlens", name = "criterion")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Criterion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tender_id", nullable = false)
    private Tender tender;

    @Column(name = "criterion_ref", nullable = false, length = 50)
    private String criterionRef;        // C-001, C-002 ...

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "criterion_type", nullable = false, length = 30)
    private String criterionType;       // FINANCIAL | TECHNICAL | COMPLIANCE | CERTIFICATION

    @Column(name = "is_mandatory", nullable = false)
    private boolean mandatory;

    @Column(name = "threshold_value", length = 500)
    private String thresholdValue;      // e.g. "5 crore", "ISO 9001", "3 years"

    @Column(name = "threshold_operator", length = 10)
    private String thresholdOperator;   // GTE | LTE | EQ | CONTAINS

    @Column(name = "source_page")
    private Integer sourcePage;

    @Column(name = "extraction_confidence", precision = 4, scale = 3)
    private BigDecimal extractionConfidence; // LLM confidence score 0.0-1.0

    @Column(name = "confirmed_by_officer", nullable = false)
    private boolean confirmedByOfficer;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = OffsetDateTime.now();
        this.confirmedByOfficer = false;
    }
}
