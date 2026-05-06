package com.HackerEarth.Hackathon.ubidbridge.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;

import java.time.OffsetDateTime;

@Entity
@Immutable                          // append-only — Hibernate never issues UPDATE
@Table(schema = "ubid", name = "audit_log")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    @Column(name = "ubid", nullable = false, length = 100)
    private String ubid;

    @Column(name = "source_system", nullable = false, length = 50)
    private String sourceSystem;

    @Column(name = "target_system", nullable = false, length = 50)
    private String targetSystem;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "field_changed", length = 100)
    private String fieldChanged;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Column(name = "status", nullable = false, length = 20)
    private String status;             // SUCCESS | FAILED | SKIPPED | CONFLICT

    @Column(name = "conflict_policy", length = 30)
    private String conflictPolicy;     // LWW | SOURCE_PRIORITY | MANUAL_ESCALATION

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = OffsetDateTime.now();
    }
}
