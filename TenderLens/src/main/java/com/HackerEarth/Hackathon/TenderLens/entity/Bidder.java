package com.HackerEarth.Hackathon.TenderLens.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(schema = "tenderlens", name = "bidder")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Bidder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tender_id", nullable = false)
    private Tender tender;

    @Column(name = "bidder_ref", nullable = false, length = 100)
    private String bidderRef;

    @Column(name = "company_name", nullable = false, length = 300)
    private String companyName;

    @Column(name = "file_name", nullable = false, length = 300)
    private String fileName;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "ocr_required", nullable = false)
    private boolean ocrRequired;

    @Column(name = "parse_status", nullable = false, length = 30)
    private String parseStatus;         // PENDING | PARSING | PARSED | FAILED

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = OffsetDateTime.now();
        if (this.parseStatus == null) this.parseStatus = "PENDING";
    }
}
