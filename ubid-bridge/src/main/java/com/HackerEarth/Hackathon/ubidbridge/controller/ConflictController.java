package com.HackerEarth.Hackathon.ubidbridge.controller;

import com.HackerEarth.Hackathon.ubidbridge.dto.ConflictResolutionRequest;
import com.HackerEarth.Hackathon.ubidbridge.entity.ConflictQueue;
import com.HackerEarth.Hackathon.ubidbridge.service.ConflictDetector;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/conflicts")
@RequiredArgsConstructor
public class ConflictController {

    private final ConflictDetector conflictDetector;

    /**
     * GET /api/ubid/conflicts/pending
     * Returns all unresolved conflicts waiting for manual review.
     * Shown on the React dashboard conflict queue panel.
     */
    @GetMapping("/pending")
    public ResponseEntity<Map<String, Object>> getPendingConflicts() {
        List<ConflictQueue> pending = conflictDetector.getPendingConflicts();
        return ResponseEntity.ok(Map.of(
                "count",     pending.size(),
                "conflicts", pending
        ));
    }

    /**
     * GET /api/ubid/conflicts/ubid/{ubid}
     * Returns all conflicts (resolved + unresolved) for a specific UBID.
     */
    @GetMapping("/ubid/{ubid}")
    public ResponseEntity<Map<String, Object>> getConflictsForUbid(
            @PathVariable String ubid) {

        List<ConflictQueue> conflicts = conflictDetector.getConflictsForUbid(ubid);
        return ResponseEntity.ok(Map.of(
                "ubid",      ubid,
                "count",     conflicts.size(),
                "conflicts", conflicts
        ));
    }

    /**
     * POST /api/ubid/conflicts/resolve
     * Officer resolves a manual conflict by choosing the accepted value.
     * Writes a resolution audit record and marks the conflict as resolved.
     */
    @PostMapping("/resolve")
    public ResponseEntity<Map<String, Object>> resolveConflict(
            @Valid @RequestBody ConflictResolutionRequest request) {

        log.info("Resolving conflict: id={}, by={}",
                request.getConflictId(), request.getResolvedBy());

        conflictDetector.resolveManual(
                request.getConflictId(),
                request.getResolvedValue(),
                request.getResolvedBy(),
                request.getResolutionNote()
        );

        return ResponseEntity.ok(Map.of(
                "status",     "RESOLVED",
                "conflictId", request.getConflictId(),
                "resolvedBy", request.getResolvedBy(),
                "message",    "Conflict resolved and audit record written"
        ));
    }
}
