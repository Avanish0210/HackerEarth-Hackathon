package com.HackerEarth.Hackathon.TenderLens.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Structured JSON response from Ollama LLM.
 * Used for both criterion extraction (from tender) and
 * evidence extraction (from bidder docs).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmExtractionResponse {

    // For criterion extraction
    private List<CriterionDto> criteria;

    // For bidder evidence extraction
    private String extractedValue;
    private String verbatimExcerpt;
    private Integer sourcePage;
    private double confidence;          // 0.0 to 1.0
    private boolean found;              // false = "not found / low confidence"
    private String notFoundReason;      // why the LLM couldn't extract a value
}
