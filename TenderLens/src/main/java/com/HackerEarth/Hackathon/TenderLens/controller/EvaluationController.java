package com.HackerEarth.Hackathon.TenderLens.controller;

import com.HackerEarth.Hackathon.TenderLens.dto.BidderEvaluationSummary;
import com.HackerEarth.Hackathon.TenderLens.dto.EvaluationResult;
import com.HackerEarth.Hackathon.TenderLens.entity.Bidder;
import com.HackerEarth.Hackathon.TenderLens.entity.Criterion;
import com.HackerEarth.Hackathon.TenderLens.entity.Evaluation;
import com.HackerEarth.Hackathon.TenderLens.entity.Tender;
import com.HackerEarth.Hackathon.TenderLens.repository.*;
import com.HackerEarth.Hackathon.TenderLens.service.ReportGeneratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * EvaluationController — query and export evaluation results.
 *
 *  GET  /tenders/{id}/evaluation/summary          → all bidder summaries (matrix)
 *  GET  /tenders/{id}/evaluation/bidder/{bidderId}→ one bidder's criterion-level results
 *  GET  /tenders/{id}/evaluation/matrix           → full N×M matrix (bidders × criteria)
 *  POST /tenders/{id}/report/generate             → generate PDF report
 *  GET  /tenders/{id}/report/download             → download latest PDF report
 *  GET  /tenders/{id}/audit                       → audit log for a tender
 */
@Slf4j
@RestController
@RequestMapping("/tenders")
@RequiredArgsConstructor
public class EvaluationController {

    private final TenderRepository tenderRepository;
    private final BidderRepository bidderRepository;
    private final CriterionRepository criterionRepository;
    private final EvaluationRepository evaluationRepository;
    private final ReportRepository reportRepository;
    private final TenderAuditLogRepository auditLogRepository;
    private final ReportGeneratorService reportGeneratorService;

    // ── Bidder summary matrix ─────────────────────────────────────────────────

    /**
     * Returns a summary for every bidder — overall verdict + counts.
     * Used by the React dashboard to render the top-level matrix.
     */
    @GetMapping("/{id}/evaluation/summary")
    public ResponseEntity<?> getEvaluationSummary(@PathVariable Long id) {
        Tender tender = tenderRepository.findById(id).orElse(null);
        if (tender == null) return ResponseEntity.notFound().build();

        List<Bidder> bidders = bidderRepository.findByTenderIdOrderByCompanyName(id);
        List<Criterion> criteria = criterionRepository.findByTenderIdOrderByCriterionRef(id).stream().filter(c -> c.isConfirmedByOfficer()).toList();

        List<BidderEvaluationSummary> summaries = new ArrayList<>();

        for (Bidder bidder : bidders) {
            List<Evaluation> evals = evaluationRepository
                    .findByTenderIdOrderByBidderIdAscCriterionIdAsc(tender.getId()).stream().filter(e -> e.getBidder().getId().equals(bidder.getId())).toList();

            List<EvaluationResult> results = evals.stream().map(this::toResult).collect(Collectors.toList());

            int eligible    = (int) results.stream().filter(r -> "ELIGIBLE".equals(r.getVerdict())).count();
            int notEligible = (int) results.stream().filter(r -> "NOT_ELIGIBLE".equals(r.getVerdict())).count();
            int needsReview = (int) results.stream().filter(r -> "NEEDS_REVIEW".equals(r.getVerdict())).count();

            boolean mandatoryFailed = evals.stream()
                    .anyMatch(e -> "NOT_ELIGIBLE".equals(e.getVerdict())
                            && e.getCriterion().isMandatory());

            String overall = mandatoryFailed ? "NOT_ELIGIBLE"
                    : (needsReview > 0 ? "NEEDS_REVIEW" : "ELIGIBLE");

            // If no evaluations yet, reflect parse status
            if (evals.isEmpty()) {
                overall = "PENDING".equals(bidder.getParseStatus()) ? "PENDING"
                        : "PARSING".equals(bidder.getParseStatus()) ? "IN_PROGRESS"
                        : "FAILED".equals(bidder.getParseStatus()) ? "FAILED"
                        : "PENDING";
            }

            summaries.add(BidderEvaluationSummary.builder()
                    .bidderId(bidder.getId())
                    .bidderRef(bidder.getBidderRef())
                    .companyName(bidder.getCompanyName())
                    .totalCriteria(criteria.size())
                    .eligibleCount(eligible)
                    .notEligibleCount(notEligible)
                    .needsReviewCount(needsReview)
                    .overallVerdict(overall)
                    .criteriaResults(results)
                    .build());
        }

        return ResponseEntity.ok(Map.of(
                "tenderId", id,
                "tenderRef", tender.getTenderRef(),
                "tenderStatus", tender.getStatus(),
                "totalBidders", bidders.size(),
                "totalCriteria", criteria.size(),
                "summaries", summaries
        ));
    }

