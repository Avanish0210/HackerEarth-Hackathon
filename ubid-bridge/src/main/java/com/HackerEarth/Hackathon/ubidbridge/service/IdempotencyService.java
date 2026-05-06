package com.HackerEarth.Hackathon.ubidbridge.service;


import com.HackerEarth.Hackathon.ubidbridge.entity.IdempotencyLog;
import com.HackerEarth.Hackathon.ubidbridge.repository.IdempotencyLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {
    private final RedisTemplate<String, String> redisTemplate;
    private final IdempotencyLogRepository idempotencyLogRepository;

    private static final long TTL_SECONDS = 604800; // 7 days

    /**
     * Returns true if this event has already been processed for this target.
     * Checks Redis first (fast), falls back to PostgreSQL (durable).
     */
    public boolean isDuplicate(String eventId, String ubid, String targetSystem) {
        String key = buildKey(eventId, ubid, targetSystem);

        // Fast path — Redis check
        Boolean exists = redisTemplate.hasKey(key);
        if (Boolean.TRUE.equals(exists)) {
            log.debug("Duplicate detected in Redis: key={}", key);
            return true;
        }

        // Fallback — PostgreSQL check (handles Redis eviction/restart)
        boolean existsInDb = idempotencyLogRepository
                .existsByEventIdAndUbidAndTargetSystem(eventId, ubid, targetSystem);
        if (existsInDb) {
            log.debug("Duplicate detected in PostgreSQL: eventId={}, target={}", eventId, targetSystem);
            // Re-warm Redis so next check is fast
            redisTemplate.opsForValue().set(key, "1", Duration.ofSeconds(TTL_SECONDS));
            return true;
        }

        return false;
    }

    /**
     * Marks this event as processed in both Redis and PostgreSQL.
     * Called only after a confirmed successful write.
     */
    public void markProcessed(String eventId, String ubid, String targetSystem) {
        String key = buildKey(eventId, ubid, targetSystem);

        // Write to Redis
        redisTemplate.opsForValue().set(key, "1", Duration.ofSeconds(TTL_SECONDS));

        // Write to PostgreSQL as durable backup
        IdempotencyLog log2 = IdempotencyLog.builder()
                .eventId(eventId)
                .ubid(ubid)
                .targetSystem(targetSystem)
                .build();
        idempotencyLogRepository.save(log2);

        log.debug("Marked as processed: eventId={}, ubid={}, target={}", eventId, ubid, targetSystem);
    }

    private String buildKey(String eventId, String ubid, String targetSystem) {
        return String.format("idempotency:%s:%s:%s", eventId, ubid, targetSystem);
    }
}
