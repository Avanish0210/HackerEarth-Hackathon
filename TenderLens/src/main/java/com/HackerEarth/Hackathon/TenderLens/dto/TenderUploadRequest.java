package com.HackerEarth.Hackathon.TenderLens.dto;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request when officer uploads a new tender document.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenderUploadRequest {
    @NotBlank
    private String tenderRef;       // e.g. TENDER-2025-001

    @NotBlank
    private String title;

    @NotBlank
    private String uploadedBy;      // officer ID

    private boolean ocrRequired;    // true for scanned PDFs
}
