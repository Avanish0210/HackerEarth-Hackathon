package com.HackerEarth.Hackathon.ubidbridge.service;


import com.HackerEarth.Hackathon.ubidbridge.config.UbidBridgeProperties;
import com.HackerEarth.Hackathon.ubidbridge.dto.PropagationTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Kafka consumer — picks up PropagationTasks and writes to department systems.
 * Handles retry with exponential back-off and DLQ on exhaustion.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PropagationConsumer {

    private final IdempotencyService           idempotencyService;
    private final AuditService                 auditService;
    private final UbidBridgeProperties properties;
    private final KafkaTemplate<String, PropagationTask> kafkaTemplate;
    private final RestTemplate                 restTemplate;

    private static final String TOPIC_RETRY = "ubid-propagation-retry";
    private static final String TOPIC_DLQ   = "ubid-propagation-dlq";

    // Department base URLs — in real system injected from config
    private static final Map<String, String> DEPT_URLS = Map.of(
            "DEPT_A", "http://localhost:8080/mock/dept-a/update",
            "DEPT_B", "http://localhost:8080/mock/dept-b/update",
            "DEPT_C", "http://localhost:8080/mock/dept-c/update",
            "SWS",    "http://localhost:8080/mock/sws/update"
    );

    @KafkaListener(
            topics = {"ubid-propagation-tasks", "ubid-propagation-retry"},
            groupId = "ubid-bridge-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(PropagationTask task, Acknowledgment ack) {
        log.info("Processing task: eventId={}, target={}, retry={}",
                task.getEventId(), task.getTargetSystem(), task.getRetryCount());

        try {
            // Idempotency re-check (task may have been retried after partial success)
            if (idempotencyService.isDuplicate(
                    task.getEventId(), task.getUbid(), task.getTargetSystem())) {
                log.info("Duplicate on consume — skipping: eventId={}", task.getEventId());
                ack.acknowledge();
                return;
            }

            // Write to target department system
            writeToTarget(task);

            // Mark as processed in Redis + PostgreSQL
            idempotencyService.markProcessed(
                    task.getEventId(), task.getUbid(), task.getTargetSystem());

            // Write success audit record
            auditService.recordSuccess(
                    task.getEventId(), task.getUbid(),
                    task.getSourceSystem(), task.getTargetSystem(),
                    task.getEventType(), null, null,
                    task.getPayload().toString(), task.getRetryCount());

            ack.acknowledge();
            log.info("Task completed successfully: eventId={}, target={}",
                    task.getEventId(), task.getTargetSystem());

        } catch (Exception ex) {
            log.error("Task failed: eventId={}, target={}, retry={}, error={}",
                    task.getEventId(), task.getTargetSystem(),
                    task.getRetryCount(), ex.getMessage());
            handleFailure(task, ex, ack);
        }
    }

    private void writeToTarget(PropagationTask task) {
        String url = DEPT_URLS.get(task.getTargetSystem());
        if (url == null) {
            throw new IllegalArgumentException(
                    "Unknown target system: " + task.getTargetSystem());
        }
        // POST the translated payload to the mock department endpoint
        restTemplate.postForEntity(url, task.getPayload(), String.class);
    }

    private void handleFailure(PropagationTask task, Exception ex, Acknowledgment ack) {
        int maxAttempts = properties.getRetry().getMaxAttempts();

        if (task.getRetryCount() < maxAttempts) {
            // Schedule retry with incremented count
            PropagationTask retryTask = PropagationTask.builder()
                    .eventId(task.getEventId())
                    .ubid(task.getUbid())
                    .sourceSystem(task.getSourceSystem())
                    .targetSystem(task.getTargetSystem())
                    .eventType(task.getEventType())
                    .payload(task.getPayload())
                    .retryCount(task.getRetryCount() + 1)
                    .createdAt(task.getCreatedAt())
                    .build();

            // Exponential back-off delay
            long delayMs = calculateBackoff(task.getRetryCount());
            log.info("Scheduling retry #{} in {}ms for eventId={}",
                    retryTask.getRetryCount(), delayMs, task.getEventId());

            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }

            kafkaTemplate.send(TOPIC_RETRY, task.getUbid(), retryTask);

        } else {
            // All retries exhausted — send to DLQ and write failure audit
            log.error("All {} retries exhausted for eventId={}, sending to DLQ",
                    maxAttempts, task.getEventId());
            kafkaTemplate.send(TOPIC_DLQ, task.getUbid(), task);
            auditService.recordFailure(
                    task.getEventId(), task.getUbid(),
                    task.getSourceSystem(), task.getTargetSystem(),
                    task.getEventType(), task.getRetryCount());
        }

        ack.acknowledge();
    }

    private long calculateBackoff(int retryCount) {
        UbidBridgeProperties.Retry retry = properties.getRetry();
        long delay = (long) (retry.getInitialIntervalMs()
                * Math.pow(retry.getMultiplier(), retryCount));
        return Math.min(delay, retry.getMaxIntervalMs());
    }
}
