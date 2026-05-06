package com.HackerEarth.Hackathon.TenderLens.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Full evaluation summary for one bidder — used in report generation
 * and the React dashboard matrix view.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BidderEvaluationSummary {

    private Long bidderId;
    private String bidderRef;
    private String companyName;

    private int totalCriteria;
    private int eligibleCount;
    private int notEligibleCount;
    private int needsReviewCount;

    // Overall verdict — NOT_ELIGIBLE if any mandatory criterion fails
    private String overallVerdict;      // ELIGIBLE | NOT_ELIGIBLE | NEEDS_REVIEW

    private List<EvaluationResult> criteriaResults;
}
