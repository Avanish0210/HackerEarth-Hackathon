package com.HackerEarth.Hackathon.TenderLens.controller;


import com.HackerEarth.Hackathon.TenderLens.dto.CriterionConfirmRequest;
import com.HackerEarth.Hackathon.TenderLens.dto.EvaluationJobMessage;
import com.HackerEarth.Hackathon.TenderLens.entity.Bidder;
import com.HackerEarth.Hackathon.TenderLens.entity.Criterion;
import com.HackerEarth.Hackathon.TenderLens.entity.Tender;
import com.HackerEarth.Hackathon.TenderLens.repository.BidderRepository;
import com.HackerEarth.Hackathon.TenderLens.repository.CriterionRepository;
import com.HackerEarth.Hackathon.TenderLens.repository.TenderRepository;
import com.HackerEarth.Hackathon.TenderLens.service.CriterionExtractorService;
import com.HackerEarth.Hackathon.TenderLens.service.DocumentParserService;
import com.HackerEarth.Hackathon.TenderLens.service.TenderAuditService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TenderController — manages the full tender lifecycle:
 *
 *  POST /tenders/upload          → officer uploads tender PDF
 *  GET  /tenders                 → list all tenders
 *  GET  /tenders/{id}            → get tender details
 *  GET  /tenders/{id}/criteria   → get extracted criteria for officer review
 *  POST /tenders/{id}/criteria/confirm → officer confirms (gate before evaluation)
 *  POST /tenders/{id}/bidders/upload   → officer uploads one bidder PDF
 *  POST /tenders/{id}/evaluate         → trigger async evaluation for all bidders
 *  GET  /tenders/{id}/bidders          → list all bidders for a tender
 */
@Slf4j
@RestController
@RequestMapping("/tenders")
@RequiredArgsConstructor
public class TenderController {

    private final TenderRepository tenderRepository;
    private final CriterionRepository criterionRepository;
    private final BidderRepository bidderRepository;
    private final DocumentParserService documentParserService;
    private final CriterionExtractorService criterionExtractorService;
    private final TenderAuditService auditService;
    private final KafkaTemplate<String, EvaluationJobMessage> kafkaTemplate;

    // ── Upload tender PDF ─────────────────────────────────────────────────────

    /**
     * Step 1 of the TenderLens flow.
     * Officer uploads a tender PDF. The backend:
     *   1. Saves the file to disk
     *   2. Parses the PDF (native or OCR)
     *   3. Calls Ollama LLM to extract eligibility criteria
     *   4. Saves criteria (unconfirmed) to DB
     *   5. Sets tender status → CRITERIA_EXTRACTED
     *
     * The officer MUST then call /criteria/confirm before evaluation can begin.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadTender(
            @RequestParam("file") MultipartFile file,
            @RequestParam("tenderRef") String tenderRef,
            @RequestParam("title") String title,
            @RequestParam("uploadedBy") String uploadedBy,
            @RequestParam(value = "ocrRequired", defaultValue = "false") boolean ocrRequired) {

        // Validate unique ref
        if (tenderRepository.existsByTenderRef(tenderRef)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Tender ref already exists: " + tenderRef));
        }

        // Validate file
        if (file.isEmpty() || !isPdf(file.getOriginalFilename())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Only PDF files are accepted"));
        }

        try {
            // Save file to upload directory
            String uploadDir = System.getProperty("java.io.tmpdir") + "/tenderlens/uploads";
            new File(uploadDir).mkdirs();
            String fileName = tenderRef.replace("/", "-") + "_" + file.getOriginalFilename();
            String filePath = uploadDir + File.separator + fileName;
            file.transferTo(new File(filePath));

            // Create tender entity
            Tender tender = Tender.builder()
                    .tenderRef(tenderRef)
                    .title(title)
                    .uploadedBy(uploadedBy)
                    .fileName(fileName)
                    .filePath(filePath)
                    .ocrRequired(ocrRequired)
                    .status("UPLOADED")
                    .build();
            tender = tenderRepository.save(tender);

            log.info("Tender uploaded: {} — starting criterion extraction", tenderRef);

            // Parse document
            DocumentParserService.ParsedDocument parsed =
                    documentParserService.parse(filePath, ocrRequired);

            // Extract criteria via LLM
            List<Criterion> criteria = criterionExtractorService.extractAndSave(tender, parsed.getText());

            // Update tender status
            tender.setStatus("CRITERIA_EXTRACTED");
            tenderRepository.save(tender);

            auditService.recordCriterionExtracted(tender, criteria.size(), uploadedBy);

            log.info("Extraction complete: {} criteria found for tender {}", criteria.size(), tenderRef);

            return ResponseEntity.ok(Map.of(
                    "tenderId", tender.getId(),
                    "tenderRef", tender.getTenderRef(),
                    "status", tender.getStatus(),
                    "criteriaExtracted", criteria.size(),
                    "ocrUsed", parsed.isOcrUsed(),
                    "message", "Criteria extracted. Please review and confirm before evaluation."
            ));

        } catch (IOException e) {
            log.error("Failed to upload/parse tender: {}", tenderRef, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Upload failed: " + e.getMessage()));
        }
    }

    // ── Get all tenders ───────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<List<Tender>> getAllTenders() {
        return ResponseEntity.ok(tenderRepository.findAllByOrderByCreatedAtDesc());
    }

    // ── Get tender by ID ──────────────────────────────────────────────────────

    @GetMapping("/{id}")
    public ResponseEntity<?> getTender(@PathVariable Long id) {
        return tenderRepository.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Get criteria for officer review ──────────────────────────────────────

    /**
     * Step 2 — Officer reviews the LLM-extracted criteria before confirming.
     * Returns all criteria for the tender, with confirmation status.
     */
    @GetMapping("/{id}/criteria")
    public ResponseEntity<?> getCriteria(@PathVariable Long id) {
        if (!tenderRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        List<Criterion> criteria = criterionRepository.findByTenderIdOrderByCriterionRef(id);

        long total     = criteria.size();
        long confirmed = criteria.stream().filter(Criterion::isConfirmedByOfficer).count();

        return ResponseEntity.ok(Map.of(
                "tenderId", id,
                "total", total,
                "confirmed", confirmed,
                "allConfirmed", total > 0 && total == confirmed,
                "criteria", criteria
        ));
    }

    // ── Officer confirms extracted criteria ───────────────────────────────────

    /**
     * Step 3 — Non-negotiable gate. Officer reviews and confirms the criteria.
     * Optional: officer can edit descriptions/thresholds before confirming.
     * Only after this call can evaluation begin.
     */
    @PostMapping("/{id}/criteria/confirm")
    public ResponseEntity<?> confirmCriteria(
            @PathVariable Long id,
            @Valid @RequestBody CriterionConfirmRequest request) {

        Tender tender = tenderRepository.findById(id)
                .orElse(null);
        if (tender == null) return ResponseEntity.notFound().build();

        if (!"CRITERIA_EXTRACTED".equals(tender.getStatus())
                && !"CRITERIA_CONFIRMED".equals(tender.getStatus())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error",
                            "Tender must be in CRITERIA_EXTRACTED state. Current: " + tender.getStatus()));
        }

