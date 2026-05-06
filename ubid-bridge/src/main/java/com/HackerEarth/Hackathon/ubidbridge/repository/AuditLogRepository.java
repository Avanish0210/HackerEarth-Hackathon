package com.HackerEarth.Hackathon.ubidbridge.repository;

import com.HackerEarth.Hackathon.ubidbridge.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    // Full change history for a UBID across all systems
    List<AuditLog> findByUbidOrderByCreatedAtDesc(String ubid);

    // All events for a specific event ID (shows retries)
    List<AuditLog> findByEventIdOrderByCreatedAtAsc(String eventId);

    // All failed writes — useful for the dashboard
    List<AuditLog> findByStatusOrderByCreatedAtDesc(String status);

    // Recent audit entries for live feed on dashboard
    @Query("SELECT a FROM AuditLog a ORDER BY a.createdAt DESC LIMIT 50")
    List<AuditLog> findRecent();
}
