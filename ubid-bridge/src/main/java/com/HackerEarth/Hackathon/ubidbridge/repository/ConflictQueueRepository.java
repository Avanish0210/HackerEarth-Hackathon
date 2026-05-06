package com.HackerEarth.Hackathon.ubidbridge.repository;

import com.HackerEarth.Hackathon.ubidbridge.entity.ConflictQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConflictQueueRepository extends JpaRepository<ConflictQueue, Long> {
    // All unresolved conflicts — shown on dashboard for manual review
    List<ConflictQueue> findByResolvedFalseOrderByCreatedAtDesc();

    // Conflicts for a specific UBID
    List<ConflictQueue> findByUbidOrderByCreatedAtDesc(String ubid);

    // Check if a conflict already exists for this event + field
    boolean existsByEventIdAndFieldName(String eventId, String fieldName);
}
