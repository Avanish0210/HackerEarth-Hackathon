package com.HackerEarth.Hackathon.TenderLens.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(schema = "tenderlens", name = "review_queue")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewQueue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluation_id", nullable = false)
    private Evaluation evaluation;

    @Column(name = "reason", nullable = false, length = 50)
    private String reason;
    // LOW_CONFIDENCE | OCR_QUALITY | AMBIGUOUS_VALUE | NOT_FOUND

    @Column(name = "reviewer", length = 100)
    private String reviewer;

    @Column(name = "reviewed", nullable = false)
    private boolean reviewed;

    @Column(name = "override_verdict", length = 20)
    private String overrideVerdict;     // Officer's final call

    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = OffsetDateTime.now();
        this.reviewed  = false;
    }
}