    // ── One bidder's full criterion-level results ─────────────────────────────

    /**
     * Returns the full criterion-by-criterion breakdown for a single bidder.
     * This is the "drill down" view with evidence chains.
     */
    @GetMapping("/{id}/evaluation/bidder/{bidderId}")
    public ResponseEntity<?> getBidderEvaluation(
            @PathVariable Long id,
            @PathVariable Long bidderId) {

        Tender tender = tenderRepository.findById(id).orElse(null);
        if (tender == null) return ResponseEntity.notFound().build();

        Bidder bidder = bidderRepository.findById(bidderId).orElse(null);
        if (bidder == null) return ResponseEntity.notFound().build();

        List<Evaluation> evals = evaluationRepository
                .findByTenderIdOrderByBidderIdAscCriterionIdAsc(id).stream().filter(e -> e.getBidder().getId().equals(bidderId)).toList();

        List<EvaluationResult> results = evals.stream().map(this::toResult).collect(Collectors.toList());

        long eligible    = results.stream().filter(r -> "ELIGIBLE".equals(r.getVerdict())).count();
        long notEligible = results.stream().filter(r -> "NOT_ELIGIBLE".equals(r.getVerdict())).count();
        long needsReview = results.stream().filter(r -> "NEEDS_REVIEW".equals(r.getVerdict())).count();

        boolean mandatoryFailed = evals.stream()
                .anyMatch(e -> "NOT_ELIGIBLE".equals(e.getVerdict())
                        && e.getCriterion().isMandatory());
        String overall = mandatoryFailed ? "NOT_ELIGIBLE"
                : (needsReview > 0 ? "NEEDS_REVIEW" : "ELIGIBLE");

        return ResponseEntity.ok(Map.of(
                "tenderId", id,
                "bidderId", bidderId,
                "bidderRef", bidder.getBidderRef(),
                "companyName", bidder.getCompanyName(),
                "parseStatus", bidder.getParseStatus(),
                "overallVerdict", results.isEmpty() ? "PENDING" : overall,
                "eligible", eligible,
                "notEligible", notEligible,
                "needsReview", needsReview,
                "results", results
        ));
    }

    // ── Full N×M matrix (bidders × criteria) ─────────────────────────────────

    /**
     * Returns a compact matrix for rendering in the React table.
     * Format: { criteria: [...], bidders: [{ bidderRef, cells: { criterionId: verdict } }] }
     */
    @GetMapping("/{id}/evaluation/matrix")
    public ResponseEntity<?> getMatrix(@PathVariable Long id) {
        Tender tender = tenderRepository.findById(id).orElse(null);
        if (tender == null) return ResponseEntity.notFound().build();

        List<Criterion> criteria = criterionRepository.findByTenderIdOrderByCriterionRef(id).stream().filter(c -> c.isConfirmedByOfficer()).toList();
        List<Bidder> bidders = bidderRepository.findByTenderIdOrderByCompanyName(id);
        List<Evaluation> allEvals = evaluationRepository.findByTenderIdOrderByBidderIdAscCriterionIdAsc(id);

        // Build per-bidder cell map
        List<Map<String, Object>> bidderRows = new ArrayList<>();
        for (Bidder bidder : bidders) {
            Map<String, Object> cells = new java.util.LinkedHashMap<>();
            for (Evaluation e : allEvals) {
                if (e.getBidder().getId().equals(bidder.getId())) {
                    cells.put(String.valueOf(e.getCriterion().getId()), Map.of(
                            "verdict", e.getVerdict() != null ? e.getVerdict() : "PENDING",
                            "confidence", e.getConfidenceScore() != null ? e.getConfidenceScore() : 0,
                            "officerOverride", e.isOfficerOverride()
                    ));
                }
            }
            bidderRows.add(Map.of(
                    "bidderId", bidder.getId(),
                    "bidderRef", bidder.getBidderRef(),
                    "companyName", bidder.getCompanyName(),
                    "parseStatus", bidder.getParseStatus(),
                    "cells", cells
            ));
        }

        // Compact criteria info
        List<Map<String, Object>> criteriaInfo = criteria.stream().map(c -> {
            Map<String, Object> info = new java.util.LinkedHashMap<>();
            info.put("id", c.getId());
            info.put("ref", c.getCriterionRef());
            info.put("description", c.getDescription());
            info.put("type", c.getCriterionType());
            info.put("mandatory", c.isMandatory());
            info.put("threshold", c.getThresholdValue() != null ? c.getThresholdValue() : "");
            return info;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "tenderId", id,
                "tenderRef", tender.getTenderRef(),
                "criteria", criteriaInfo,
                "bidders", bidderRows
        ));
    }

