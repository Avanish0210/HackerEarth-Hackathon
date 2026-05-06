package com.HackerEarth.Hackathon.ubidbridge.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(schema = "ubid", name = "idempotency_log")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    @Column(name = "ubid", nullable = false, length = 100)
    private String ubid;

    @Column(name = "target_system", nullable = false, length = 50)
    private String targetSystem;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private OffsetDateTime processedAt;

    @PrePersist
    public void prePersist() {
        this.processedAt = OffsetDateTime.now();
    }
}
