package com.HackerEarth.Hackathon.ubidbridge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ConflictResolutionRequest {
    @NotNull
    private Long conflictId;

    @NotBlank
    private String resolvedValue;

    @NotBlank
    private String resolvedBy;          // officer ID

    private String resolutionNote;
}
