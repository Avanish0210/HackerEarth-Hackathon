package com.HackerEarth.Hackathon.TenderLens.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * LLM-extracted criterion — what the AI pulls from the tender document.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CriterionDto {

    private String criterionRef;        // C-001, C-002 ...
    private String description;
    private String criterionType;       // FINANCIAL | TECHNICAL | COMPLIANCE | CERTIFICATION
    private boolean mandatory;
    private String thresholdValue;      // e.g. "5 crore", "ISO 9001"
    private String thresholdOperator;   // GTE | LTE | EQ | CONTAINS
    private Integer sourcePage;
    private double confidence;          // LLM confidence in this extraction
}
