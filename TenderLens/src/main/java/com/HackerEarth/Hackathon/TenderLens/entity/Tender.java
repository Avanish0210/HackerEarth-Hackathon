package com.HackerEarth.Hackathon.TenderLens.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(schema = "tenderlens", name = "tender")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tender {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tender_ref", nullable = false, unique = true, length = 100)
    private String tenderRef;           // e.g. TENDER-2025-001

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "uploaded_by", nullable = false, length = 100)
    private String uploadedBy;

    @Column(name = "file_name", nullable = false, length = 300)
    private String fileName;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "ocr_required", nullable = false)
    private boolean ocrRequired;

    @Column(name = "status", nullable = false, length = 30)
    private String status;
    // UPLOADED | CRITERIA_EXTRACTED | CRITERIA_CONFIRMED | EVALUATION_RUNNING | COMPLETED

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
        if (this.status == null) this.status = "UPLOADED";
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
