package com.HackerEarth.Hackathon.TenderLens.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * Officer confirms extracted criteria before evaluation begins.
 * This is the non-negotiable gate — no evaluation starts without this.
 */
@Data
public class CriterionConfirmRequest {

    @NotNull
    private Long tenderId;

    @NotBlank
    private String confirmedBy;         // officer ID

    // Officer can optionally edit criteria before confirming
    private List<CriterionEditDto> edits;

    @Data
    public static class CriterionEditDto {
        private Long criterionId;
        private String description;
        private String thresholdValue;
        private String thresholdOperator;
        private boolean mandatory;
    }
}
