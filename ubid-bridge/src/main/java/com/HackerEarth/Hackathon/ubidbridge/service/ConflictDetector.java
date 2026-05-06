package com.HackerEarth.Hackathon.ubidbridge.service;


import com.HackerEarth.Hackathon.ubidbridge.config.UbidBridgeProperties;
import com.HackerEarth.Hackathon.ubidbridge.entity.ConflictQueue;
import com.HackerEarth.Hackathon.ubidbridge.repository.ConflictQueueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConflictDetector {

    private final RedisTemplate<String, String> redisTemplate;
    private final ConflictQueueRepository conflictQueueRepository;
    private final AuditService auditService;
    private final UbidBridgeProperties properties;

    private static final String SOURCE_SWS = "SWS";

    /**
     * Result of conflict resolution — tells the caller whether to proceed with the write.
     */
    public enum Resolution {
        PROCEED,    // apply the new value
        SKIP,       // discard the new value (existing wins)
        HOLD        // manual review required — do not write either value
    }

    public record ConflictResult(Resolution resolution, String acceptedValue, String policy) {}

    /**
     * Called when two updates for the same UBID+field arrive within the conflict window.
     * Applies the configured policy and returns what to do.
     */
    @Transactional
    public ConflictResult resolve(String eventId, String ubid,
                                  String fieldName,
                                  String incomingSource, String incomingValue,
                                  String existingSource, String existingValue) {

        String policy = determinePolicy(fieldName, incomingSource, existingSource);
        log.info("Conflict detected: ubid={}, field={}, policy={}, incoming={}:{}, existing={}:{}",
                ubid, fieldName, policy,
                incomingSource, incomingValue,
                existingSource, existingValue);

        return switch (policy) {
            case "LAST_WRITE_WINS" -> {
                // Incoming event wins — proceed with the write
                auditService.recordConflict(eventId, ubid, incomingSource, existingSource,
                        "CONFLICT_RESOLVED", fieldName, existingValue, incomingValue, policy);
                yield new ConflictResult(Resolution.PROCEED, incomingValue, policy);
            }
            case "SOURCE_PRIORITY" -> {
                // SWS always wins over department systems
                if (SOURCE_SWS.equals(incomingSource)) {
                    auditService.recordConflict(eventId, ubid, incomingSource, existingSource,
                            "CONFLICT_RESOLVED", fieldName, existingValue, incomingValue, policy);
                    yield new ConflictResult(Resolution.PROCEED, incomingValue, policy);
                } else {
                    auditService.recordConflict(eventId, ubid, incomingSource, existingSource,
                            "CONFLICT_RESOLVED", fieldName, incomingValue, existingValue, policy);
                    yield new ConflictResult(Resolution.SKIP, existingValue, policy);
                }
            }
            case "MANUAL_ESCALATION" -> {
                // Hold — do not write until a human resolves it
                escalateToQueue(eventId, ubid, fieldName,
                        incomingSource, incomingValue,
                        existingSource, existingValue, policy);
                yield new ConflictResult(Resolution.HOLD, null, policy);
            }
            default -> {
                log.warn("Unknown conflict policy '{}', defaulting to LAST_WRITE_WINS", policy);
                yield new ConflictResult(Resolution.PROCEED, incomingValue, "LAST_WRITE_WINS");
            }
        };
    }

    /**
     * Resolve a manual conflict from the review queue.
     */
    @Transactional
    public void resolveManual(Long conflictId, String resolvedValue,
                              String resolvedBy, String note) {
        ConflictQueue conflict = conflictQueueRepository.findById(conflictId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Conflict not found: " + conflictId));

        conflict.setResolved(true);
        conflict.setResolvedBy(resolvedBy);
        conflict.setResolvedValue(resolvedValue);
        conflict.setResolutionNote(note);
        conflict.setResolvedAt(OffsetDateTime.now());
        conflictQueueRepository.save(conflict);

        auditService.recordConflict(
                conflict.getEventId(), conflict.getUbid(),
                conflict.getSourceA(), conflict.getSourceB(),
                "MANUAL_RESOLUTION", conflict.getFieldName(),
                conflict.getValueA() + " vs " + conflict.getValueB(),
                resolvedValue, "MANUAL_ESCALATION");

        log.info("Manual conflict resolved: id={}, by={}, value={}",
                conflictId, resolvedBy, resolvedValue);
    }

    public List<ConflictQueue> getPendingConflicts() {
        return conflictQueueRepository.findByResolvedFalseOrderByCreatedAtDesc();
    }

    public List<ConflictQueue> getConflictsForUbid(String ubid) {
        return conflictQueueRepository.findByUbidOrderByCreatedAtDesc(ubid);
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private String determinePolicy(String fieldName,
                                   String incomingSource,
                                   String existingSource) {
        // SWS vs department → always SOURCE_PRIORITY
        if (SOURCE_SWS.equals(incomingSource) || SOURCE_SWS.equals(existingSource)) {
            return "SOURCE_PRIORITY";
        }
        // Fall back to global default from config
        return properties.getDefaultConflictPolicy();
    }

    private void escalateToQueue(String eventId, String ubid, String fieldName,
                                 String sourceA, String valueA,
                                 String sourceB, String valueB,
                                 String policy) {
        if (conflictQueueRepository.existsByEventIdAndFieldName(eventId, fieldName)) {
            log.debug("Conflict already in queue: eventId={}, field={}", eventId, fieldName);
            return;
        }

        ConflictQueue entry = ConflictQueue.builder()
                .eventId(eventId)
                .ubid(ubid)
                .fieldName(fieldName)
                .sourceA(sourceA)
                .valueA(valueA)
                .sourceB(sourceB)
                .valueB(valueB)
                .resolutionPolicy(policy)
                .resolved(false)
                .build();

        conflictQueueRepository.save(entry);
        log.info("Conflict escalated to manual queue: ubid={}, field={}", ubid, fieldName);
    }
}