    // ── Generate PDF report ───────────────────────────────────────────────────

    @PostMapping("/{id}/report/generate")
    public ResponseEntity<?> generateReport(
            @PathVariable Long id,
            @RequestParam(value = "generatedBy", defaultValue = "SYSTEM") String generatedBy) {

        Tender tender = tenderRepository.findById(id).orElse(null);
        if (tender == null) return ResponseEntity.notFound().build();

        if ("UPLOADED".equals(tender.getStatus()) || "CRITERIA_EXTRACTED".equals(tender.getStatus())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Evaluation must be completed before generating a report."));
        }

        try {
            com.HackerEarth.Hackathon.TenderLens.entity.Report report =
                    reportGeneratorService.generate(id);

            return ResponseEntity.ok(Map.of(
                    "reportId", report.getId(),
                    "fileName", report.getFileName(),
                    "totalBidders", report.getTotalBidders(),
                    "eligibleCount", report.getEligibleCount(),
                    "notEligibleCount", report.getNotEligibleCount(),
                    "needsReviewCount", report.getNeedsReviewCount(),
                    "generatedAt", report.getGeneratedAt(),
                    "message", "Report generated. Use /report/download to get the PDF."
            ));

        } catch (IOException e) {
            log.error("Report generation failed for tender {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Report generation failed: " + e.getMessage()));
        }
    }

    // ── Download latest PDF report ────────────────────────────────────────────

    @GetMapping("/{id}/report/download")
    public ResponseEntity<Resource> downloadReport(@PathVariable Long id) {
        com.HackerEarth.Hackathon.TenderLens.entity.Report report =
                reportRepository.findTopByTenderIdOrderByGeneratedAtDesc(id).orElse(null);

        if (report == null) {
            return ResponseEntity.notFound().build();
        }

        File file = new File(report.getFilePath());
        if (!file.exists()) {
            return ResponseEntity.status(HttpStatus.GONE).build();
        }

        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + report.getFileName() + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(resource);
    }

    // ── Audit log for a tender ────────────────────────────────────────────────

    @GetMapping("/{id}/audit")
    public ResponseEntity<?> getAuditLog(@PathVariable Long id) {
        if (!tenderRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(
                auditLogRepository.findByTenderIdOrderByCreatedAtDesc(id));
    }

    // ── Helper: Evaluation → EvaluationResult DTO ────────────────────────────

    private EvaluationResult toResult(Evaluation e) {
        return EvaluationResult.builder()
                .criterionId(e.getCriterion().getId())
                .criterionRef(e.getCriterion().getCriterionRef())
                .criterionDescription(e.getCriterion().getDescription())
                .criterionType(e.getCriterion().getCriterionType())
                .mandatory(e.getCriterion().isMandatory())
                .thresholdValue(e.getCriterion().getThresholdValue())
                .extractedValue(e.getExtractedValue())
                .verbatimExcerpt(e.getVerbatimExcerpt())
                .sourcePage(e.getSourcePage())
                //.sourceDocument(e.getSourceDocument())
                .verdict(e.getVerdict())
                .confidenceScore(e.getConfidenceScore())
                .ocrQualityFlag(e.isOcrQualityFlag())
                //.llmCallId(e.getLlmCallId())
                .reviewReason(e.getReviewReason())
                .build();
    }



}
