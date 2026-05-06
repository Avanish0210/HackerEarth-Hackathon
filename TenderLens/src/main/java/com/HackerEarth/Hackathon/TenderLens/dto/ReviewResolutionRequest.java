package com.HackerEarth.Hackathon.TenderLens.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * Officer resolves a low-confidence evaluation from the review queue.
 */
@Data
public class ReviewResolutionRequest {

    @NotNull
    private Long reviewId;

    @NotBlank
    @Pattern(regexp = "ELIGIBLE|NOT_ELIGIBLE")
    private String overrideVerdict;

    @NotBlank
    private String reviewer;            // officer ID

    private String reviewNote;          // officer's reasoning
}
