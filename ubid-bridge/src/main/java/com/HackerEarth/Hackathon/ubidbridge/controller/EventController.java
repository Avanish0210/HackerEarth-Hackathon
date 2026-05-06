package com.HackerEarth.Hackathon.ubidbridge.controller;

import com.HackerEarth.Hackathon.ubidbridge.dto.SwsEvent;
import com.HackerEarth.Hackathon.ubidbridge.service.EventRouter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
public class EventController {

    private final EventRouter eventRouter;

    /**
     * POST /api/ubid/events/sws
     * SWS posts an event here when a citizen record changes.
     * Bridge fans out to all relevant department systems.
     */
    @PostMapping("/sws")
    public ResponseEntity<Map<String, Object>> receiveSwsEvent(
            @Valid @RequestBody SwsEvent event) {

        // Auto-generate eventId if not provided
        if (event.getEventId() == null || event.getEventId().isBlank()) {
            event.setEventId(UUID.randomUUID().toString());
        }
        if (event.getOccurredAt() == null) {
            event.setOccurredAt(OffsetDateTime.now());
        }

        log.info("Received SWS event: eventId={}, ubid={}, type={}",
                event.getEventId(), event.getUbid(), event.getEventType());

        eventRouter.routeSwsEvent(event);

        return ResponseEntity.accepted().body(Map.of(
                "status",  "ACCEPTED",
                "eventId", event.getEventId(),
                "message", "Event received and queued for propagation"
        ));
    }

    /**
     * POST /api/ubid/events/demo
     * Convenience endpoint for hackathon demo —
     * fires a pre-built ADDRESS_CHANGE event for a demo UBID.
     */
    @PostMapping("/demo")
    public ResponseEntity<Map<String, Object>> fireDemo() {
        SwsEvent demo = SwsEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .ubid("UBID-DEMO-001")
                .eventType("ADDRESS_CHANGE")
                .fields(Map.of(
                        "street",    "42 MG Road",
                        "city",      "Varanasi",
                        "state",     "Uttar Pradesh",
                        "pincode",   "221001",
                        "district",  "Varanasi"
                ))
                .occurredAt(OffsetDateTime.now())
                .initiatedBy("DEMO_OFFICER")
                .build();

        eventRouter.routeSwsEvent(demo);

        return ResponseEntity.accepted().body(Map.of(
                "status",  "ACCEPTED",
                "eventId", demo.getEventId(),
                "ubid",    demo.getUbid(),
                "message", "Demo ADDRESS_CHANGE event fired for UBID-DEMO-001"
        ));
    }

    /**
     * POST /api/ubid/events/demo/conflict
     * Fires two conflicting updates for the same UBID within the conflict window.
     * Great for demonstrating the conflict detection engine.
     */
    @PostMapping("/demo/conflict")
    public ResponseEntity<Map<String, Object>> fireDemoConflict() {
        String ubid = "UBID-DEMO-001";

        // First update from SWS
        SwsEvent swsUpdate = SwsEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .ubid(ubid)
                .eventType("ADDRESS_CHANGE")
                .fields(Map.of(
                        "street",  "10 Civil Lines",
                        "city",    "Varanasi",
                        "pincode", "221001"
                ))
                .occurredAt(OffsetDateTime.now())
                .initiatedBy("SWS_OFFICER")
                .build();

        eventRouter.routeSwsEvent(swsUpdate);

        return ResponseEntity.accepted().body(Map.of(
                "status",  "ACCEPTED",
                "eventId", swsUpdate.getEventId(),
                "message", "Conflict demo event fired — check /conflicts for pending review"
        ));
    }
}
