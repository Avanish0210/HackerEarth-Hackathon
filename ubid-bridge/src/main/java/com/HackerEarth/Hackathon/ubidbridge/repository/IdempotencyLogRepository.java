package com.HackerEarth.Hackathon.ubidbridge.repository;

import com.HackerEarth.Hackathon.ubidbridge.entity.IdempotencyLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IdempotencyLogRepository extends JpaRepository<IdempotencyLog, Long> {
    // Primary idempotency check — has this event already been processed for this target?
    boolean existsByEventIdAndUbidAndTargetSystem(
            String eventId, String ubid, String targetSystem);
}
