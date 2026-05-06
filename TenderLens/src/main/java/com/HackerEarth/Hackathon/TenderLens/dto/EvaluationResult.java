package com.HackerEarth.Hackathon.TenderLens.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Result of evaluating one bidder against one criterion.
 * Every verdict has a full evidence chain attached.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationResult {

    private Long criterionId;
    private String criterionRef;
    private String criterionDescription;
    private String criterionType;
    private boolean mandatory;
    private String thresholdValue;

    // Evidence
    private String extractedValue;      // What was found in the bidder doc
    private String sourceDocument;
    private Integer sourcePage;
    private String verbatimExcerpt;     // Exact text from the document

    // Verdict
    private String verdict;             // ELIGIBLE | NOT_ELIGIBLE | NEEDS_REVIEW
    private BigDecimal confidenceScore;
    private boolean ocrQualityFlag;
    private String llmCallId;

    // Reason when routed to review queue
    private String reviewReason;        // LOW_CONFIDENCE | OCR_QUALITY | NOT_FOUND
}
