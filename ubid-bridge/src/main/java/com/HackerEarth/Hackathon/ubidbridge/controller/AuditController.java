package com.HackerEarth.Hackathon.ubidbridge.controller;

import com.HackerEarth.Hackathon.ubidbridge.entity.AuditLog;
import com.HackerEarth.Hackathon.ubidbridge.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    /**
     * GET /api/ubid/audit/feed
     * Returns the 50 most recent audit entries across all UBIDs.
     * Used for the live feed on the React dashboard.
     */
    @GetMapping("/feed")
    public ResponseEntity<List<AuditLog>> getRecentFeed() {
        return ResponseEntity.ok(auditService.getRecentAuditFeed());
    }

    /**
     * GET /api/ubid/audit/ubid/{ubid}
     * Returns complete change history for a specific UBID.
     * Officers use this to trace all changes for a citizen.
     */
    @GetMapping("/ubid/{ubid}")
    public ResponseEntity<Map<String, Object>> getHistoryForUbid(
            @PathVariable String ubid) {

        List<AuditLog> history = auditService.getHistoryForUbid(ubid);

        return ResponseEntity.ok(Map.of(
                "ubid",    ubid,
                "count",   history.size(),
                "history", history
        ));
    }

    /**
     * GET /api/ubid/audit/event/{eventId}
     * Returns all audit records for a specific event ID.
     * Shows retries, partial writes, and final outcome.
     */
    @GetMapping("/event/{eventId}")
    public ResponseEntity<Map<String, Object>> getByEventId(
            @PathVariable String eventId) {

        List<AuditLog> records = auditService.getByEventId(eventId);

        return ResponseEntity.ok(Map.of(
                "eventId", eventId,
                "count",   records.size(),
                "records", records
        ));
    }
}