        List<Criterion> criteria = criterionRepository.findByTenderIdOrderByCriterionRef(id);

        // Apply any officer edits
        if (request.getEdits() != null && !request.getEdits().isEmpty()) {
            for (CriterionConfirmRequest.CriterionEditDto edit : request.getEdits()) {
                criteria.stream()
                        .filter(c -> c.getId().equals(edit.getCriterionId()))
                        .findFirst()
                        .ifPresent(c -> {
                            if (edit.getDescription() != null)       c.setDescription(edit.getDescription());
                            if (edit.getThresholdValue() != null)    c.setThresholdValue(edit.getThresholdValue());
                            if (edit.getThresholdOperator() != null) c.setThresholdOperator(edit.getThresholdOperator());
                            c.setMandatory(edit.isMandatory());
                        });
            }
        }

        // Mark all criteria as confirmed
        criteria.forEach(c -> c.setConfirmedByOfficer(true));
        criterionRepository.saveAll(criteria);

        // Update tender status
        tender.setStatus("CRITERIA_CONFIRMED");
        tenderRepository.save(tender);

        auditService.recordCriteriaConfirmed(tender, request.getConfirmedBy());

        log.info("Criteria confirmed by officer {} for tender {}", request.getConfirmedBy(), tender.getTenderRef());

