package com.HackerEarth.Hackathon.TenderLens.entity;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(schema = "tenderlens", name = "report")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tender_id", nullable = false)
    private Tender tender;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "file_name", nullable = false, length = 300)
    private String fileName;

    @Column(name = "total_bidders", nullable = false)
    private int totalBidders;

    @Column(name = "eligible_count", nullable = false)
    private int eligibleCount;

    @Column(name = "not_eligible_count", nullable = false)
    private int notEligibleCount;

    @Column(name = "needs_review_count", nullable = false)
    private int needsReviewCount;

    @Column(name = "generated_by", length = 100)
    private String generatedBy;

    @Column(name = "generated_at", nullable = false, updatable = false)
    private OffsetDateTime generatedAt;

    @PrePersist
    public void prePersist() {
        this.generatedAt = OffsetDateTime.now();
    }
}
