package com.HackerEarth.Hackathon.ubidbridge.service;


import com.HackerEarth.Hackathon.ubidbridge.entity.AuditLog;
import com.HackerEarth.Hackathon.ubidbridge.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {
    private final AuditLogRepository auditLogRepository;

    /**
     * Write a SUCCESS audit record after a confirmed department write.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(String eventId, String ubid,
                              String sourceSystem, String targetSystem,
                              String eventType, String fieldChanged,
                              String oldValue, String newValue, int retryCount) {
        save(eventId, ubid, sourceSystem, targetSystem,
                eventType, fieldChanged, oldValue, newValue,
                "SUCCESS", null, retryCount);
    }

    /**
     * Write a FAILED audit record after all retries are exhausted.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String eventId, String ubid,
                              String sourceSystem, String targetSystem,
                              String eventType, int retryCount) {
        save(eventId, ubid, sourceSystem, targetSystem,
                eventType, null, null, null,
                "FAILED", null, retryCount);
    }

    /**
     * Write a SKIPPED audit record when idempotency check fires.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSkipped(String eventId, String ubid,
                              String sourceSystem, String targetSystem,
                              String eventType) {
        save(eventId, ubid, sourceSystem, targetSystem,
                eventType, null, null, null,
                "SKIPPED", null, 0);
    }

    /**
     * Write a CONFLICT audit record explaining which policy was applied
     * and what both conflicting values were.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordConflict(String eventId, String ubid,
                               String sourceSystem, String targetSystem,
                               String eventType, String fieldChanged,
                               String conflictingValue, String acceptedValue,
                               String conflictPolicy) {
        save(eventId, ubid, sourceSystem, targetSystem,
                eventType, fieldChanged, conflictingValue, acceptedValue,
                "CONFLICT", conflictPolicy, 0);
    }

    // ── Query methods for REST controllers ───────────────────────────────────

    public List<AuditLog> getHistoryForUbid(String ubid) {
        return auditLogRepository.findByUbidOrderByCreatedAtDesc(ubid);
    }

    public List<AuditLog> getRecentAuditFeed() {
        return auditLogRepository.findRecent();
    }

    public List<AuditLog> getByEventId(String eventId) {
        return auditLogRepository.findByEventIdOrderByCreatedAtAsc(eventId);
    }

    // ── Internal save — always inserts, never updates ─────────────────────────

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void save(String eventId, String ubid,
                        String sourceSystem, String targetSystem,
                        String eventType, String fieldChanged,
                        String oldValue, String newValue,
                        String status, String conflictPolicy, int retryCount) {
        AuditLog entry = AuditLog.builder()
                .eventId(eventId)
                .ubid(ubid)
                .sourceSystem(sourceSystem)
                .targetSystem(targetSystem)
                .eventType(eventType)
                .fieldChanged(fieldChanged)
                .oldValue(oldValue)
                .newValue(newValue)
                .status(status)
                .conflictPolicy(conflictPolicy)
                .retryCount(retryCount)
                .build();

        auditLogRepository.save(entry);
        log.debug("Audit record written: eventId={}, status={}, target={}",
                eventId, status, targetSystem);
    }
}