        return ResponseEntity.ok(Map.of(
                "tenderId", id,
                "status", tender.getStatus(),
                "criteriaConfirmed", criteria.size(),
                "message", "Criteria confirmed. You can now upload bidders and trigger evaluation."
        ));
    }

    // ── Upload bidder PDF ─────────────────────────────────────────────────────

    /**
     * Step 4 — Officer uploads one bidder's submission PDF.
     * Can be called multiple times (once per bidder).
     * Bidder is saved with parseStatus=PENDING — parsing happens during evaluation.
     */
    @PostMapping(value = "/{id}/bidders/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadBidder(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam("bidderRef") String bidderRef,
            @RequestParam("companyName") String companyName,
            @RequestParam(value = "ocrRequired", defaultValue = "false") boolean ocrRequired) {

        Tender tender = tenderRepository.findById(id).orElse(null);
        if (tender == null) return ResponseEntity.notFound().build();

        if (!"CRITERIA_CONFIRMED".equals(tender.getStatus())
                && !"EVALUATION_RUNNING".equals(tender.getStatus())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Criteria must be confirmed before uploading bidders"));
        }

        if (file.isEmpty() || !isPdf(file.getOriginalFilename())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Only PDF files are accepted"));
        }

        // Check for duplicate bidder ref
        if (bidderRepository.findByTenderIdAndBidderRef(id, bidderRef).isPresent()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Bidder ref already uploaded: " + bidderRef));
        }

        try {
            String uploadDir = System.getProperty("java.io.tmpdir") + "/tenderlens/bidders/" + id;
            new File(uploadDir).mkdirs();
            String fileName = bidderRef.replace("/", "-") + "_" + file.getOriginalFilename();
            String filePath = uploadDir + File.separator + fileName;
            file.transferTo(new File(filePath));

            Bidder bidder = Bidder.builder()
                    .tender(tender)
                    .bidderRef(bidderRef)
                    .companyName(companyName)
                    .fileName(fileName)
                    .filePath(filePath)
                    .ocrRequired(ocrRequired)
                    .parseStatus("PENDING")
                    .build();
            bidder = bidderRepository.save(bidder);

            log.info("Bidder uploaded: {} ({}) for tender {}", bidderRef, companyName, tender.getTenderRef());

            return ResponseEntity.ok(Map.of(
                    "bidderId", bidder.getId(),
                    "bidderRef", bidder.getBidderRef(),
                    "companyName", bidder.getCompanyName(),
                    "parseStatus", bidder.getParseStatus(),
                    "message", "Bidder uploaded. Trigger /evaluate to start evaluation."
            ));

        } catch (IOException e) {
            log.error("Failed to upload bidder: {}", bidderRef, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Upload failed: " + e.getMessage()));
        }
    }

    // ── List bidders ──────────────────────────────────────────────────────────

    @GetMapping("/{id}/bidders")
    public ResponseEntity<?> getBidders(@PathVariable Long id) {
        if (!tenderRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        List<Bidder> bidders = bidderRepository.findByTenderIdOrderByCompanyName(id);
        return ResponseEntity.ok(Map.of(
                "tenderId", id,
                "totalBidders", bidders.size(),
                "bidders", bidders
        ));
    }

    // ── Trigger evaluation ────────────────────────────────────────────────────

    /**
     * Step 5 — Triggers async evaluation via Kafka.
     * One EvaluationJobMessage per bidder is sent to the evaluation-jobs topic.
     * The EvaluationConsumer picks these up, runs the full pipeline, and updates DB.
     *
     * Only PENDING bidders are queued — already PARSED ones are skipped.
     */
    @PostMapping("/{id}/evaluate")
    public ResponseEntity<?> triggerEvaluation(
            @PathVariable Long id,
            @RequestParam(value = "requestedBy", defaultValue = "SYSTEM") String requestedBy) {

        Tender tender = tenderRepository.findById(id).orElse(null);
        if (tender == null) return ResponseEntity.notFound().build();

        if (!"CRITERIA_CONFIRMED".equals(tender.getStatus())
                && !"EVALUATION_RUNNING".equals(tender.getStatus())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error",
                            "Criteria must be confirmed before evaluation. Current status: " + tender.getStatus()));
        }

        // Only confirmed criteria are used
        long confirmedCriteria = criterionRepository.countByTenderIdAndConfirmedByOfficerTrue(id);
        if (confirmedCriteria == 0) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "No confirmed criteria found. Please confirm criteria first."));
        }

        // Queue PENDING bidders
        List<Bidder> pendingBidders = bidderRepository.findByTenderIdAndParseStatus(id, "PENDING");
        if (pendingBidders.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "No pending bidders found. Upload bidder documents first."));
        }

        // Send one Kafka message per bidder
        int queued = 0;
        for (Bidder bidder : pendingBidders) {
            EvaluationJobMessage message = EvaluationJobMessage.builder()
                    .tenderId(tender.getId())
                    .bidderId(bidder.getId())
                    .tenderRef(tender.getTenderRef())
                    .bidderRef(bidder.getBidderRef())
                    .companyName(bidder.getCompanyName())
                    .filePath(bidder.getFilePath())
                    .ocrRequired(bidder.isOcrRequired())
                    .queuedAt(OffsetDateTime.now())
                    .requestedBy(requestedBy)
                    .build();

            kafkaTemplate.send("evaluation-jobs", bidder.getBidderRef(), message);
            queued++;
            log.info("Queued evaluation job for bidder {} (tender {})", bidder.getBidderRef(), tender.getTenderRef());
        }

        // Update tender status
        tender.setStatus("EVALUATION_RUNNING");
        tenderRepository.save(tender);

        return ResponseEntity.ok(Map.of(
                "tenderId", id,
                "tenderRef", tender.getTenderRef(),
                "status", tender.getStatus(),
                "biddersQueued", queued,
                "confirmedCriteria", confirmedCriteria,
                "message", "Evaluation started. Check /tenders/" + id + "/evaluation/summary for progress."
        ));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isPdf(String fileName) {
        return fileName != null && fileName.toLowerCase().endsWith(".pdf");
    }
}
