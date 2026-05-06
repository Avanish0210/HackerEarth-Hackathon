package com.HackerEarth.Hackathon.ubidbridge.service;


import com.HackerEarth.Hackathon.ubidbridge.dto.PropagationTask;
import com.HackerEarth.Hackathon.ubidbridge.dto.SwsEvent;
import com.HackerEarth.Hackathon.ubidbridge.entity.DepartmentRegistry;
import com.HackerEarth.Hackathon.ubidbridge.repository.DepartmentRegistryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Core orchestrator — Direction 1 (SWS → Departments).
 * Receives a SwsEvent, looks up which departments carry a record
 * for that UBID, translates the payload for each, and fans out
 * PropagationTasks onto Kafka.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventRouter {

    private final DepartmentRegistryRepository departmentRegistryRepository;
    private final TranslatorRegistry           translatorRegistry;
    private final IdempotencyService           idempotencyService;
    private final AuditService                 auditService;
    private final KafkaTemplate<String, PropagationTask> kafkaTemplate;

    private static final String TOPIC_PROPAGATION = "ubid-propagation-tasks";
    private static final String SOURCE_SWS        = "SWS";

    /**
     * Entry point called by the REST controller when SWS posts an event.
     * Fans out one PropagationTask per relevant department onto Kafka.
     */
    public void routeSwsEvent(SwsEvent event) {
        log.info("Routing SWS event: eventId={}, ubid={}, type={}",
                event.getEventId(), event.getUbid(), event.getEventType());

        // 1. Find all active departments that carry a record for this UBID
        List<DepartmentRegistry> departments =
                departmentRegistryRepository.findByUbidAndActiveTrue(event.getUbid());

        if (departments.isEmpty()) {
            log.warn("No departments registered for UBID: {}", event.getUbid());
            return;
        }

        log.info("Fanning out to {} departments for UBID: {}",
                departments.size(), event.getUbid());

        // 2. For each department, translate + idempotency check + publish to Kafka
        for (DepartmentRegistry dept : departments) {
            fanOutToDepartment(event, dept);
        }
    }

    private void fanOutToDepartment(SwsEvent event, DepartmentRegistry dept) {
        String departmentId = dept.getDepartmentId();

        // Idempotency check — skip if already processed
        if (idempotencyService.isDuplicate(
                event.getEventId(), event.getUbid(), departmentId)) {
            log.info("Skipping duplicate: eventId={}, target={}",
                    event.getEventId(), departmentId);
            auditService.recordSkipped(
                    event.getEventId(), event.getUbid(),
                    SOURCE_SWS, departmentId, event.getEventType());
            return;
        }

        // Translate SWS canonical fields → department schema
        Map<String, Object> translatedPayload = translatorRegistry.translate(
                departmentId, event.getFields(), event.getEventType());

        // Build the propagation task
        PropagationTask task = PropagationTask.builder()
                .eventId(event.getEventId())
                .ubid(event.getUbid())
                .sourceSystem(SOURCE_SWS)
                .targetSystem(departmentId)
                .eventType(event.getEventType())
                .payload(translatedPayload)
                .retryCount(0)
                .createdAt(OffsetDateTime.now())
                .build();

        // Publish to Kafka — keyed by UBID for ordered processing per citizen
        kafkaTemplate.send(TOPIC_PROPAGATION, event.getUbid(), task);

        log.info("Task published to Kafka: eventId={}, target={}, topic={}",
                event.getEventId(), departmentId, TOPIC_PROPAGATION);
    }

    /**
     * Direction 2 — Department → SWS.
     * Called by the DeptPoller or webhook handler when a dept system changes.
     * Translates back to SWS canonical format and publishes to Kafka.
     */
    public void routeDeptEvent(String departmentId, String ubid,
                               String eventType, Map<String, Object> deptFields) {
        String eventId = UUID.randomUUID().toString();

        log.info("Routing dept event: eventId={}, source={}, ubid={}, type={}",
                eventId, departmentId, ubid, eventType);

        // Idempotency check against SWS as target
        if (idempotencyService.isDuplicate(eventId, ubid, SOURCE_SWS)) {
            auditService.recordSkipped(eventId, ubid, departmentId, SOURCE_SWS, eventType);
            return;
        }

        // For dept→SWS, translate dept fields back to SWS canonical format
        // (reverse translation — pass through for now, SWS handles normalization)
        PropagationTask task = PropagationTask.builder()
                .eventId(eventId)
                .ubid(ubid)
                .sourceSystem(departmentId)
                .targetSystem(SOURCE_SWS)
                .eventType(eventType)
                .payload(deptFields)
                .retryCount(0)
                .createdAt(OffsetDateTime.now())
                .build();

        kafkaTemplate.send(TOPIC_PROPAGATION, ubid, task);

        log.info("Dept→SWS task published: eventId={}, source={}", eventId, departmentId);
    }
}
