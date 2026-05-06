package com.HackerEarth.Hackathon.ubidbridge.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(schema = "ubid", name = "department_registry")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentRegistry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ubid", nullable = false, length = 100)
    private String ubid;

    @Column(name = "department_id", nullable = false, length = 50)
    private String departmentId;

    @Column(name = "department_name", nullable = false, length = 200)
    private String departmentName;

    @Column(name = "integration_type", nullable = false, length = 20)
    private String integrationType;    // WEBHOOK | POLLING | SNAPSHOT

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "registered_at", nullable = false, updatable = false)
    private OffsetDateTime registeredAt;

    @PrePersist
    public void prePersist() {
        this.registeredAt = OffsetDateTime.now();
        this.active = true;
    }
}
