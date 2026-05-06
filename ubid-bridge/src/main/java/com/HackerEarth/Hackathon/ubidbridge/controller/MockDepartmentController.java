package com.HackerEarth.Hackathon.ubidbridge.controller;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock department system endpoints — run inside the same JVM for demo convenience.
 * In production these would be real external department APIs.
 *
 * Simulates:
 *   DEPT_A — Revenue Department    (WEBHOOK integration)
 *   DEPT_B — Municipal Corporation (POLLING integration)
 *   DEPT_C — Utility Department    (SNAPSHOT integration)
 *   SWS    — Source system
 */
@Slf4j
@RestController
@RequestMapping("/mock")
public class MockDepartmentController {
    // In-memory store simulating each department's database
    private final Map<String, Map<String, Object>> deptAStore = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> deptBStore = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> deptCStore = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> swsStore   = new ConcurrentHashMap<>();

    // ── DEPT A — Revenue (receives writes via webhook) ────────────────────────

    @PostMapping("/dept-a/update")
    public ResponseEntity<Map<String, Object>> deptAUpdate(
            @RequestBody Map<String, Object> payload) {
        String ubid = extractUbid(payload);
        deptAStore.merge(ubid, new HashMap<>(payload), (old, neu) -> {
            old.putAll(neu);
            return old;
        });
        log.info("[MOCK DEPT_A] Updated record for ubid={}, fields={}", ubid, payload.keySet());
        return ResponseEntity.ok(Map.of(
                "system", "DEPT_A",
                "status", "UPDATED",
                "ubid",   ubid,
                "ts",     OffsetDateTime.now().toString()
        ));
    }

    @GetMapping("/dept-a/record/{ubid}")
    public ResponseEntity<Map<String, Object>> deptARead(@PathVariable String ubid) {
        Map<String, Object> record = deptAStore.getOrDefault(ubid, Map.of());
        return ResponseEntity.ok(Map.of("system", "DEPT_A", "ubid", ubid, "data", record));
    }

    // ── DEPT B — Municipal (polled by DeptPoller) ─────────────────────────────

    @PostMapping("/dept-b/update")
    public ResponseEntity<Map<String, Object>> deptBUpdate(
            @RequestBody Map<String, Object> payload) {
        String ubid = extractUbid(payload);
        deptBStore.merge(ubid, new HashMap<>(payload), (old, neu) -> {
            old.putAll(neu);
            return old;
        });
        log.info("[MOCK DEPT_B] Updated record for ubid={}", ubid);
        return ResponseEntity.ok(Map.of("system", "DEPT_B", "status", "UPDATED", "ubid", ubid));
    }

    @GetMapping("/dept-b/snapshot")
    public ResponseEntity<Map<String, Object>> deptBSnapshot() {
        // Returns current state of all records — polled by DeptPoller
        Map<String, Object> snapshot = new HashMap<>();
        deptBStore.forEach((ubid, data) -> snapshot.putAll(data));
        return ResponseEntity.ok(snapshot);
    }

    @GetMapping("/dept-b/record/{ubid}")
    public ResponseEntity<Map<String, Object>> deptBRead(@PathVariable String ubid) {
        Map<String, Object> record = deptBStore.getOrDefault(ubid, Map.of());
        return ResponseEntity.ok(Map.of("system", "DEPT_B", "ubid", ubid, "data", record));
    }

    // ── DEPT C — Utility (snapshot/export-based) ──────────────────────────────

    @PostMapping("/dept-c/update")
    public ResponseEntity<Map<String, Object>> deptCUpdate(
            @RequestBody Map<String, Object> payload) {
        String ubid = extractUbid(payload);
        deptCStore.merge(ubid, new HashMap<>(payload), (old, neu) -> {
            old.putAll(neu);
            return old;
        });
        log.info("[MOCK DEPT_C] Updated record for ubid={}", ubid);
        return ResponseEntity.ok(Map.of("system", "DEPT_C", "status", "UPDATED", "ubid", ubid));
    }

    @GetMapping("/dept-c/snapshot")
    public ResponseEntity<Map<String, Object>> deptCSnapshot() {
        Map<String, Object> snapshot = new HashMap<>();
        deptCStore.forEach((ubid, data) -> snapshot.putAll(data));
        return ResponseEntity.ok(snapshot);
    }

    @GetMapping("/dept-c/record/{ubid}")
    public ResponseEntity<Map<String, Object>> deptCRead(@PathVariable String ubid) {
        Map<String, Object> record = deptCStore.getOrDefault(ubid, Map.of());
        return ResponseEntity.ok(Map.of("system", "DEPT_C", "ubid", ubid, "data", record));
    }

    // ── SWS — target for Direction 2 (dept → SWS) ────────────────────────────

    @PostMapping("/sws/update")
    public ResponseEntity<Map<String, Object>> swsUpdate(
            @RequestBody Map<String, Object> payload) {
        String ubid = extractUbid(payload);
        swsStore.merge(ubid, new HashMap<>(payload), (old, neu) -> {
            old.putAll(neu);
            return old;
        });
        log.info("[MOCK SWS] Received update from dept for ubid={}", ubid);
        return ResponseEntity.ok(Map.of("system", "SWS", "status", "UPDATED", "ubid", ubid));
    }

    @GetMapping("/sws/record/{ubid}")
    public ResponseEntity<Map<String, Object>> swsRead(@PathVariable String ubid) {
        Map<String, Object> record = swsStore.getOrDefault(ubid, Map.of());
        return ResponseEntity.ok(Map.of("system", "SWS", "ubid", ubid, "data", record));
    }

    // ── Demo state — shows all mock system states for dashboard ──────────────

    @GetMapping("/state")
    public ResponseEntity<Map<String, Object>> getAllState() {
        return ResponseEntity.ok(Map.of(
                "DEPT_A", deptAStore,
                "DEPT_B", deptBStore,
                "DEPT_C", deptCStore,
                "SWS",    swsStore
        ));
    }

    // ── Reset — clears all mock state (useful between demos) ─────────────────

    @DeleteMapping("/reset")
    public ResponseEntity<Map<String, Object>> reset() {
        deptAStore.clear();
        deptBStore.clear();
        deptCStore.clear();
        swsStore.clear();
        log.info("[MOCK] All department stores reset");
        return ResponseEntity.ok(Map.of("status", "RESET", "message", "All mock stores cleared"));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private String extractUbid(Map<String, Object> payload) {
        return payload.getOrDefault("ubid",
                        payload.getOrDefault("rev_ubid",
                                payload.getOrDefault("mun_ubid",
                                        payload.getOrDefault("UTL_UBID", "UNKNOWN"))))
                .toString();
    }
}
