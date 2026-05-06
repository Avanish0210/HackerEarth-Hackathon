package com.HackerEarth.Hackathon.ubidbridge.controller;

import com.HackerEarth.Hackathon.ubidbridge.repository.AuditLogRepository;

import com.HackerEarth.Hackathon.ubidbridge.repository.ConflictQueueRepository;
import com.HackerEarth.Hackathon.ubidbridge.repository.DepartmentRegistryRepository;
import com.HackerEarth.Hackathon.ubidbridge.service.TranslatorRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Map;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final AuditLogRepository auditLogRepository;
    private final ConflictQueueRepository conflictQueueRepository;
    private final DepartmentRegistryRepository departmentRegistryRepository;
    private final TranslatorRegistry translatorRegistry;

    /**
     * GET /api/ubid/dashboard/stats
     * Returns summary stats for the React dashboard header cards.
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        long totalEvents      = auditLogRepository.count();
        long successCount     = auditLogRepository.findByStatusOrderByCreatedAtDesc("SUCCESS").size();
        long failedCount      = auditLogRepository.findByStatusOrderByCreatedAtDesc("FAILED").size();
        long skippedCount     = auditLogRepository.findByStatusOrderByCreatedAtDesc("SKIPPED").size();
        long pendingConflicts = conflictQueueRepository.findByResolvedFalseOrderByCreatedAtDesc().size();
        long activeDepts      = departmentRegistryRepository.findByActiveTrue().size();

        return ResponseEntity.ok(Map.of(
                "totalEvents",       totalEvents,
                "successCount",      successCount,
                "failedCount",       failedCount,
                "skippedCount",      skippedCount,
                "pendingConflicts",  pendingConflicts,
                "activeDepartments", activeDepts,
                "registeredTranslators", translatorRegistry.registeredDepartments(),
                "timestamp",         OffsetDateTime.now().toString()
        ));
    }
}
