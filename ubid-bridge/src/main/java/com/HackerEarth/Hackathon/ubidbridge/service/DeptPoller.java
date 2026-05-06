package com.HackerEarth.Hackathon.ubidbridge.service;


import com.HackerEarth.Hackathon.ubidbridge.entity.DepartmentRegistry;
import com.HackerEarth.Hackathon.ubidbridge.entity.DepartmentSnapshot;
import com.HackerEarth.Hackathon.ubidbridge.repository.DepartmentRegistryRepository;
import com.HackerEarth.Hackathon.ubidbridge.repository.DepartmentSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Scheduled poller for Direction 2 (Departments → SWS).
 * Handles POLLING and SNAPSHOT integration types.
 * Diffs fetched state against last known snapshot using SHA-256 hashing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeptPoller {

    private final DepartmentRegistryRepository departmentRegistryRepository;
    private final DepartmentSnapshotRepository departmentSnapshotRepository;
    private final EventRouter                   eventRouter;
    private final RestTemplate                  restTemplate;

    // Poll URLs for mock department systems
    private static final Map<String, String> POLL_URLS = Map.of(
            "DEPT_B", "http://localhost:8080/mock/dept-b/snapshot",
            "DEPT_C", "http://localhost:8080/mock/dept-c/snapshot"
    );

    @Scheduled(cron = "${ubid.bridge.poller.cron}")
    public void poll() {
        log.debug("DeptPoller running...");

        List<DepartmentRegistry> depts = departmentRegistryRepository.findByActiveTrue();

        for (DepartmentRegistry dept : depts) {
            if ("POLLING".equals(dept.getIntegrationType())) {
                pollDepartment(dept);
            } else if ("SNAPSHOT".equals(dept.getIntegrationType())) {
                compareSnapshot(dept);
            }
        }
    }

    // ── POLLING — fetch current state and diff ────────────────────────────────

    @SuppressWarnings("unchecked")
    private void pollDepartment(DepartmentRegistry dept) {
        String url = POLL_URLS.get(dept.getDepartmentId());
        if (url == null) return;

        try {
            Map<String, Object> currentState = restTemplate.getForObject(url, Map.class);
            if (currentState == null) return;

            detectAndRouteChanges(dept, currentState);

        } catch (Exception ex) {
            log.warn("Polling failed for dept={}: {}", dept.getDepartmentId(), ex.getMessage());
        }
    }

    // ── SNAPSHOT — hash comparison for file/export-based systems ─────────────

    @SuppressWarnings("unchecked")
    private void compareSnapshot(DepartmentRegistry dept) {
        String url = POLL_URLS.get(dept.getDepartmentId());
        if (url == null) return;

        try {
            Map<String, Object> snapshot = restTemplate.getForObject(url, Map.class);
            if (snapshot == null) return;

            detectAndRouteChanges(dept, snapshot);

        } catch (Exception ex) {
            log.warn("Snapshot compare failed for dept={}: {}",
                    dept.getDepartmentId(), ex.getMessage());
        }
    }

    // ── Core diff logic ───────────────────────────────────────────────────────

    private void detectAndRouteChanges(DepartmentRegistry dept,
                                       Map<String, Object> currentState) {
        String ubid = dept.getUbid();
        String deptId = dept.getDepartmentId();
        boolean anyChanged = false;

        for (Map.Entry<String, Object> entry : currentState.entrySet()) {
            String fieldName  = entry.getKey();
            String fieldValue = entry.getValue() != null ? entry.getValue().toString() : "";
            String newHash    = sha256(fieldValue);

            Optional<DepartmentSnapshot> existing =
                    departmentSnapshotRepository
                            .findByDepartmentIdAndUbidAndFieldName(deptId, ubid, fieldName);

            if (existing.isEmpty()) {
                // First time seeing this field — store snapshot, no event
                saveSnapshot(deptId, ubid, fieldName, fieldValue, newHash);

            } else if (!existing.get().getValueHash().equals(newHash)) {
                // Hash changed — field was updated in the department system
                log.info("Change detected: dept={}, ubid={}, field={}",
                        deptId, ubid, fieldName);

                // Update snapshot
                DepartmentSnapshot snap = existing.get();
                snap.setFieldValue(fieldValue);
                snap.setValueHash(newHash);
                departmentSnapshotRepository.save(snap);

                anyChanged = true;
            }
        }

        if (anyChanged) {
            // Route the full changed state to SWS via EventRouter
            eventRouter.routeDeptEvent(deptId, ubid, "FIELD_UPDATE", currentState);
        }
    }

    private void saveSnapshot(String deptId, String ubid,
                              String fieldName, String value, String hash) {
        DepartmentSnapshot snap = DepartmentSnapshot.builder()
                .departmentId(deptId)
                .ubid(ubid)
                .fieldName(fieldName)
                .fieldValue(value)
                .valueHash(hash)
                .build();
        departmentSnapshotRepository.save(snap);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
