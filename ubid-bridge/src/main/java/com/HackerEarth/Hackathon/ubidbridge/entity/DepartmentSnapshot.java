package com.HackerEarth.Hackathon.ubidbridge.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(schema = "ubid", name = "department_snapshot")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "department_id", nullable = false, length = 50)
    private String departmentId;

    @Column(name = "ubid", nullable = false, length = 100)
    private String ubid;

    @Column(name = "field_name", nullable = false, length = 100)
    private String fieldName;

    @Column(name = "field_value", columnDefinition = "TEXT")
    private String fieldValue;

    @Column(name = "value_hash", nullable = false, length = 64)
    private String valueHash;           // SHA-256 — used for fast diff detection

    @Column(name = "snapshot_at", nullable = false)
    private OffsetDateTime snapshotAt;

    @PrePersist
    @PreUpdate
    public void preUpdate() {
        this.snapshotAt = OffsetDateTime.now();
    }
}
