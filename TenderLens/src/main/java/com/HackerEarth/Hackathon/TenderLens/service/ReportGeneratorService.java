package com.HackerEarth.Hackathon.TenderLens.service;

import com.HackerEarth.Hackathon.TenderLens.config.TenderLensProperties;
import com.HackerEarth.Hackathon.TenderLens.dto.BidderEvaluationSummary;
import com.HackerEarth.Hackathon.TenderLens.dto.EvaluationResult;
import com.HackerEarth.Hackathon.TenderLens.entity.*;
import com.HackerEarth.Hackathon.TenderLens.repository.*;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates a structured PDF evaluation report for a tender.
 *
 * Report structure:
 *   1. Cover page — tender details, evaluation date, summary counts
 *   2. Executive summary — eligible / not eligible / pending review matrix
 *   3. Per-bidder detailed section — one table per bidder with all criteria results
 *   4. Review queue summary — outstanding items needing officer action
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportGeneratorService {

    private final TenderRepository tenderRepository;
    private final BidderRepository bidderRepository;
    private final EvaluationRepository evaluationRepository;
    private final CriterionRepository criterionRepository;
    private final ReportRepository reportRepository;
    private final TenderAuditService auditService;
    private final TenderLensProperties props;

    // Brand colors
    private static final DeviceRgb HEADER_BG    = new DeviceRgb(0x1E, 0x3A, 0x5F); // Navy blue
    private static final DeviceRgb ELIGIBLE_BG  = new DeviceRgb(0xD4, 0xED, 0xDA); // Light green
    private static final DeviceRgb INELIGIBLE_BG= new DeviceRgb(0xF8, 0xD7, 0xDA); // Light red
    private static final DeviceRgb REVIEW_BG    = new DeviceRgb(0xFF, 0xF3, 0xCD); // Light yellow
    private static final DeviceRgb ROW_ALT_BG   = new DeviceRgb(0xF8, 0xF9, 0xFA); // Light grey

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm");

    @Transactional
    public Report generate(Long tenderId) throws IOException {
        Tender tender = tenderRepository.findById(tenderId)
                .orElseThrow(() -> new IllegalArgumentException("Tender not found: " + tenderId));

        List<Criterion> criteria = criterionRepository.findByTenderIdAndConfirmedByOfficerTrue(tenderId);
        List<Bidder> bidders = bidderRepository.findByTenderIdAndParseStatus(tenderId, "PARSED");
        List<BidderEvaluationSummary> summaries = buildSummaries(tender, bidders, criteria);

        // Ensure output directory exists
        String outputDir = props.getReport().getOutputDir();
        new File(outputDir).mkdirs();

        String fileName = "TenderLens_Report_" + tender.getTenderRef().replace("/", "-")
                + "_" + System.currentTimeMillis() + ".pdf";
        String filePath = outputDir + File.separator + fileName;

        writePdf(filePath, tender, criteria, summaries);

        // Save report metadata
        Report report = Report.builder()
                .tender(tender)
                .filePath(filePath)
                .fileName(fileName)
                .totalBidders(bidders.size())
                .eligibleCount((int) summaries.stream().filter(s -> "ELIGIBLE".equals(s.getOverallVerdict())).count())
                .notEligibleCount((int) summaries.stream().filter(s -> "NOT_ELIGIBLE".equals(s.getOverallVerdict())).count())
                .needsReviewCount((int) summaries.stream().filter(s -> "NEEDS_REVIEW".equals(s.getOverallVerdict())).count())
                .generatedAt(OffsetDateTime.now())
                .build();
        reportRepository.save(report);

        auditService.recordReportGenerated(tender, filePath);
        log.info("Report generated: {}", filePath);

        return report;
    }

    // ── PDF writing ───────────────────────────────────────────────────────────

    private void writePdf(String filePath, Tender tender,
                          List<Criterion> criteria,
                          List<BidderEvaluationSummary> summaries) throws IOException {

        try (PdfDocument pdf = new PdfDocument(new PdfWriter(filePath));
             Document doc = new Document(pdf)) {

            doc.setMargins(36, 36, 36, 36);

            addCoverPage(doc, tender, summaries);
            doc.add(new AreaBreak());

            addExecutiveSummary(doc, tender, summaries);
            doc.add(new AreaBreak());

            for (BidderEvaluationSummary summary : summaries) {
                addBidderSection(doc, summary, criteria);
            }
        }
    }

    // ── Cover page ────────────────────────────────────────────────────────────

    private void addCoverPage(Document doc, Tender tender, List<BidderEvaluationSummary> summaries) {
        // Title block
        Table titleBlock = new Table(UnitValue.createPercentArray(new float[]{1}))
                .useAllAvailableWidth();

        Cell titleCell = new Cell()
                .setBackgroundColor(HEADER_BG)
                .setPadding(24)
                .add(new Paragraph("TenderLens")
                        .setFontColor(ColorConstants.WHITE)
                        .setFontSize(28)
                        .setBold())
                .add(new Paragraph("AI-Powered Procurement Evaluation Report")
                        .setFontColor(ColorConstants.WHITE)
                        .setFontSize(14));
        titleBlock.addCell(titleCell);
        doc.add(titleBlock);

        doc.add(new Paragraph("\n"));

        // Tender details
        Table details = new Table(UnitValue.createPercentArray(new float[]{1, 2}))
                .useAllAvailableWidth()
                .setMarginTop(20);

        addDetailRow(details, "Tender Reference", tender.getTenderRef());
        addDetailRow(details, "Title",            tender.getTitle());
        addDetailRow(details, "Report Generated", OffsetDateTime.now().format(FMT));
        addDetailRow(details, "Total Bidders",    String.valueOf(summaries.size()));
        addDetailRow(details, "Eligible",
                String.valueOf(summaries.stream().filter(s -> "ELIGIBLE".equals(s.getOverallVerdict())).count()));
        addDetailRow(details, "Not Eligible",
                String.valueOf(summaries.stream().filter(s -> "NOT_ELIGIBLE".equals(s.getOverallVerdict())).count()));
        addDetailRow(details, "Needs Review",
                String.valueOf(summaries.stream().filter(s -> "NEEDS_REVIEW".equals(s.getOverallVerdict())).count()));
        doc.add(details);

        // Disclaimer
        doc.add(new Paragraph(
                "\nThis report is AI-generated. All verdicts are explainable and auditable. " +
                        "Low-confidence evaluations have been routed for officer review.")
                .setFontSize(9)
                .setFontColor(ColorConstants.GRAY)
                .setMarginTop(30));
    }

    // ── Executive summary matrix ──────────────────────────────────────────────

    private void addExecutiveSummary(Document doc, Tender tender, List<BidderEvaluationSummary> summaries) {
        doc.add(new Paragraph("Executive Summary — Bidder Matrix")
                .setFontSize(16).setBold().setMarginBottom(12));

        // Header row
        Table matrix = new Table(UnitValue.createPercentArray(new float[]{3, 2, 2, 2, 2}))
                .useAllAvailableWidth();

        addHeaderCell(matrix, "Company");
        addHeaderCell(matrix, "Bidder Ref");
        addHeaderCell(matrix, "Eligible");
        addHeaderCell(matrix, "Not Eligible");
        addHeaderCell(matrix, "Overall");

        boolean alternate = false;
        for (BidderEvaluationSummary s : summaries) {
            DeviceRgb rowBg = alternate ? ROW_ALT_BG : null;
            alternate = !alternate;

            addDataCell(matrix, s.getCompanyName(), rowBg);
            addDataCell(matrix, s.getBidderRef(), rowBg);
            addDataCell(matrix, String.valueOf(s.getEligibleCount()), rowBg);
            addDataCell(matrix, String.valueOf(s.getNotEligibleCount()), rowBg);

            DeviceRgb verdictBg = verdictColor(s.getOverallVerdict());
            Cell verdictCell = new Cell()
                    .setBackgroundColor(verdictBg != null ? verdictBg : (rowBg != null ? rowBg : ColorConstants.WHITE))
                    .setPadding(6)
                    .add(new Paragraph(s.getOverallVerdict()).setFontSize(9).setBold());
            matrix.addCell(verdictCell);
        }

        doc.add(matrix);
    }

    // ── Per-bidder detailed section ───────────────────────────────────────────

    private void addBidderSection(Document doc, BidderEvaluationSummary summary, List<Criterion> criteria) {
        // Bidder header
        DeviceRgb headerColor = verdictColor(summary.getOverallVerdict());
        Table bidderHeader = new Table(UnitValue.createPercentArray(new float[]{1})).useAllAvailableWidth();
        Cell hCell = new Cell()
                .setBackgroundColor(headerColor != null ? headerColor : ROW_ALT_BG)
                .setPadding(10)
                .add(new Paragraph(summary.getCompanyName() + "  [" + summary.getBidderRef() + "]")
                        .setFontSize(13).setBold())
                .add(new Paragraph("Overall: " + summary.getOverallVerdict()
                        + "   |   Eligible: " + summary.getEligibleCount()
                        + "   Not Eligible: " + summary.getNotEligibleCount()
                        + "   Needs Review: " + summary.getNeedsReviewCount())
                        .setFontSize(10));
        bidderHeader.addCell(hCell);
        doc.add(bidderHeader);

        // Criteria results table
        Table resultsTable = new Table(UnitValue.createPercentArray(new float[]{1, 3, 2, 2, 1, 1}))
                .useAllAvailableWidth()
                .setMarginTop(6);

        addHeaderCell(resultsTable, "Ref");
        addHeaderCell(resultsTable, "Criterion");
        addHeaderCell(resultsTable, "Extracted Value");
        addHeaderCell(resultsTable, "Required");
        addHeaderCell(resultsTable, "Confidence");
        addHeaderCell(resultsTable, "Verdict");

        boolean alt = false;
        for (EvaluationResult result : summary.getCriteriaResults()) {
            DeviceRgb rowBg = alt ? ROW_ALT_BG : null;
            alt = !alt;

            addDataCell(resultsTable, result.getCriterionRef(), rowBg);
            addDataCell(resultsTable, result.getCriterionDescription(), rowBg);
            addDataCell(resultsTable,
                    result.getExtractedValue() != null ? result.getExtractedValue() : "—", rowBg);
            addDataCell(resultsTable,
                    (result.getThresholdValue() != null ? result.getThresholdValue() : "—"), rowBg);
            addDataCell(resultsTable,
                    result.getConfidenceScore() != null
                            ? result.getConfidenceScore().multiply(BigDecimal.valueOf(100))
                            .setScale(0, java.math.RoundingMode.HALF_UP) + "%"
                            : "—", rowBg);

            DeviceRgb vBg = verdictColor(result.getVerdict());
            Cell vCell = new Cell()
                    .setBackgroundColor(vBg != null ? vBg : (rowBg != null ? rowBg : ColorConstants.WHITE))
                    .setPadding(5)
                    .add(new Paragraph(result.getVerdict() != null ? result.getVerdict() : "—")
                            .setFontSize(8).setBold());
            resultsTable.addCell(vCell);
        }

        doc.add(resultsTable);
        doc.add(new Paragraph("\n"));
    }

    // ── Build summaries from DB ───────────────────────────────────────────────

    private List<BidderEvaluationSummary> buildSummaries(
            Tender tender,
            List<Bidder> bidders,
            List<Criterion> criteria) {

        List<BidderEvaluationSummary> summaries = new ArrayList<>();

        for (Bidder bidder : bidders) {
            List<Evaluation> evals = evaluationRepository
                    .findByTenderIdAndBidderIdOrderByCriterionId(tender.getId(), bidder.getId());

            List<EvaluationResult> results = evals.stream().map(this::toResult).toList();

            int eligible    = (int) results.stream().filter(r -> "ELIGIBLE".equals(r.getVerdict())).count();
            int notEligible = (int) results.stream().filter(r -> "NOT_ELIGIBLE".equals(r.getVerdict())).count();
            int needsReview = (int) results.stream().filter(r -> "NEEDS_REVIEW".equals(r.getVerdict())).count();

            // Overall verdict logic:
            // NOT_ELIGIBLE if any MANDATORY criterion fails
            // NEEDS_REVIEW if any pending reviews exist
            // ELIGIBLE only if all mandatory criteria pass and no pending reviews
            String overall;
            boolean mandatoryFailed = evals.stream()
                    .anyMatch(e -> "NOT_ELIGIBLE".equals(e.getVerdict())
                            && e.getCriterion().isMandatory());

            if (mandatoryFailed) {
                overall = "NOT_ELIGIBLE";
            } else if (needsReview > 0) {
                overall = "NEEDS_REVIEW";
            } else {
                overall = "ELIGIBLE";
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

        return summaries;
    }

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
                .verdict(e.getVerdict())
                .confidenceScore(e.getConfidenceScore())
                .ocrQualityFlag(e.isOcrQualityFlag())
                .reviewReason(e.getReviewReason())
                .build();
    }

    // ── PDF helpers ───────────────────────────────────────────────────────────

    private void addHeaderCell(Table table, String text) {
        table.addCell(new Cell()
                .setBackgroundColor(HEADER_BG)
                .setPadding(7)
                .add(new Paragraph(text)
                        .setFontColor(ColorConstants.WHITE)
                        .setFontSize(9)
                        .setBold()
                        .setTextAlignment(TextAlignment.CENTER)));
    }

    private void addDataCell(Table table, String text, DeviceRgb bg) {
        Cell cell = new Cell()
                .setPadding(5)
                .add(new Paragraph(text != null ? text : "—").setFontSize(8));
        if (bg != null) cell.setBackgroundColor(bg);
        table.addCell(cell);
    }

    private void addDetailRow(Table table, String label, String value) {
        table.addCell(new Cell().setPadding(6)
                .add(new Paragraph(label).setBold().setFontSize(10)));
        table.addCell(new Cell().setPadding(6)
                .add(new Paragraph(value != null ? value : "—").setFontSize(10)));
    }

    private DeviceRgb verdictColor(String verdict) {
        if (verdict == null) return null;
        return switch (verdict) {
            case "ELIGIBLE"     -> ELIGIBLE_BG;
            case "NOT_ELIGIBLE" -> INELIGIBLE_BG;
            case "NEEDS_REVIEW" -> REVIEW_BG;
            default             -> null;
        };
    }
}