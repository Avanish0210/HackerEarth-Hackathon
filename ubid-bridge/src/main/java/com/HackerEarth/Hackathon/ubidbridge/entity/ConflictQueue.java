package com.HackerEarth.Hackathon.ubidbridge.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(schema = "ubid", name = "conflict_queue")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConflictQueue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    @Column(name = "ubid", nullable = false, length = 100)
    private String ubid;

    @Column(name = "field_name", nullable = false, length = 100)
    private String fieldName;

    @Column(name = "source_a", nullable = false, length = 50)
    private String sourceA;

    @Column(name = "value_a", nullable = false, columnDefinition = "TEXT")
    private String valueA;

    @Column(name = "source_b", nullable = false, length = 50)
    private String sourceB;

    @Column(name = "value_b", nullable = false, columnDefinition = "TEXT")
    private String valueB;

    @Column(name = "resolution_policy", nullable = false, length = 30)
    private String resolutionPolicy;

    @Column(name = "resolved", nullable = false)
    private boolean resolved;

    @Column(name = "resolved_by", length = 100)
    private String resolvedBy;

    @Column(name = "resolved_value", columnDefinition = "TEXT")
    private String resolvedValue;

    @Column(name = "resolution_note", columnDefinition = "TEXT")
    private String resolutionNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = OffsetDateTime.now();
        this.resolved  = false;
    }
}
